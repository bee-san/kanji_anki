#!/usr/bin/env python3

from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import sys
import time
from pathlib import Path
from subprocess import CompletedProcess
from typing import Callable, Iterable, Optional, Sequence, cast

Runner = Callable[[list[str], Optional[Path]], CompletedProcess[str]]

PROTECTED_BRANCHES = {"main", "master", "develop", "release", "production"}
EXPECTED_REPO = "bee-san/kanji_anki"
EXPECTED_ALL_SCREENSHOT_ROUTES = ["home", "study", "stats", "settings", "games", "narrow", "wide"]
SCREENSHOT_ROUTE_ALIASES = {"launcher-home": "home"}


def run_command(args: list[str], cwd: Path | None = None) -> CompletedProcess[str]:
    return subprocess.run(args, cwd=cwd, text=True, capture_output=True, check=False)


def _status(status: str, message: str, **extra: object) -> dict[str, object]:
    result: dict[str, object] = {"status": status, "message": message}
    result.update(extra)
    return result


def _run(runner: Runner, args: Sequence[str], repo_root: Path) -> CompletedProcess[str]:
    return runner(list(args), repo_root)


def _command_text(command: Sequence[str]) -> str:
    return " ".join(command)


def _canonical_screenshot_route(route: str | None) -> str | None:
    if route is None:
        return None
    normalized = route.strip()
    if not normalized:
        return None
    return SCREENSHOT_ROUTE_ALIASES.get(normalized, normalized)


def _manifest_routes(manifest: dict[str, object]) -> list[str]:
    routes = manifest.get("routes")
    if not isinstance(routes, list):
        return []
    normalized: list[str] = []
    for route in routes:
        if not isinstance(route, str):
            continue
        canonical = _canonical_screenshot_route(route)
        if canonical:
            normalized.append(canonical)
    return normalized


def _manifest_files(manifest: dict[str, object], out_dir: Path) -> list[Path]:
    files = manifest.get("files")
    if not isinstance(files, list):
        return []
    resolved: list[Path] = []
    for file_name in files:
        path = Path(str(file_name))
        if not path.is_absolute():
            path = out_dir / path
        resolved.append(path)
    return resolved


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _manifest_capture_entries(manifest: dict[str, object], out_dir: Path) -> list[dict[str, object]]:
    captures = manifest.get("captures")
    if not isinstance(captures, list):
        return []
    normalized: list[dict[str, object]] = []
    for capture in captures:
        if not isinstance(capture, dict):
            continue
        raw_route = capture.get("route")
        route = _canonical_screenshot_route(raw_route if isinstance(raw_route, str) else None)
        raw_file = capture.get("file") or capture.get("path")
        if not isinstance(raw_file, str) or not raw_file:
            continue
        path = Path(raw_file)
        if not path.is_absolute():
            path = out_dir / path
        raw_scroll_y = capture.get("scroll_y")
        if isinstance(raw_scroll_y, str) and raw_scroll_y.strip():
            try:
                scroll_y: int | str | None = int(raw_scroll_y.strip())
            except ValueError:
                scroll_y = raw_scroll_y.strip()
        elif isinstance(raw_scroll_y, int):
            scroll_y = raw_scroll_y
        elif isinstance(raw_scroll_y, float) and raw_scroll_y.is_integer():
            scroll_y = int(raw_scroll_y)
        else:
            scroll_y = None
        raw_uiautomator_dump = capture.get("uiautomator_dump_path") or capture.get("ui_dump_path")
        ui_dump_path: Path | None = None
        if isinstance(raw_uiautomator_dump, str) and raw_uiautomator_dump:
            ui_dump_path = Path(raw_uiautomator_dump)
            if not ui_dump_path.is_absolute():
                ui_dump_path = out_dir / ui_dump_path
        normalized.append(
            {
                "route": route or "",
                "path": path,
                "sha256": str(capture.get("sha256") or ""),
                "orientation": str(capture.get("orientation") or ""),
                "launch_target": str(capture.get("launch_target") or ""),
                "scroll_position": str(capture.get("scroll_position") or ""),
                "scroll_y": scroll_y,
                "scrollable": bool(capture.get("scrollable")),
                "uiautomator_dump_path": ui_dump_path,
                "uiautomator_dump_sha256": str(capture.get("uiautomator_dump_sha256") or capture.get("ui_dump_sha256") or ""),
            }
        )
    return normalized


def _unique_routes(routes: Iterable[str]) -> list[str]:
    unique: list[str] = []
    seen: set[str] = set()
    for route in routes:
        if route and route not in seen:
            seen.add(route)
            unique.append(route)
    return unique


def _manifest_capture_path(capture: dict[str, object], out_dir: Path) -> Path | None:
    raw_path = capture.get("path")
    if not isinstance(raw_path, str) or not raw_path.strip():
        raw_file = capture.get("file")
        if not isinstance(raw_file, str) or not raw_file.strip():
            return None
        raw_path = raw_file
    path = Path(raw_path)
    if not path.is_absolute():
        path = out_dir / path
    return path


def _current_branch(repo_root: Path, runner: Runner) -> tuple[str | None, str | None]:
    process = _run(runner, ["git", "branch", "--show-current"], repo_root)
    if process.returncode != 0:
        return None, process.stderr.strip() or "git branch --show-current failed"
    branch = process.stdout.strip()
    if not branch:
        return None, "Detached HEAD is not safe for remote screenshot dispatch"
    return branch, None


def _current_sha(repo_root: Path, runner: Runner) -> tuple[str | None, str | None]:
    process = _run(runner, ["git", "rev-parse", "HEAD"], repo_root)
    if process.returncode != 0:
        return None, process.stderr.strip() or "git rev-parse HEAD failed"
    sha = process.stdout.strip()
    if not sha:
        return None, "Unable to determine current commit SHA"
    return sha, None


def _default_branch(repo_root: Path, runner: Runner) -> str | None:
    process = _run(runner, ["git", "rev-parse", "--abbrev-ref", "origin/HEAD"], repo_root)
    if process.returncode != 0:
        return None
    ref = process.stdout.strip()
    if not ref:
        return None
    return ref.removeprefix("origin/")


def _is_protected_branch(branch: str, default_branch: str | None = None) -> bool:
    if branch in PROTECTED_BRANCHES or branch == default_branch:
        return True
    return branch.startswith(("release/", "releases/", "release-", "hotfix/release"))


def _gh_ready(repo_root: Path, runner: Runner, require: bool) -> dict[str, object] | None:
    auth = _run(runner, ["gh", "auth", "status"], repo_root)
    if auth.returncode != 0:
        status = "failed" if require else "remote_visual_pending"
        return _status(status, "gh is not authenticated; run `gh auth login` or provide a valid GitHub token.", stderr=auth.stderr.strip())

    repo = _run(runner, ["gh", "repo", "view", "--json", "nameWithOwner", "--jq", ".nameWithOwner"], repo_root)
    if repo.returncode != 0:
        status = "failed" if require else "remote_visual_pending"
        return _status(status, "Unable to verify GitHub repository with `gh repo view`.", stderr=repo.stderr.strip())
    actual = repo.stdout.strip()
    if actual != EXPECTED_REPO:
        return _status("failed", f"Refusing to dispatch screenshots for {actual!r}; expected {EXPECTED_REPO!r}.")
    return None


def _find_run_for_sha(repo_root: Path, runner: Runner, workflow: str, branch: str, sha: str, attempts: int, sleep_seconds: float) -> tuple[int | None, dict[str, object] | None]:
    command = [
        "gh",
        "run",
        "list",
        "--workflow",
        workflow,
        "--branch",
        branch,
        "--json",
        "databaseId,headSha,status,conclusion",
        "--limit",
        "20",
    ]
    last_error = ""
    for _ in range(max(1, attempts)):
        process = _run(runner, command, repo_root)
        if process.returncode != 0:
            last_error = process.stderr.strip() or f"{_command_text(command)} failed"
        else:
            try:
                runs = json.loads(process.stdout or "[]")
            except json.JSONDecodeError as error:
                last_error = f"Unable to parse `gh run list` JSON: {error}"
            else:
                for run in runs:
                    if str(run.get("headSha")) == sha and run.get("databaseId") is not None:
                        return int(run["databaseId"]), run
                last_error = f"No {workflow} run found yet for branch {branch} at {sha}."
        time.sleep(sleep_seconds)
    return None, _status("remote_visual_pending", last_error)


def validate_artifact(out_dir: Path, expected_route: str | None = None) -> dict[str, object]:
    manifests = sorted(out_dir.rglob("manifest.json"))
    pngs = sorted(out_dir.rglob("*.png"))
    if not manifests:
        return _status("missing_artifact", f"Downloaded artifact under {out_dir} does not contain manifest.json.")
    if not pngs:
        return _status("missing_artifact", f"Downloaded artifact under {out_dir} does not contain any PNG screenshots.", manifest=str(manifests[0]))
    try:
        manifest = json.loads(manifests[0].read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        return _status("missing_artifact", f"Artifact manifest is not valid JSON: {error}", manifest=str(manifests[0]))
    if not isinstance(manifest, dict):
        return _status("missing_artifact", f"Artifact manifest at {manifests[0]} is not a JSON object.", manifest=str(manifests[0]))

    capture_entries = _manifest_capture_entries(manifest, out_dir)
    manifest_routes = _manifest_routes(manifest)
    manifest_files = _manifest_files(manifest, out_dir)
    if capture_entries:
        for index, entry in enumerate(capture_entries):
            route = str(entry["route"]).strip()
            if not route:
                return _status(
                    "missing_artifact",
                    f"Artifact manifest capture entry {index} does not declare a non-empty route.",
                    manifest=str(manifests[0]),
                    capture_path=str(cast(Path, entry["path"])),
                )
        if not manifest_routes:
            manifest_routes = _unique_routes(str(entry["route"]) for entry in capture_entries)
        manifest_files = [cast(Path, entry["path"]) for entry in capture_entries]
    if not manifest_routes:
        return _status("missing_artifact", f"Artifact manifest at {manifests[0]} does not declare any routes.", manifest=str(manifests[0]))

    raw_requested_route = manifest.get("requested_route")
    manifest_requested_route = _canonical_screenshot_route(raw_requested_route if isinstance(raw_requested_route, str) else None)
    if manifest_files:
        missing_files = [path for path in manifest_files if not path.exists()]
        if missing_files:
            return _status(
                "missing_artifact",
                f"Artifact manifest references missing files: {', '.join(str(path) for path in missing_files)}",
                manifest=str(manifests[0]),
            )
        if capture_entries:
            hash_mismatches = []
            for entry in capture_entries:
                expected_sha256 = str(entry["sha256"])
                if not expected_sha256:
                    continue
                file_path = cast(Path, entry["path"])
                actual_sha256 = _sha256_file(file_path)
                if actual_sha256 != expected_sha256:
                    hash_mismatches.append(
                        {
                            "file": str(file_path),
                            "expected_sha256": expected_sha256,
                            "actual_sha256": actual_sha256,
                        }
                    )
            if hash_mismatches:
                return _status(
                    "missing_artifact",
                    f"Artifact manifest SHA-256 hashes do not match for: {', '.join(item['file'] for item in hash_mismatches)}",
                    manifest=str(manifests[0]),
                    hash_mismatches=hash_mismatches,
                )
        pngs = manifest_files

    raw_captures = manifest.get("captures")
    if isinstance(raw_captures, list) and raw_captures:
        if len(capture_entries) != len(raw_captures):
            return _status(
                "missing_artifact",
                f"Artifact manifest contains {len(raw_captures)} captures; expected exactly {len(capture_entries)} valid capture entries.",
                manifest=str(manifests[0]),
                captures=len(raw_captures),
                valid_captures=len(capture_entries),
            )

        validated_pngs: list[Path] = []
        for index, (capture, expected_file) in enumerate(zip(capture_entries, manifest_files)):
            if not isinstance(capture, dict):
                return _status(
                    "missing_artifact",
                    f"Artifact manifest capture entry {index} is not a JSON object.",
                    manifest=str(manifests[0]),
                )
            capture_path = _manifest_capture_path(capture, out_dir)
            if capture_path is None:
                return _status(
                    "missing_artifact",
                    f"Artifact manifest capture entry {index} does not declare a file path.",
                    manifest=str(manifests[0]),
                )
            if capture_path.resolve() != expected_file.resolve():
                return _status(
                    "missing_artifact",
                    f"Artifact manifest capture entry {index} points to {capture_path} but expected {expected_file}.",
                    manifest=str(manifests[0]),
                    capture_path=str(capture_path),
                    file_path=str(expected_file),
                )
            if not capture_path.exists():
                return _status(
                    "missing_artifact",
                    f"Artifact manifest references missing capture file: {capture_path}",
                    manifest=str(manifests[0]),
                )
            raw_sha256 = capture.get("sha256")
            if not isinstance(raw_sha256, str) or not raw_sha256.strip():
                return _status(
                    "missing_artifact",
                    f"Artifact manifest capture entry {index} is missing a non-empty sha256.",
                    manifest=str(manifests[0]),
                    capture_path=str(capture_path),
                )
            try:
                actual_sha256 = hashlib.sha256(capture_path.read_bytes()).hexdigest()
            except OSError as error:
                return _status(
                    "missing_artifact",
                    f"Unable to read capture file {capture_path}: {error}",
                    manifest=str(manifests[0]),
                )
            provided_sha256 = raw_sha256.strip().lower()
            if provided_sha256 != actual_sha256:
                return _status(
                    "missing_artifact",
                    f"Artifact manifest capture entry {index} sha256 mismatch for {capture_path}: expected {actual_sha256}, got {provided_sha256}.",
                    manifest=str(manifests[0]),
                    capture_path=str(capture_path),
                    sha256=provided_sha256,
                )
            raw_ui_dump_path = capture.get("uiautomator_dump_path") or capture.get("ui_dump_path")
            ui_dump_path = None
            if isinstance(raw_ui_dump_path, str) and raw_ui_dump_path.strip():
                ui_dump_path = Path(raw_ui_dump_path)
                if not ui_dump_path.is_absolute():
                    ui_dump_path = out_dir / ui_dump_path
            if ui_dump_path is not None:
                if not ui_dump_path.exists():
                    return _status(
                        "missing_artifact",
                        f"Artifact manifest references missing UIAutomator dump file: {ui_dump_path}",
                        manifest=str(manifests[0]),
                        capture_path=str(ui_dump_path),
                    )
                raw_ui_dump_sha256 = str(capture.get("uiautomator_dump_sha256") or capture.get("ui_dump_sha256") or "").strip().lower()
                if raw_ui_dump_sha256:
                    actual_ui_dump_sha256 = hashlib.sha256(ui_dump_path.read_bytes()).hexdigest()
                    if raw_ui_dump_sha256 != actual_ui_dump_sha256:
                        return _status(
                            "missing_artifact",
                            f"Artifact manifest UIAutomator dump hash mismatch for {ui_dump_path}: expected {actual_ui_dump_sha256}, got {raw_ui_dump_sha256}.",
                            manifest=str(manifests[0]),
                            capture_path=str(ui_dump_path),
                            sha256=raw_ui_dump_sha256,
                        )
            validated_pngs.append(capture_path)

        pngs = validated_pngs

    if expected_route is not None:
        expected_canonical = _canonical_screenshot_route(expected_route)
        assert expected_canonical is not None
        if expected_canonical == "all":
            if manifest_requested_route != "all":
                return _status(
                    "missing_artifact",
                    "Artifact manifest does not report the requested route 'all'.",
                    manifest=str(manifests[0]),
                    requested_route=manifest_requested_route,
                )
            if manifest_routes != EXPECTED_ALL_SCREENSHOT_ROUTES:
                if len(manifest_routes) != len(EXPECTED_ALL_SCREENSHOT_ROUTES):
                    return _status(
                        "missing_artifact",
                        f"Artifact manifest contains {len(manifest_routes)} routes; expected exactly {len(EXPECTED_ALL_SCREENSHOT_ROUTES)} for route 'all'.",
                        manifest=str(manifests[0]),
                        routes=manifest_routes,
                    )
                return _status(
                    "missing_artifact",
                    "Artifact manifest routes for 'all' must exactly match: "
                    f"{', '.join(EXPECTED_ALL_SCREENSHOT_ROUTES)} in order.",
                    manifest=str(manifests[0]),
                    routes=manifest_routes,
                )
        else:
            if manifest_requested_route not in {expected_canonical, None}:
                return _status(
                    "missing_artifact",
                    f"Artifact manifest reports route {manifest_requested_route!r} instead of {expected_canonical!r}.",
                    manifest=str(manifests[0]),
                    routes=manifest_routes,
                    requested_route=manifest_requested_route,
                )
            if manifest_routes != [expected_canonical]:
                return _status(
                    "missing_artifact",
                    f"Artifact manifest routes must exactly match [{expected_canonical!r}] for route-specific runs.",
                    manifest=str(manifests[0]),
                    routes=manifest_routes,
                    requested_route=manifest_requested_route,
                )

    return _status(
        "passed",
        "Remote screenshot artifact contains a valid manifest, matching routes, capture hashes, PNG screenshots, and any declared UIAutomator dumps.",
        manifest=str(manifests[0]),
        pngs=[str(path) for path in pngs],
        routes=manifest_routes,
        requested_route=manifest_requested_route,
        captures=[
            {
                "route": str(entry["route"]),
                "path": str(cast(Path, entry["path"])),
                "orientation": str(entry["orientation"]),
                "launch_target": str(entry["launch_target"]),
                "scroll_position": str(entry["scroll_position"]),
                "scroll_y": entry["scroll_y"],
                "scrollable": bool(entry["scrollable"]),
                "sha256": str(entry["sha256"]),
                "uiautomator_dump_path": str(cast(Path, entry["uiautomator_dump_path"])) if entry.get("uiautomator_dump_path") else "",
                "uiautomator_dump_sha256": str(entry["uiautomator_dump_sha256"]),
            }
            for entry in capture_entries
        ],
    )


def run_remote_screenshots(
    *,
    repo_root: Path,
    workflow: str,
    artifact: str,
    screenshot_route: str,
    out_dir: Path,
    push_pr_branch: bool = False,
    require_remote_screenshots: bool = False,
    runner: Runner = run_command,
    run_lookup_attempts: int = 12,
    run_lookup_sleep_seconds: float = 5.0,
) -> dict[str, object]:
    repo_root = repo_root.resolve()
    out_dir = out_dir.resolve()

    if os.environ.get("GITHUB_ACTIONS") == "true":
        status = "failed" if require_remote_screenshots else "remote_visual_pending"
        return _status(status, "Refusing to dispatch the screenshot workflow from inside GitHub Actions to avoid self-looping.")

    branch, branch_error = _current_branch(repo_root, runner)
    if branch_error:
        return _status("failed", branch_error)
    assert branch is not None
    default_branch = _default_branch(repo_root, runner)
    if _is_protected_branch(branch, default_branch):
        return _status("failed", f"Refusing to run on protected branch {branch!r}; use a non-main PR branch.")

    sha, sha_error = _current_sha(repo_root, runner)
    if sha_error:
        return _status("failed", sha_error)
    assert sha is not None

    gh_error = _gh_ready(repo_root, runner, require_remote_screenshots)
    if gh_error:
        return gh_error

    if push_pr_branch:
        push = _run(runner, ["git", "push", "-u", "origin", branch], repo_root)
        if push.returncode != 0:
            status = "failed" if require_remote_screenshots else "remote_visual_pending"
            return _status(status, f"Unable to push branch {branch!r}; remote screenshots require the commit to exist on GitHub.", stderr=push.stderr.strip())

    dispatch = _run(runner, ["gh", "workflow", "run", workflow, "--ref", branch, "-f", f"screenshot_route={screenshot_route}"], repo_root)
    if dispatch.returncode != 0:
        status = "failed" if require_remote_screenshots else "remote_visual_pending"
        return _status(status, f"Unable to dispatch workflow {workflow!r}.", stderr=dispatch.stderr.strip())

    run_id, run = _find_run_for_sha(repo_root, runner, workflow, branch, sha, run_lookup_attempts, run_lookup_sleep_seconds)
    if run_id is None:
        assert run is not None
        if require_remote_screenshots:
            run["status"] = "failed"
        return run

    watch = _run(runner, ["gh", "run", "watch", str(run_id), "--exit-status"], repo_root)
    if watch.returncode != 0:
        return _status("failed", f"Screenshot workflow run {run_id} failed or did not complete successfully.", run_id=run_id, stderr=watch.stderr.strip())

    out_dir.mkdir(parents=True, exist_ok=True)
    download = _run(runner, ["gh", "run", "download", str(run_id), "--name", artifact, "--dir", str(out_dir)], repo_root)
    if download.returncode != 0:
        return _status("missing_artifact", f"Unable to download artifact {artifact!r} from run {run_id}.", run_id=run_id, stderr=download.stderr.strip())

    result = validate_artifact(out_dir, expected_route=screenshot_route)
    result["run_id"] = run_id
    result["branch"] = branch
    result["head_sha"] = sha
    return result


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Dispatch and validate Kani Android screenshots from GitHub Actions.")
    parser.add_argument("--repo-root", type=Path, default=Path("."))
    parser.add_argument("--workflow", "--remote-screenshot-workflow", dest="workflow", default="android-screenshots.yml")
    parser.add_argument("--artifact", "--screenshot-artifact", dest="artifact", default="android-screenshots")
    parser.add_argument("--screenshot-route", default="all")
    parser.add_argument("--out", type=Path, default=Path(".ralph-loop/current/remote-screenshots"))
    parser.add_argument("--push-pr-branch", action="store_true")
    parser.add_argument("--require-remote-screenshots", action="store_true")
    parser.add_argument("--run-lookup-attempts", type=int, default=12)
    parser.add_argument("--run-lookup-sleep-seconds", type=float, default=5.0)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    result = run_remote_screenshots(
        repo_root=args.repo_root,
        workflow=args.workflow,
        artifact=args.artifact,
        screenshot_route=args.screenshot_route,
        out_dir=args.out,
        push_pr_branch=args.push_pr_branch,
        require_remote_screenshots=args.require_remote_screenshots,
        run_lookup_attempts=args.run_lookup_attempts,
        run_lookup_sleep_seconds=args.run_lookup_sleep_seconds,
    )
    print(json.dumps(result, indent=2, sort_keys=True))
    if result["status"] in {"passed", "remote_visual_pending"}:
        return 0
    return 1


if __name__ == "__main__":
    sys.exit(main())
