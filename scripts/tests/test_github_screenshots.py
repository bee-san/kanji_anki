#!/usr/bin/env python3

from __future__ import annotations

import hashlib
import json
import os
import tempfile
import unittest
from pathlib import Path
from subprocess import CompletedProcess
from typing import Any, cast
from unittest import mock

from scripts.ralph_loop import github_screenshots


class FakeRunner:
    def __init__(self, responses: dict[tuple[str, ...], CompletedProcess[str]] | None = None) -> None:
        self.responses = responses or {}
        self.calls: list[list[str]] = []

    def __call__(self, args: list[str], cwd: Path | None = None) -> CompletedProcess[str]:
        self.calls.append(args)
        key = tuple(args)
        if key in self.responses:
            return self.responses[key]
        return CompletedProcess(args, 0, "", "")


def ok(args: tuple[str, ...], stdout: object = "") -> CompletedProcess[str]:
    text = stdout if isinstance(stdout, str) else json.dumps(stdout)
    return CompletedProcess(list(args), 0, text, "")


def fail(args: tuple[str, ...], stderr: str) -> CompletedProcess[str]:
    return CompletedProcess(list(args), 1, "", stderr)


def run_remote_screenshots_for_test(**kwargs: Any) -> dict[str, object]:
    with mock.patch.dict(os.environ, {"GITHUB_ACTIONS": "false"}):
        return github_screenshots.run_remote_screenshots(**kwargs)


def _capture_entry(path: Path, route: str | None = None) -> dict[str, str]:
    return {
        "route": route or path.stem,
        "path": str(path),
        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
    }


class GithubScreenshotsTest(unittest.TestCase):
    def test_android_screenshots_workflow_sanitizes_dispatch_inputs(self) -> None:
        workflow = Path(".github/workflows/android-screenshots.yml").read_text(encoding="utf-8")

        self.assertIn("GRADLE_TASK: ${{ inputs.gradle_task }}", workflow)
        self.assertIn('case "$GRADLE_TASK" in', workflow)
        self.assertIn('./gradlew "$GRADLE_TASK"', workflow)
        self.assertNotIn('./gradlew "${{ inputs.gradle_task }}"', workflow)
        self.assertIn("SCREENSHOT_ROUTE: ${{ inputs.screenshot_route }}", workflow)
        self.assertIn("ANDROID_SCREENSHOT_API_LEVEL: '35'", workflow)
        self.assertIn("ANDROID_SCREENSHOT_PROFILE: pixel_6", workflow)
        self.assertIn('bash ci/scripts/capture_android_screenshots.sh "$SCREENSHOT_ROUTE"', workflow)
        self.assertNotIn("capture_android_screenshots.sh '${{ inputs.screenshot_route }}'", workflow)

    def test_capture_script_uses_portable_mktemp_for_uiautomator_dump(self) -> None:
        script = Path("ci/scripts/capture_android_screenshots.sh").read_text(encoding="utf-8")

        self.assertIn('mktemp "${TMPDIR:-/tmp}/kani-ui.XXXXXX"', script)
        self.assertNotIn('mktemp -t kani-ui', script)
        self.assertIn('adb shell am start -W -n "${package_name}/.MainActivity" --es "${screen_route_extra}" "${launch_target}" >/dev/null', script)
        self.assertNotIn('-a android.intent.action.MAIN', script)
        self.assertNotIn('-c android.intent.category.LAUNCHER', script)
        self.assertIn('local status=0', script)
        self.assertNotIn('wait_for_route "${capture_name}" "${expected_terms[@]}"\n  sleep 1\n  capture_png "${capture_name}" >/dev/null', script)
        self.assertIn('if len(captured_routes) != len(captured_files):', script)
        self.assertIn('"captures": captures,', script)
        self.assertIn('hashlib.sha256', script)

    def test_finds_run_for_current_sha_and_downloads_valid_artifact(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp).resolve()
            out = root / ".ralph-loop" / "runs" / "123" / "remote-screenshots"
            run_list = [
                {"databaseId": 122, "headSha": "older", "status": "completed", "conclusion": "success"},
                {"databaseId": 123, "headSha": "abc123", "status": "completed", "conclusion": "success"},
            ]
            runner = FakeRunner(
                {
                    ("git", "branch", "--show-current"): ok(("git", "branch", "--show-current"), "feature/visual\n"),
                    ("git", "rev-parse", "HEAD"): ok(("git", "rev-parse", "HEAD"), "abc123\n"),
                    ("gh", "auth", "status"): ok(("gh", "auth", "status")),
                    ("gh", "repo", "view", "--json", "nameWithOwner", "--jq", ".nameWithOwner"): ok(("gh", "repo", "view", "--json", "nameWithOwner", "--jq", ".nameWithOwner"), "bee-san/kanji_anki\n"),
                    ("gh", "workflow", "run", "android-screenshots.yml", "--ref", "feature/visual", "-f", "screenshot_route=home"): ok(("gh", "workflow", "run", "android-screenshots.yml", "--ref", "feature/visual", "-f", "screenshot_route=home")),
                    ("gh", "run", "list", "--workflow", "android-screenshots.yml", "--branch", "feature/visual", "--json", "databaseId,headSha,status,conclusion", "--limit", "20"): ok(("gh", "run", "list", "--workflow", "android-screenshots.yml", "--branch", "feature/visual", "--json", "databaseId,headSha,status,conclusion", "--limit", "20"), run_list),
                    ("gh", "run", "watch", "123", "--exit-status"): ok(("gh", "run", "watch", "123", "--exit-status")),
                }
            )

            result = run_remote_screenshots_for_test(
                repo_root=root,
                workflow="android-screenshots.yml",
                artifact="android-screenshots",
                screenshot_route="home",
                out_dir=out,
                runner=runner,
            )

            self.assertEqual("missing_artifact", result["status"])
            self.assertIn("manifest.json", str(result["message"]))
            self.assertIn(["gh", "run", "download", "123", "--name", "android-screenshots", "--dir", str(out)], runner.calls)

            out.mkdir(parents=True, exist_ok=True)
            (out / "home.png").write_bytes(b"\x89PNG\r\n\x1a\n")
            (out / "manifest.json").write_text(
                json.dumps(
                    {
                        "requested_route": "home",
                        "routes": ["home"],
                        "files": [str(out / "home.png")],
                        "captures": [_capture_entry(out / "home.png", "home")],
                    }
                ),
                encoding="utf-8",
            )

            result = run_remote_screenshots_for_test(
                repo_root=root,
                workflow="android-screenshots.yml",
                artifact="android-screenshots",
                screenshot_route="home",
                out_dir=out,
                runner=runner,
            )

            self.assertEqual("passed", result["status"])
            self.assertEqual(123, result["run_id"])
            self.assertEqual(str(out / "manifest.json"), result["manifest"])
            self.assertEqual([str(out / "home.png")], result["pngs"])

            (out / "stats.png").write_bytes(b"\x89PNG\r\n\x1a\n")
            (out / "manifest.json").write_text(
                json.dumps(
                    {
                        "requested_route": "stats",
                        "routes": ["stats"],
                        "files": [str(out / "stats.png")],
                        "captures": [_capture_entry(out / "stats.png", "stats")],
                    }
                ),
                encoding="utf-8",
            )
            mismatch = github_screenshots.validate_artifact(out, expected_route="home")
            self.assertEqual("missing_artifact", mismatch["status"])
            self.assertIn("home", mismatch["message"])

    def test_validate_artifact_accepts_all_route_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            out = Path(temp)
            routes = ["home", "study", "stats", "settings", "games", "narrow", "wide"]
            files = []
            for route in routes:
                path = out / f"{route}.png"
                path.write_bytes(b"\x89PNG\r\n\x1a\n")
                files.append(str(path))

            (out / "manifest.json").write_text(
                json.dumps(
                    {
                        "requested_route": "all",
                        "routes": routes,
                        "files": files,
                        "captures": [_capture_entry(out / f"{route}.png", route) for route in routes],
                    }
                ),
                encoding="utf-8",
            )
            result = github_screenshots.validate_artifact(out, expected_route="all")
            self.assertEqual("passed", result["status"])
            self.assertEqual(routes, result["routes"])

    def test_validate_artifact_verifies_capture_hashes(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            out = Path(temp)
            png = out / "home.png"
            png.write_bytes(b"\x89PNG\r\n\x1a\n")
            sha256 = hashlib.sha256(png.read_bytes()).hexdigest()
            (out / "manifest.json").write_text(
                json.dumps(
                    {
                        "captured_at_utc": "2026-06-13T00:00:00Z",
                        "command_argv": ["ci/scripts/capture_android_screenshots.sh", "home"],
                        "requested_route": "home",
                        "routes": ["home"],
                        "files": [str(png)],
                        "captures": [
                            {
                                "route": "home",
                                "launch_target": "home",
                                "orientation": "portrait",
                                "file": str(png),
                                "sha256": sha256,
                            }
                        ],
                    },
                    sort_keys=True,
                ),
                encoding="utf-8",
            )

            result = github_screenshots.validate_artifact(out, expected_route="home")
            self.assertEqual("passed", result["status"])
            captures = cast(list[dict[str, object]], result["captures"])
            self.assertEqual(1, len(captures))
            self.assertEqual(sha256, str(captures[0]["sha256"]))

            (out / "manifest.json").write_text(
                json.dumps(
                    {
                        "captured_at_utc": "2026-06-13T00:00:00Z",
                        "command_argv": ["ci/scripts/capture_android_screenshots.sh", "home"],
                        "requested_route": "home",
                        "routes": ["home"],
                        "files": [str(png)],
                        "captures": [
                            {
                                "route": "home",
                                "launch_target": "home",
                                "orientation": "portrait",
                                "file": str(png),
                                "sha256": "0" * 64,
                            }
                        ],
                    },
                    sort_keys=True,
                ),
                encoding="utf-8",
            )

            mismatch = github_screenshots.validate_artifact(out, expected_route="home")
            self.assertEqual("missing_artifact", mismatch["status"])
            self.assertIn("SHA-256", cast(str, mismatch["message"]))

    def test_validate_artifact_rejects_non_exact_all_route_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            out = Path(temp)
            for routes in [
                ["study", "home", "stats", "settings", "games", "narrow", "wide"],
                ["home", "study", "stats", "settings", "games", "narrow", "wide", "extra"],
            ]:
                files = []
                for route in routes:
                    path = out / f"{route}.png"
                    path.write_bytes(b"\x89PNG\r\n\x1a\n")
                    files.append(str(path))

                (out / "manifest.json").write_text(
                    json.dumps(
                        {
                            "requested_route": "all",
                            "routes": routes,
                            "files": files,
                            "captures": [_capture_entry(out / f"{route}.png", route) for route in routes],
                        },
                        sort_keys=True,
                    ),
                    encoding="utf-8",
                )

                result = github_screenshots.validate_artifact(out, expected_route="all")
                self.assertEqual("missing_artifact", result["status"])
                self.assertIn("exactly", str(result["message"]))

                for route in routes:
                    (out / f"{route}.png").unlink()
                (out / "manifest.json").unlink()

    def test_validate_artifact_rejects_missing_captures_and_blank_sha256(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            out = Path(temp)
            png = out / "home.png"
            png.write_bytes(b"\x89PNG\r\n\x1a\n")
            manifest_path = out / "manifest.json"

            manifest_path.write_text(
                json.dumps(
                    {
                        "requested_route": "home",
                        "routes": ["home"],
                        "files": [str(png)],
                    }
                ),
                encoding="utf-8",
            )
            missing_captures = github_screenshots.validate_artifact(out, expected_route="home")
            self.assertEqual("missing_artifact", missing_captures["status"])
            self.assertIn("captures", str(missing_captures["message"]))

            manifest_path.write_text(
                json.dumps(
                    {
                        "requested_route": "home",
                        "routes": ["home"],
                        "files": [str(png)],
                        "captures": [{"route": "home", "path": str(png), "sha256": ""}],
                    }
                ),
                encoding="utf-8",
            )
            blank_sha256 = github_screenshots.validate_artifact(out, expected_route="home")
            self.assertEqual("missing_artifact", blank_sha256["status"])
            self.assertIn("sha256", str(blank_sha256["message"]))

    def test_requires_non_main_branch_and_explicit_push_flag(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for protected_branch in ("main", "release-1.2", "releases/1.2", "trunk"):
                main_runner = FakeRunner(
                    {
                        ("git", "branch", "--show-current"): ok(("git", "branch", "--show-current"), f"{protected_branch}\n"),
                        ("git", "rev-parse", "--abbrev-ref", "origin/HEAD"): ok(("git", "rev-parse", "--abbrev-ref", "origin/HEAD"), "origin/trunk\n"),
                        ("git", "rev-parse", "HEAD"): ok(("git", "rev-parse", "HEAD"), "abc123\n"),
                    }
                )

                result = run_remote_screenshots_for_test(
                    repo_root=root,
                    workflow="android-screenshots.yml",
                    artifact="android-screenshots",
                    screenshot_route="all",
                    out_dir=root / "out",
                    push_pr_branch=True,
                    runner=main_runner,
                )

                self.assertEqual("failed", result["status"], protected_branch)
                self.assertIn("Refusing to run on protected branch", str(result["message"]))

            branch_runner = FakeRunner(
                {
                    ("git", "branch", "--show-current"): ok(("git", "branch", "--show-current"), "feature/visual\n"),
                    ("git", "rev-parse", "HEAD"): ok(("git", "rev-parse", "HEAD"), "abc123\n"),
                    ("gh", "auth", "status"): fail(("gh", "auth", "status"), "not logged in"),
                }
            )

            result = run_remote_screenshots_for_test(
                repo_root=root,
                workflow="android-screenshots.yml",
                artifact="android-screenshots",
                screenshot_route="all",
                out_dir=root / "out",
                push_pr_branch=False,
                runner=branch_runner,
            )

            self.assertEqual("remote_visual_pending", result["status"])
            self.assertFalse(any(call[:3] == ["git", "push", "-u"] for call in branch_runner.calls))

    def test_pushes_current_branch_only_when_explicitly_requested(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            runner = FakeRunner(
                {
                    ("git", "branch", "--show-current"): ok(("git", "branch", "--show-current"), "feature/visual\n"),
                    ("git", "rev-parse", "HEAD"): ok(("git", "rev-parse", "HEAD"), "abc123\n"),
                    ("gh", "auth", "status"): ok(("gh", "auth", "status")),
                    ("gh", "repo", "view", "--json", "nameWithOwner", "--jq", ".nameWithOwner"): ok(("gh", "repo", "view", "--json", "nameWithOwner", "--jq", ".nameWithOwner"), "bee-san/kanji_anki\n"),
                    ("git", "push", "-u", "origin", "feature/visual"): ok(("git", "push", "-u", "origin", "feature/visual")),
                    ("gh", "workflow", "run", "android-screenshots.yml", "--ref", "feature/visual", "-f", "screenshot_route=all"): ok(("gh", "workflow", "run", "android-screenshots.yml", "--ref", "feature/visual", "-f", "screenshot_route=all")),
                    ("gh", "run", "list", "--workflow", "android-screenshots.yml", "--branch", "feature/visual", "--json", "databaseId,headSha,status,conclusion", "--limit", "20"): ok(("gh", "run", "list", "--workflow", "android-screenshots.yml", "--branch", "feature/visual", "--json", "databaseId,headSha,status,conclusion", "--limit", "20"), [{"databaseId": 10, "headSha": "abc123", "status": "completed", "conclusion": "success"}]),
                    ("gh", "run", "watch", "10", "--exit-status"): ok(("gh", "run", "watch", "10", "--exit-status")),
                }
            )

            run_remote_screenshots_for_test(
                repo_root=root,
                workflow="android-screenshots.yml",
                artifact="android-screenshots",
                screenshot_route="all",
                out_dir=root / "out",
                push_pr_branch=True,
                runner=runner,
            )

            self.assertIn(["git", "push", "-u", "origin", "feature/visual"], runner.calls)


if __name__ == "__main__":
    unittest.main()
