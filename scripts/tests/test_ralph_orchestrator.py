#!/usr/bin/env python3

from __future__ import annotations

import json
import shutil
import tempfile
import unittest
from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path
from subprocess import CompletedProcess
from unittest.mock import patch
from typing import cast

from scripts.ralph_loop import button_contract, orchestrator


class RalphOrchestratorTest(unittest.TestCase):
    def test_parser_accepts_audit_flags_and_caps_iterations(self) -> None:
        parser = orchestrator.build_parser()
        root = Path("/tmp/ralph-audit-fixture")

        args = parser.parse_args(
            [
                "--repo-root",
                str(root),
                "--run-dir",
                ".ralph-loop/current",
                "--audit-only",
                "--file-bucket",
                "settings",
                "--max-files",
                "2",
                "--critic-profile",
                "design-critic",
                "--button-profile",
                "qa",
                "--critic-cmd",
                "hermes -p {profile} chat -Q -t safe -q {prompt}",
                "--button-cmd",
                "hermes -p {profile} chat -Q -t safe -q {prompt}",
                "--agent-cmd",
                "hermes -p agent chat -Q -t safe -q {prompt}",
                "--reviewer-model",
                "gpt-5.4-mini",
                "--reviewer-cmd",
                "hermes -p reviewer chat -Q -t safe -q {prompt}",
                "--pr-branch",
                "feat/ralph-audit",
                "--push-pr-branch",
                "--require-remote-green",
                "--iterations",
                "1",
                "--max-iterations",
                "3",
            ]
        )

        self.assertTrue(args.audit_only)
        self.assertEqual("audit-only", orchestrator.run_mode(args))
        self.assertEqual("settings", args.file_bucket)
        shared_args = parser.parse_args(["--repo-root", str(root), "--audit-only", "--file-bucket", "shared"])
        self.assertEqual("shared", shared_args.file_bucket)
        self.assertEqual(2, args.max_files)
        self.assertEqual("design-critic", args.critic_profile)
        self.assertEqual("qa", args.button_profile)
        self.assertEqual("hermes -p {profile} chat -Q -t safe -q {prompt}", args.critic_cmd)
        self.assertEqual("hermes -p {profile} chat -Q -t safe -q {prompt}", args.button_cmd)
        self.assertEqual("hermes -p agent chat -Q -t safe -q {prompt}", args.agent_cmd)
        self.assertEqual("gpt-5.4-mini", args.reviewer_model)
        self.assertEqual("hermes -p reviewer chat -Q -t safe -q {prompt}", args.reviewer_cmd)
        self.assertEqual("feat/ralph-audit", args.pr_branch)
        self.assertTrue(args.push_pr_branch)
        self.assertTrue(args.require_remote_green)
        self.assertEqual(0.10, args.min_design_score_delta)
        self.assertEqual(root, args.repo_root)
        self.assertEqual(Path(".ralph-loop/current"), args.run_dir)
        self.assertEqual("hermes -p {profile} chat -Q -t safe -q {prompt}", parser.get_default("critic_cmd"))
        self.assertEqual("hermes -p {profile} chat -Q -t safe -q {prompt}", parser.get_default("button_cmd"))

        with self.assertRaises(SystemExit):
            parser.parse_args(["--repo-root", str(root), "--file-bucket", "bogus"])
        with self.assertRaises(SystemExit):
            parser.parse_args(["--repo-root", str(root), "--iterations", "2", "--max-iterations", "1"])
        with self.assertRaises(SystemExit):
            parser.parse_args(["--repo-root", str(root), "--max-files", "0"])

    def test_parser_accepts_mutation_modes_but_rejects_unsafe_combinations(self) -> None:
        parser = orchestrator.build_parser()
        root = Path("/tmp/ralph-mode-fixture")

        dry_run = parser.parse_args(
            [
                "--repo-root",
                str(root),
                "--dry-run",
                "--min-design-score-delta",
                "0.25",
            ]
        )
        apply_only = parser.parse_args(["--repo-root", str(root), "--apply-accepted"])
        commit = parser.parse_args(["--repo-root", str(root), "--apply-accepted", "--commit-accepted"])
        remote_visual = parser.parse_args(["--repo-root", str(root)])

        self.assertTrue(dry_run.dry_run)
        self.assertEqual("dry-run", orchestrator.run_mode(dry_run))
        self.assertEqual(0.25, dry_run.min_design_score_delta)
        self.assertEqual("apply-accepted", orchestrator.run_mode(apply_only))
        self.assertEqual("commit-accepted", orchestrator.run_mode(commit))
        self.assertEqual("remote-visual", orchestrator.run_mode(remote_visual))

        invalid_combinations = [
            ["--audit-only", "--dry-run"],
            ["--audit-only", "--apply-accepted"],
            ["--dry-run", "--apply-accepted"],
            ["--commit-accepted"],
            ["--min-design-score-delta", "-0.01"],
            ["--min-design-score-delta", "nan"],
            ["--min-design-score-delta", "inf"],
        ]
        for extra_args in invalid_combinations:
            with self.subTest(extra_args=extra_args):
                with self.assertRaises(SystemExit):
                    parser.parse_args(["--repo-root", str(root), *extra_args])

    def test_dry_run_prepares_scratch_checkout_and_leaves_source_clean(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source_texts = self._write_fixture_repo(root)
            project_root = Path(__file__).resolve().parents[2]
            shutil.copytree(project_root / "scripts" / "prompts", root / "scripts" / "prompts")
            args = orchestrator.build_parser().parse_args(
                [
                    "--repo-root",
                    str(root),
                    "--run-dir",
                    ".ralph-loop/current",
                    "--dry-run",
                    "--file-bucket",
                    "all",
                    "--max-files",
                    "1",
                    "--critic-cmd",
                    "hermes -p {profile} chat -Q -t safe -q {prompt}",
                    "--button-cmd",
                    "hermes -p {profile} chat -Q -t safe -q {prompt}",
                    "--agent-cmd",
                    "hermes -p {profile} chat -Q -t safe -q {prompt}",
                    "--critic-profile",
                    "design",
                    "--button-profile",
                    "uitester",
                ]
            )

            calls: list[tuple[str, Path]] = []
            screenshot_calls: list[tuple[str, Path]] = []
            scratch_checkout = root / "scratch-checkout" / "source"
            accepted_issue = {
                "id": "home-primary-action-hierarchy",
                "title": "Primary action is visually buried",
                "severity": "medium",
                "evidence": "home.png shows the Study button below two equally weighted secondary cards",
                "primary_file": "app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt",
                "file": "app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt",
                "expected_fix": "Make the existing Study action visually dominant without changing behavior",
                "acceptance_criteria": ["after screenshot makes Study the clear primary action"],
                "do_not_touch": ["scheduler semantics", "sync/provider/storage"],
            }
            target_view_spec = {
                "summary": "The Study CTA is the dominant next step while secondary cards stay available.",
                "hierarchy": ["Study CTA has the strongest visual weight"],
                "copy_changes": ["No copy semantics change"],
                "spacing_touch_targets": ["Keep tappable areas at least 48dp"],
                "accessibility": ["Preserve test tags and content descriptions"],
                "material_expectations": ["Use existing Material card/button styles"],
            }
            design_stdout = json.dumps(
                {
                    "schema": "cheap-ralph-design-critic-v1",
                    "passed": False,
                    "view_id": "home-default",
                    "before_screenshot_sha256": "a" * 64,
                    "score_before": 0.62,
                    "accepted_issue": accepted_issue,
                    "target_view_spec": target_view_spec,
                    "target_screenshot": None,
                    "target_screenshot_unavailable_reason": "image generation not configured",
                    "rejected_issues": [],
                    "do_not_touch": ["learning correctness", "provider/sync/storage", "release/signing/build/CI"],
                }
            )
            button_stdout = json.dumps(
                {
                    "passed": True,
                    "missing_contract_rows": [],
                    "missing_click_tests": [],
                    "missing_disabled_state_tests": [],
                    "a11y_gaps": [],
                    "highest_priority_fix": {
                        "row": "home-study-cta",
                        "reason": "Already covered by the existing test",
                        "test_file": "app/src/androidTest/kotlin/dev/bee/kanjianki/HomeScreenComposeTest.kt",
                    },
                }
            )
            patch_path = root / ".ralph-loop/current/dry-run/implementation/candidate.patch"
            agent_stdout = json.dumps(
                {
                    "schema": "cheap-ralph-ui-implementer-v1",
                    "passed": True,
                    "accepted_issue": accepted_issue,
                    "target_view_spec": target_view_spec,
                    "changed_files": [
                        "app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt",
                        "app/src/androidTest/kotlin/dev/bee/kanjianki/HomeScreenComposeTest.kt",
                    ],
                    "tests_first": True,
                    "tests_run": [{"command": "./gradlew testDebugUnitTest", "result": "passed"}],
                    "patch_path": str(patch_path),
                    "after_screenshot_should_improve": True,
                    "blocked_reason": None,
                }
            )

            def fake_prepare_scratch_checkout(repo_root: Path, run_dir: Path) -> Path:
                calls.append(("prepare", repo_root.resolve()))
                scratch_checkout.parent.mkdir(parents=True, exist_ok=True)
                scratch_checkout.mkdir(parents=True, exist_ok=True)
                pointer_dir = run_dir / "scratch"
                pointer_dir.mkdir(parents=True, exist_ok=True)
                (pointer_dir / "path.txt").write_text(f"{scratch_checkout}\n", encoding="utf-8")
                (pointer_dir / "head-sha.txt").write_text("deadbeef\n", encoding="utf-8")
                return scratch_checkout

            def fake_run(args, cwd=None, text=None, capture_output=None, check=None):
                profile = args[2]
                cwd_path = Path(cwd).resolve() if cwd is not None else root.resolve()
                calls.append((profile, cwd_path))
                if profile == "design":
                    return CompletedProcess(args, 0, design_stdout, "")
                if profile == "uitester":
                    return CompletedProcess(args, 0, button_stdout, "")
                if profile == "agent":
                    patch_path.parent.mkdir(parents=True, exist_ok=True)
                    patch_path.write_text("diff --git a/foo b/foo\n", encoding="utf-8")
                    return CompletedProcess(args, 0, agent_stdout, "")
                raise AssertionError(f"unexpected profile command: {args}")

            def fake_capture_local_screenshots(
                repo_root: Path,
                out_dir: Path,
                screenshot_route: str,
                *,
                label: str,
                view_context: dict[str, object] | None = None,
            ) -> dict[str, object]:
                repo_path = Path(repo_root).resolve()
                capture_dir = Path(out_dir).resolve()
                screenshot_calls.append((label, repo_path))
                capture_dir.mkdir(parents=True, exist_ok=True)
                png_path = capture_dir / f"{label}.png"
                png_path.write_bytes(b"fake-png")
                result = {
                    "schema": "ralph-local-screenshot-v1",
                    "label": label,
                    "status": "passed",
                    "reason": "captured locally",
                    "capture_dir": str(capture_dir),
                    "requested_route": screenshot_route,
                    "routes": [screenshot_route],
                    "view_id": "home-default",
                    "fixture_id": "home-fixture",
                    "device_profile": "pixel-6",
                    "fixture_set_hash": "fixture-hash-123",
                    "pngs": [str(png_path)],
                    "captures": [
                        {
                            "route": screenshot_route,
                            "path": str(png_path),
                            "orientation": "portrait",
                            "launch_target": "home",
                            "sha256": "a" * 64,
                        }
                    ],
                }
                if view_context is not None:
                    result["view_context"] = view_context
                return result

            with (
                patch.object(
                    orchestrator.github_screenshots,
                    "run_remote_screenshots",
                    side_effect=AssertionError("dry-run should not dispatch screenshots"),
                ),
                patch.object(orchestrator, "_prepare_scratch_checkout", side_effect=fake_prepare_scratch_checkout),
                patch.object(orchestrator, "_capture_local_screenshots", side_effect=fake_capture_local_screenshots),
                patch.object(orchestrator.subprocess, "run", side_effect=fake_run),
            ):
                result = orchestrator.run(args)

            resolved_root = root.resolve()
            resolved_run_dir = (resolved_root / ".ralph-loop/current").resolve()
            self.assertEqual("failed", result["status"])
            self.assertEqual("dry-run", result["mode"])
            self.assertFalse(result["source_mutated"])
            self.assertTrue(result["cleanup_pending"])
            scratch_pointer_dir = resolved_run_dir / "scratch"
            self.assertEqual(str(scratch_checkout), result["scratch_checkout"])
            self.assertEqual(str(scratch_pointer_dir), result["scratch_pointer_dir"])
            self.assertEqual(accepted_issue, cast(dict[str, object], result["accepted_issue"]))
            self.assertEqual(str(scratch_checkout), (scratch_pointer_dir / "path.txt").read_text(encoding="utf-8").strip())
            self.assertEqual("deadbeef", (scratch_pointer_dir / "head-sha.txt").read_text(encoding="utf-8").strip())
            self.assertIn("design comparison rejected the change", str(result["reason"]))
            self.assertEqual(source_texts, self._current_source_texts(root))
            self.assertEqual(
                [
                    ("design", resolved_root),
                    ("uitester", resolved_root),
                    ("prepare", resolved_root),
                    ("agent", scratch_checkout.resolve()),
                    ("design", resolved_root),
                ],
                calls,
            )
            self.assertEqual(
                [("before", resolved_root), ("after", scratch_checkout.resolve())],
                screenshot_calls,
            )
            mode_state = resolved_run_dir / "mode-state.json"
            self.assertTrue(mode_state.exists())
            saved_state = json.loads(mode_state.read_text(encoding="utf-8"))
            self.assertEqual(result, saved_state)
            implementation = cast(dict[str, object], result["implementation"])
            self.assertEqual("passed", implementation["status"])
            self.assertTrue(Path(cast(str, implementation["prompt_path"])).exists())
            self.assertTrue(Path(cast(str, implementation["result_path"])).exists())
            self.assertTrue(patch_path.exists())
            parsed = cast(dict[str, object], implementation["parsed"])
            self.assertEqual("cheap-ralph-ui-implementer-v1", parsed["schema"])

    def test_audit_only_generates_artifacts_and_retries_button_review_once_with_qa(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source_texts = self._write_fixture_repo(root)
            project_root = Path(__file__).resolve().parents[2]
            shutil.copytree(project_root / "scripts" / "prompts", root / "scripts" / "prompts")

            call_profiles: list[str] = []
            design_stdout = json.dumps(
                {
                    "file": "app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt",
                    "components": [{"name": "HomeScreen", "role": "home surface", "states": ["default"]}],
                    "visual_strengths": ["Clear primary CTA"],
                    "visual_problems": [],
                    "interaction_a11y_problems": [],
                    "one_best_fix": {
                        "summary": "No UI change needed",
                        "file": "app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt",
                        "why_first": "No accepted issues",
                    },
                    "do_not_touch": ["study flow"],
                }
            )
            button_initial_stdout = json.dumps(
                {
                    "passed": False,
                    "missing_contract_rows": ["home-study-cta"],
                    "missing_click_tests": [],
                    "missing_disabled_state_tests": [],
                    "a11y_gaps": [],
                    "highest_priority_fix": {
                        "row": "home-study-cta",
                        "reason": "Retry with qa once before filing a backlog item",
                        "test_file": "app/src/androidTest/kotlin/dev/bee/kanjianki/HomeScreenComposeTest.kt",
                    },
                }
            )
            button_retry_stdout = json.dumps(
                {
                    "passed": True,
                    "missing_contract_rows": [],
                    "missing_click_tests": [],
                    "missing_disabled_state_tests": [],
                    "a11y_gaps": [],
                    "highest_priority_fix": {
                        "row": "home-study-cta",
                        "reason": "Already covered by the existing test",
                        "test_file": "app/src/androidTest/kotlin/dev/bee/kanjianki/HomeScreenComposeTest.kt",
                    },
                }
            )

            def fake_run(args, cwd=None, text=None, capture_output=None, check=None):
                self.assertEqual(root.resolve(), Path(cwd).resolve())
                self.assertGreaterEqual(len(args), 3)
                self.assertEqual("hermes", args[0])
                self.assertEqual("-p", args[1])
                profile = args[2]
                call_profiles.append(profile)
                if profile == "design":
                    return CompletedProcess(args, 0, design_stdout, "")
                if profile == "uitester":
                    return CompletedProcess(args, 0, button_initial_stdout, "uitester review asked for qa retry")
                if profile == "qa":
                    return CompletedProcess(args, 0, button_retry_stdout, "")
                raise AssertionError(f"unexpected profile command: {args}")

            with patch.object(orchestrator.github_screenshots, "run_remote_screenshots", side_effect=AssertionError("remote screenshots should not run in audit-only mode")), patch.object(orchestrator.subprocess, "run", side_effect=fake_run):
                with redirect_stdout(StringIO()):
                    exit_code = orchestrator.main(
                        [
                            "--repo-root",
                            str(root),
                            "--audit-only",
                            "--file-bucket",
                            "all",
                            "--max-files",
                            "1",
                            "--critic-cmd",
                            "hermes -p {profile} chat -Q -t safe -q {prompt}",
                            "--button-cmd",
                            "hermes -p {profile} chat -Q -t safe -q {prompt}",
                            "--critic-profile",
                            "design",
                            "--button-profile",
                            "uitester",
                            "--run-dir",
                            ".ralph-loop/current",
                        ]
                    )

            self.assertEqual(0, exit_code)
            self.assertEqual(["design", "uitester", "qa"], call_profiles)

            report_path = root / ".ralph-loop/current/audit-report.json"
            report = json.loads(report_path.read_text(encoding="utf-8"))
            self.assertEqual("passed", report["status"])
            self.assertEqual(7, report["summary"]["manifest_files"])
            self.assertEqual(len(button_contract.SEEDS), report["button_contract_summary"]["row_count"])
            self.assertEqual(9, report["ui_view_matrix_summary"]["view_count"])
            self.assertEqual(1, report["summary"]["selected_files"])
            self.assertEqual(1, report["summary"]["interactive_files"])
            self.assertEqual(1, report["summary"]["qa_retries"])
            self.assertEqual(0, report["summary"]["backlog_items"])
            self.assertEqual(1, len(report["file_reviews"]))

            review = report["file_reviews"][0]
            self.assertEqual("app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt", review["file"])
            self.assertEqual("home", review["bucket"])
            self.assertTrue(review["interactive"])
            self.assertEqual("passed", review["design"]["status"])
            self.assertEqual("passed", review["button"]["status"])
            self.assertTrue(review["button"]["used_qa_retry"])
            self.assertEqual(2, len(review["button"]["attempts"]))
            self.assertEqual("failed", review["button"]["attempts"][0]["status"])
            self.assertEqual("passed", review["button"]["attempts"][1]["status"])
            self.assertEqual([], report["backlog"])

            design_prompt_path = Path(review["design"]["prompt_path"])
            button_prompt_path = Path(review["button"]["attempts"][0]["prompt_path"])
            self.assertTrue(design_prompt_path.exists())
            self.assertTrue(button_prompt_path.exists())
            design_prompt_text = design_prompt_path.read_text(encoding="utf-8")
            button_prompt_text = button_prompt_path.read_text(encoding="utf-8")
            self.assertIn("File under review: app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt", design_prompt_text)
            self.assertIn('"schema": "ui-manifest-v1"', design_prompt_text)
            self.assertIn('"schema": "button-contract-v1"', button_prompt_text)
            self.assertNotIn("settings-save-toggle-reorder", button_prompt_text)
            self.assertNotIn("SettingsStudyLadderCompose.kt", button_prompt_text)
            self.assertEqual(source_texts, self._current_source_texts(root))
            self.assertTrue((root / ".ralph-loop/current/ui-manifest.json").exists())
            self.assertTrue((root / ".ralph-loop/current/ui-view-matrix.json").exists())
            self.assertTrue((root / ".ralph-loop/current/ui-view-matrix.md").exists())
            view_matrix = json.loads((root / ".ralph-loop/current/ui-view-matrix.json").read_text(encoding="utf-8"))
            self.assertEqual("ui-view-matrix-v1", view_matrix["schema"])
            self.assertTrue((root / ".ralph-loop/current/button-contract.json").exists())
            self.assertTrue((root / ".ralph-loop/current/button-contract.md").exists())
            self.assertTrue((root / ".ralph-loop/current/button-latency-inventory.json").exists())
            self.assertTrue((root / ".ralph-loop/current/button-latency-inventory.md").exists())
            latency_inventory = json.loads((root / ".ralph-loop/current/button-latency-inventory.json").read_text(encoding="utf-8"))
            self.assertEqual(".ralph-loop/current/ui-manifest.json", latency_inventory["source_manifest"])
            self.assertEqual(".ralph-loop/current/button-contract.json", latency_inventory["source_button_contract"])
            self.assertTrue((root / ".ralph-loop/current/audit-report.md").exists())

    def test_malformed_or_schema_missing_reviewer_json_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            review_dir = root / ".ralph-loop/current/audit/home"
            call_profiles: list[str] = []
            qa_stdout = json.dumps(
                {
                    "passed": True,
                    "missing_contract_rows": [],
                    "missing_click_tests": [],
                    "missing_disabled_state_tests": [],
                    "a11y_gaps": [],
                    "highest_priority_fix": {"row": "home-study-cta", "reason": "covered", "test_file": "HomeScreenComposeTest.kt"},
                }
            )

            def fake_run(args, cwd=None, text=None, capture_output=None, check=None):
                profile = args[2]
                call_profiles.append(profile)
                if profile == "uitester":
                    return CompletedProcess(args, 0, "not json", "")
                if profile == "qa":
                    return CompletedProcess(args, 0, qa_stdout, "")
                raise AssertionError(f"unexpected profile command: {args}")

            with patch.object(orchestrator.subprocess, "run", side_effect=fake_run):
                result = orchestrator._review_with_qa_retry(
                    "hermes -p {profile} chat -q {prompt}",
                    "prompt body",
                    root,
                    review_dir=review_dir,
                    initial_profile="uitester",
                )

            self.assertEqual(["uitester", "qa"], call_profiles)
            self.assertEqual("passed", result["status"])
            self.assertTrue(result["used_qa_retry"])
            attempts = cast(list[dict[str, object]], result["attempts"])
            first_attempt = attempts[0]
            second_attempt = attempts[1]
            schema_errors = cast(list[str], first_attempt["schema_errors"])
            self.assertEqual("failed", first_attempt["status"])
            self.assertIn("not valid JSON", schema_errors[0])
            self.assertEqual("passed", second_attempt["status"])

            def missing_schema_run(args, cwd=None, text=None, capture_output=None, check=None):
                return CompletedProcess(args, 0, json.dumps({"unexpected": True}), "")

            with patch.object(orchestrator.subprocess, "run", side_effect=missing_schema_run):
                missing_schema = orchestrator._run_profile_command(
                    "hermes -p {profile} chat -q {prompt}",
                    "prompt body",
                    root,
                    out_dir=review_dir,
                    label="button-attempt-1",
                    profile="uitester",
                )

            self.assertEqual("failed", missing_schema["status"])
            self.assertEqual(["button review JSON must include boolean 'passed'"], missing_schema["schema_errors"])

    def test_design_comparison_schema_rejects_old_issue_review_payload(self) -> None:
        old_issue_review = {"passed": True, "accepted_issues": [], "highest_priority_issue": None}
        comparison_review = {
            "schema": "cheap-ralph-design-comparison-v1",
            "passed": True,
            "after_better": True,
            "score_before": 0.62,
            "score_after": 0.77,
            "score_delta": 0.15,
            "issue_resolved": True,
            "new_regressions": [],
            "learning_correctness_risk": False,
            "rationale": "The after screenshot makes the primary action clearer without changing behavior.",
        }
        out_of_range_review = {
            **comparison_review,
            "score_before": 1.2,
            "score_after": 2.4,
            "score_delta": 1.2,
        }

        errors = orchestrator._review_schema_errors("design-comparison", old_issue_review)
        range_errors = orchestrator._review_schema_errors("design-comparison", out_of_range_review)

        self.assertEqual([], orchestrator._review_schema_errors("design-comparison", comparison_review))
        self.assertTrue(any("cheap-ralph-design-comparison-v1" in error for error in errors))
        self.assertTrue(any("after_better" in error for error in errors))
        self.assertTrue(any("score_delta" in error for error in errors))
        self.assertTrue(any("score_before" in error and "0.0..1.0" in error for error in range_errors))
        self.assertTrue(any("score_after" in error and "0.0..1.0" in error for error in range_errors))

    def test_design_critic_schema_requires_single_issue_and_target_spec(self) -> None:
        critic_review = {
            "schema": "cheap-ralph-design-critic-v1",
            "passed": False,
            "view_id": "home-default",
            "before_screenshot_sha256": "a" * 64,
            "score_before": 0.62,
            "accepted_issue": {
                "id": "home-primary-action-hierarchy",
                "title": "Primary action is visually buried",
                "severity": "medium",
                "evidence": "home.png shows the Study button below two equally weighted secondary cards",
                "primary_file": "app/src/main/kotlin/dev/bee/kanjianki/MainActivityHome.kt",
                "expected_fix": "Make the existing Study action visually dominant without changing behavior",
                "acceptance_criteria": ["after screenshot makes Study the clear primary action"],
                "do_not_touch": ["scheduler semantics", "sync/provider/storage"],
            },
            "target_view_spec": {
                "summary": "The Study CTA is the dominant next step while secondary cards stay available.",
                "hierarchy": ["Study CTA has the strongest visual weight"],
                "copy_changes": ["No copy semantics change"],
                "spacing_touch_targets": ["Keep tappable areas at least 48dp"],
                "accessibility": ["Preserve test tags and content descriptions"],
                "material_expectations": ["Use existing Material card/button styles"],
            },
            "target_screenshot": None,
            "target_screenshot_unavailable_reason": "image generation not configured",
            "rejected_issues": [],
            "do_not_touch": ["learning correctness", "provider/sync/storage", "release/signing/build/CI"],
        }
        no_issue_review = {
            **critic_review,
            "passed": True,
            "accepted_issue": None,
            "target_view_spec": None,
        }
        old_multi_issue_review = {
            "passed": False,
            "accepted_issues": [critic_review["accepted_issue"]],
            "highest_priority_issue": "home-primary-action-hierarchy",
        }
        missing_target_spec = {**critic_review, "target_view_spec": None}
        no_issue_missing_screenshot_reason = {**no_issue_review, "target_screenshot_unavailable_reason": ""}
        no_issue_empty_screenshot = {**no_issue_review, "target_screenshot": ""}
        stale_multi_issue_field = {**critic_review, "accepted_issues": [critic_review["accepted_issue"]]}

        self.assertEqual([], orchestrator._review_schema_errors("design-critic", critic_review))
        self.assertEqual([], orchestrator._review_schema_errors("design-critic", no_issue_review))

        old_errors = orchestrator._review_schema_errors("design-critic", old_multi_issue_review)
        design_label_old_errors = orchestrator._review_schema_errors("design", old_multi_issue_review)
        design_label_bare_errors = orchestrator._review_schema_errors("design", {"passed": True})
        missing_target_errors = orchestrator._review_schema_errors("design-critic", missing_target_spec)
        missing_screenshot_reason_errors = orchestrator._review_schema_errors(
            "design-critic", no_issue_missing_screenshot_reason
        )
        empty_screenshot_errors = orchestrator._review_schema_errors("design-critic", no_issue_empty_screenshot)
        stale_field_errors = orchestrator._review_schema_errors("design-critic", stale_multi_issue_field)

        self.assertTrue(any("cheap-ralph-design-critic-v1" in error for error in old_errors))
        self.assertTrue(any("single 'accepted_issue'" in error for error in old_errors))
        self.assertTrue(any("highest_priority_issue" in error for error in old_errors))
        self.assertTrue(any("single 'accepted_issue'" in error for error in design_label_old_errors))
        self.assertTrue(any("expected file-auditor fields" in error for error in design_label_bare_errors))
        self.assertTrue(any("target_view_spec" in error for error in missing_target_errors))
        self.assertTrue(any("target_screenshot_unavailable_reason" in error for error in missing_screenshot_reason_errors))
        self.assertTrue(any("target_screenshot must be a non-empty string" in error for error in empty_screenshot_errors))
        self.assertTrue(any("single 'accepted_issue'" in error for error in stale_field_errors))

        backlog = orchestrator._design_backlog_items(
            {
                "file": "app/src/main/kotlin/dev/bee/kanjianki/Fallback.kt",
                "design": {"status": "failed", "parsed": critic_review, "prompt_path": "p", "result_path": "r"},
            }
        )
        self.assertEqual(1, len(backlog))
        self.assertEqual("design-accepted-issue", backlog[0]["kind"])
        self.assertEqual("app/src/main/kotlin/dev/bee/kanjianki/MainActivityHome.kt", backlog[0]["file"])
        self.assertIn("Target view:", str(backlog[0]["reason"]))

        legacy_backlog = orchestrator._design_backlog_items(
            {
                "file": "app/src/main/kotlin/dev/bee/kanjianki/Fallback.kt",
                "design": {"status": "failed", "parsed": old_multi_issue_review, "prompt_path": "p", "result_path": "r"},
            }
        )
        self.assertEqual(1, len(legacy_backlog))
        self.assertEqual("design-command-failure", legacy_backlog[0]["kind"])
        self.assertIn("schema validation", str(legacy_backlog[0]["title"]))
        self.assertIn("single 'accepted_issue'", str(legacy_backlog[0]["reason"]))

    def test_ui_implementer_schema_requires_patch_and_target_spec(self) -> None:
        accepted_issue = {
            "id": "home-primary-action-hierarchy",
            "title": "Primary action is visually buried",
            "severity": "medium",
            "evidence": "home.png shows the Study button below two equally weighted secondary cards",
            "primary_file": "app/src/main/kotlin/dev/bee/kanjianki/MainActivityHome.kt",
            "file": "app/src/main/kotlin/dev/bee/kanjianki/MainActivityHome.kt",
            "expected_fix": "Make the existing Study action visually dominant without changing behavior",
        }
        target_view_spec = {
            "summary": "The Study CTA is the dominant next step while secondary cards stay available.",
            "hierarchy": ["Study CTA has the strongest visual weight"],
            "copy_changes": ["No copy semantics change"],
            "spacing_touch_targets": ["Keep tappable areas at least 48dp"],
            "accessibility": ["Preserve test tags and content descriptions"],
            "material_expectations": ["Use existing Material card/button styles"],
        }
        implementer_review = {
            "schema": "cheap-ralph-ui-implementer-v1",
            "passed": True,
            "accepted_issue": accepted_issue,
            "target_view_spec": target_view_spec,
            "changed_files": ["app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt"],
            "tests_first": True,
            "tests_run": [{"command": "./gradlew testDebugUnitTest", "result": "passed"}],
            "patch_path": "/tmp/cheap-ralph-run/dry-run/implementation/candidate.patch",
            "after_screenshot_should_improve": True,
            "blocked_reason": None,
        }
        blocked_review = {**implementer_review, "passed": False, "patch_path": None, "blocked_reason": "Need one more screenshot gate"}
        legacy_review = {
            "accepted_issue": accepted_issue,
            "changed_files": ["app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt"],
            "tests_first": True,
            "tests_run": [{"command": "./gradlew testDebugUnitTest", "result": "passed"}],
            "blocked_reason": None,
        }

        self.assertEqual([], orchestrator._review_schema_errors("ui-implementer", implementer_review))
        self.assertEqual([], orchestrator._review_schema_errors("ui-implementer", blocked_review))
        self.assertTrue(any("cheap-ralph-ui-implementer-v1" in error for error in orchestrator._review_schema_errors("ui-implementer", legacy_review)))

    def test_remote_visual_mode_uses_design_comparison_prompt_and_label(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            project_root = Path(__file__).resolve().parents[2]
            shutil.copytree(project_root / "scripts" / "prompts", root / "scripts" / "prompts")
            run_dir = root / ".ralph-loop/current"
            screenshot_dir = run_dir / "remote-screenshots"
            screenshot_dir.mkdir(parents=True)
            manifest_path = screenshot_dir / "manifest.json"
            manifest_path.write_text(
                json.dumps(
                    {
                        "schema": "android-screenshots-v1",
                        "requested_route": "home",
                        "routes": ["home"],
                        "files": [{"route": "home", "path": "home.png"}],
                    }
                ),
                encoding="utf-8",
            )
            remote_result = {"status": "passed", "manifest": str(manifest_path), "files": ["home.png"]}
            design_stdout = json.dumps(
                {
                    "schema": "cheap-ralph-design-comparison-v1",
                    "passed": True,
                    "after_better": True,
                    "score_before": 0.62,
                    "score_after": 0.77,
                    "score_delta": 0.15,
                    "issue_resolved": True,
                    "new_regressions": [],
                    "learning_correctness_risk": False,
                    "rationale": "The after screenshot improves hierarchy without changing study behavior.",
                }
            )
            button_stdout = json.dumps(
                {
                    "passed": True,
                    "missing_contract_rows": [],
                    "missing_click_tests": [],
                    "missing_disabled_state_tests": [],
                    "a11y_gaps": [],
                    "highest_priority_fix": {"row": "home-study-cta", "reason": "covered", "test_file": "HomeScreenComposeTest.kt"},
                }
            )
            seen_design_prompt: list[str] = []

            def fake_run(args, cwd=None, text=None, capture_output=None, check=None):
                self.assertIsNotNone(cwd)
                self.assertEqual(root.resolve(), Path(str(cwd)).resolve())
                profile = args[2]
                prompt = args[-1]
                if profile == "design":
                    seen_design_prompt.append(prompt)
                    self.assertIn("cheap-ralph-design-comparison-v1", prompt)
                    self.assertIn("after_better", prompt)
                    self.assertIn("visual acceptance gate", prompt)
                    return CompletedProcess(args, 0, design_stdout, "")
                if profile == "uitester":
                    return CompletedProcess(args, 0, button_stdout, "")
                raise AssertionError(f"unexpected profile command: {args}")

            args = orchestrator.build_parser().parse_args(
                [
                    "--repo-root",
                    str(root),
                    "--run-dir",
                    ".ralph-loop/current",
                    "--critic-cmd",
                    "hermes -p {profile} chat -Q -t safe -q {prompt}",
                    "--button-cmd",
                    "hermes -p {profile} chat -Q -t safe -q {prompt}",
                    "--critic-profile",
                    "design",
                    "--button-profile",
                    "uitester",
                ]
            )

            with patch.object(orchestrator.github_screenshots, "run_remote_screenshots", return_value=remote_result), patch.object(orchestrator.subprocess, "run", side_effect=fake_run):
                result = orchestrator._run_remote_visual_mode(args, root, run_dir)

            self.assertEqual("passed", result["status"])
            self.assertEqual(1, len(seen_design_prompt))
            profile_reviews = cast(dict[str, dict[str, object]], result["profile_reviews"])
            design_review = profile_reviews["design"]
            self.assertEqual("design-comparison", design_review["label"])
            self.assertEqual("cheap-ralph-design-comparison-v1", cast(dict[str, object], design_review["parsed"])["schema"])
            self.assertEqual("design-comparison.prompt.txt", Path(cast(str, design_review["prompt_path"])).name)

    def test_audit_only_preserves_raw_prompt_and_status_when_button_retry_still_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source_texts = self._write_fixture_repo(root)
            project_root = Path(__file__).resolve().parents[2]
            shutil.copytree(project_root / "scripts" / "prompts", root / "scripts" / "prompts")

            call_profiles: list[str] = []
            design_stdout = json.dumps(
                {
                    "file": "app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt",
                    "components": [{"name": "HomeScreen", "role": "home surface", "states": ["default"]}],
                    "visual_strengths": ["Clear primary CTA"],
                    "visual_problems": [],
                    "interaction_a11y_problems": [],
                    "one_best_fix": {
                        "summary": "No UI change needed",
                        "file": "app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt",
                        "why_first": "No accepted issues",
                    },
                    "do_not_touch": ["study flow"],
                }
            )
            button_initial_stdout = json.dumps(
                {
                    "passed": False,
                    "missing_contract_rows": ["home-study-cta"],
                    "missing_click_tests": [],
                    "missing_disabled_state_tests": [],
                    "a11y_gaps": [],
                    "highest_priority_fix": {
                        "row": "home-study-cta",
                        "reason": "Retry with qa once before filing a backlog item",
                        "test_file": "app/src/androidTest/kotlin/dev/bee/kanjianki/HomeScreenComposeTest.kt",
                    },
                }
            )
            button_retry_stdout = json.dumps(
                {
                    "passed": False,
                    "missing_contract_rows": ["home-study-cta"],
                    "missing_click_tests": ["home-study-cta"],
                    "missing_disabled_state_tests": [],
                    "a11y_gaps": [{"row": "home-study-cta", "gap": "Low-contrast CTA"}],
                    "highest_priority_fix": {
                        "row": "home-study-cta",
                        "reason": "The CTA still lacks a focused click test after the qa retry",
                        "test_file": "app/src/androidTest/kotlin/dev/bee/kanjianki/HomeScreenComposeTest.kt",
                    },
                }
            )

            def fake_run(args, cwd=None, text=None, capture_output=None, check=None):
                self.assertEqual(root.resolve(), Path(cwd).resolve())
                self.assertGreaterEqual(len(args), 3)
                self.assertEqual("hermes", args[0])
                self.assertEqual("-p", args[1])
                profile = args[2]
                call_profiles.append(profile)
                if profile == "design":
                    return CompletedProcess(args, 0, design_stdout, "")
                if profile == "uitester":
                    return CompletedProcess(args, 0, button_initial_stdout, "uitester review asked for qa retry")
                if profile == "qa":
                    return CompletedProcess(args, 0, button_retry_stdout, "qa still sees missing button coverage")
                raise AssertionError(f"unexpected profile command: {args}")

            with patch.object(orchestrator.github_screenshots, "run_remote_screenshots", side_effect=AssertionError("remote screenshots should not run in audit-only mode")), patch.object(orchestrator.subprocess, "run", side_effect=fake_run):
                with redirect_stdout(StringIO()):
                    exit_code = orchestrator.main(
                        [
                            "--repo-root",
                            str(root),
                            "--audit-only",
                            "--file-bucket",
                            "all",
                            "--max-files",
                            "1",
                            "--critic-cmd",
                            "hermes -p {profile} chat -Q -t safe -q {prompt}",
                            "--button-cmd",
                            "hermes -p {profile} chat -Q -t safe -q {prompt}",
                            "--critic-profile",
                            "design",
                            "--button-profile",
                            "uitester",
                            "--run-dir",
                            ".ralph-loop/current",
                        ]
                    )

            self.assertEqual(1, exit_code)
            self.assertEqual(["design", "uitester", "qa"], call_profiles)

            report_path = root / ".ralph-loop/current/audit-report.json"
            report = json.loads(report_path.read_text(encoding="utf-8"))
            self.assertEqual("failed", report["status"])
            self.assertGreater(report["summary"]["backlog_items"], 0)
            self.assertEqual(1, len(report["file_reviews"]))

            review = report["file_reviews"][0]
            self.assertEqual("failed", review["button"]["status"])
            self.assertEqual(2, len(review["button"]["attempts"]))
            self.assertEqual(["failed", "failed"], [attempt["status"] for attempt in review["button"]["attempts"]])
            self.assertEqual("button-missing-contract-row", report["backlog"][0]["kind"])
            self.assertEqual("app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt", report["backlog"][0]["file"])

            first_attempt = review["button"]["attempts"][0]
            second_attempt = review["button"]["attempts"][1]
            self.assertTrue(Path(first_attempt["prompt_path"]).exists())
            self.assertTrue(Path(first_attempt["result_path"]).exists())
            self.assertTrue(Path(second_attempt["result_path"]).exists())
            self.assertIn('"status": "failed"', Path(first_attempt["result_path"]).read_text(encoding="utf-8"))
            self.assertIn('"status": "failed"', Path(second_attempt["result_path"]).read_text(encoding="utf-8"))
            self.assertEqual(source_texts, self._current_source_texts(root))

    def test_apply_and_commit_modes_apply_candidate_patch_to_source_checkout(self) -> None:
        for commit_mode in (False, True):
            with self.subTest(commit_mode=commit_mode):
                with tempfile.TemporaryDirectory() as temp:
                    root = Path(temp)
                    source_texts = self._write_fixture_repo(root)
                    project_root = Path(__file__).resolve().parents[2]
                    shutil.copytree(project_root / "scripts" / "prompts", root / "scripts" / "prompts")

                    mode_args = ["--apply-accepted"]
                    if commit_mode:
                        mode_args.append("--commit-accepted")
                    args = orchestrator.build_parser().parse_args(
                        [
                            "--repo-root",
                            str(root),
                            "--run-dir",
                            ".ralph-loop/current",
                            *mode_args,
                            "--file-bucket",
                            "all",
                            "--max-files",
                            "1",
                            "--critic-cmd",
                            "hermes -p {profile} chat -Q -t safe -q {prompt}",
                            "--button-cmd",
                            "hermes -p {profile} chat -Q -t safe -q {prompt}",
                            "--agent-cmd",
                            "hermes -p {profile} chat -Q -t safe -q {prompt}",
                            "--critic-profile",
                            "design",
                            "--button-profile",
                            "uitester",
                        ]
                    )

                    calls: list[tuple[str, Path]] = []
                    git_calls: list[list[str]] = []
                    screenshot_calls: list[tuple[str, Path]] = []
                    scratch_checkout = root / "scratch-checkout" / "source"
                    accepted_issue = {
                        "id": "home-primary-action-hierarchy",
                        "title": "Primary action is visually buried",
                        "severity": "medium",
                        "evidence": "home.png shows the Study button below two equally weighted secondary cards",
                        "primary_file": "app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt",
                        "file": "app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt",
                        "expected_fix": "Make the existing Study action visually dominant without changing behavior",
                        "acceptance_criteria": ["after screenshot makes the Study CTA the clear primary action"],
                        "do_not_touch": ["scheduler semantics", "sync/provider/storage"],
                    }
                    target_view_spec = {
                        "summary": "The Study CTA is the dominant next step while secondary cards stay available.",
                        "hierarchy": ["Study CTA has the strongest visual weight"],
                        "copy_changes": ["No copy semantics change"],
                        "spacing_touch_targets": ["Keep tappable areas at least 48dp"],
                        "accessibility": ["Preserve test tags and content descriptions"],
                        "material_expectations": ["Use existing Material card/button styles"],
                    }
                    design_critic_stdout = json.dumps(
                        {
                            "schema": "cheap-ralph-design-critic-v1",
                            "passed": False,
                            "view_id": "home-default",
                            "before_screenshot_sha256": "a" * 64,
                            "score_before": 0.62,
                            "accepted_issue": accepted_issue,
                            "target_view_spec": target_view_spec,
                            "target_screenshot": None,
                            "target_screenshot_unavailable_reason": "image generation not configured",
                            "rejected_issues": [],
                            "do_not_touch": ["learning correctness", "provider/sync/storage", "release/signing/build/CI"],
                        }
                    )
                    design_comparison_stdout = json.dumps(
                        {
                            "schema": "cheap-ralph-design-comparison-v1",
                            "passed": True,
                            "after_better": True,
                            "score_before": 0.62,
                            "score_after": 0.81,
                            "score_delta": 0.19,
                            "issue_resolved": True,
                            "new_regressions": [],
                            "learning_correctness_risk": False,
                            "rationale": "The after screenshot improves hierarchy without changing study behavior.",
                        }
                    )
                    button_stdout = json.dumps(
                        {
                            "passed": True,
                            "missing_contract_rows": [],
                            "missing_click_tests": [],
                            "missing_disabled_state_tests": [],
                            "a11y_gaps": [],
                            "highest_priority_fix": {
                                "row": "home-study-cta",
                                "reason": "Already covered by the existing test",
                                "test_file": "app/src/androidTest/kotlin/dev/bee/kanjianki/HomeScreenComposeTest.kt",
                            },
                        }
                    )
                    patch_path = root / ".ralph-loop/current/dry-run/implementation/candidate.patch"
                    patch_text = (
                        "diff --git a/app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt b/app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt\n"
                        "--- a/app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt\n"
                        "+++ b/app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt\n"
                        "@@\n"
                        "-                        Text(\"Study now\")\n"
                        "+                        Text(\"Study now!\")\n"
                    )
                    agent_stdout = json.dumps(
                        {
                            "schema": "cheap-ralph-ui-implementer-v1",
                            "passed": True,
                            "accepted_issue": accepted_issue,
                            "target_view_spec": target_view_spec,
                            "changed_files": [
                                "app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt",
                                "app/src/androidTest/kotlin/dev/bee/kanjianki/HomeScreenComposeTest.kt",
                            ],
                            "tests_first": True,
                            "tests_run": [{"command": "./gradlew testDebugUnitTest", "result": "passed"}],
                            "patch_path": str(patch_path),
                            "after_screenshot_should_improve": True,
                            "blocked_reason": None,
                        }
                    )
                    design_call_count = 0

                    def fake_prepare_scratch_checkout(repo_root: Path, run_dir: Path) -> Path:
                        calls.append(("prepare", repo_root.resolve()))
                        scratch_checkout.parent.mkdir(parents=True, exist_ok=True)
                        scratch_checkout.mkdir(parents=True, exist_ok=True)
                        pointer_dir = run_dir / "scratch"
                        pointer_dir.mkdir(parents=True, exist_ok=True)
                        (pointer_dir / "path.txt").write_text(f"{scratch_checkout}\n", encoding="utf-8")
                        (pointer_dir / "head-sha.txt").write_text("deadbeef\n", encoding="utf-8")
                        return scratch_checkout

                    def fake_run(args, cwd=None, text=None, capture_output=None, check=None):
                        nonlocal design_call_count
                        cwd_path = Path(cwd).resolve() if cwd is not None else root.resolve()
                        if args[0] == "hermes":
                            self.assertGreaterEqual(len(args), 3)
                            self.assertEqual("-p", args[1])
                            profile = args[2]
                            calls.append((profile, cwd_path))
                            if profile == "design":
                                self.assertEqual(root.resolve(), cwd_path)
                                design_call_count += 1
                                if design_call_count == 1:
                                    return CompletedProcess(args, 0, design_critic_stdout, "")
                                return CompletedProcess(args, 0, design_comparison_stdout, "")
                            if profile == "uitester":
                                self.assertEqual(root.resolve(), cwd_path)
                                return CompletedProcess(args, 0, button_stdout, "")
                            if profile == "agent":
                                self.assertEqual(scratch_checkout.resolve(), cwd_path)
                                patch_path.parent.mkdir(parents=True, exist_ok=True)
                                patch_path.write_text(patch_text, encoding="utf-8")
                                return CompletedProcess(args, 0, agent_stdout, "")
                            raise AssertionError(f"unexpected profile command: {args}")
                        git_calls.append(list(args))
                        self.assertEqual(root.resolve(), cwd_path)
                        if args[:3] == ["git", "apply", "--check"]:
                            return CompletedProcess(args, 0, "", "")
                        if args[:2] == ["git", "apply"]:
                            home_path = root / "app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt"
                            home_path.write_text(
                                home_path.read_text(encoding="utf-8").replace(
                                    'Text("Study now")',
                                    'Text("Study now!")',
                                ),
                                encoding="utf-8",
                            )
                            return CompletedProcess(args, 0, "", "")
                        if args[:2] == ["git", "add"]:
                            return CompletedProcess(args, 0, "", "")
                        if args[:2] == ["git", "commit"]:
                            return CompletedProcess(args, 0, "[mock commit]", "")
                        raise AssertionError(f"unexpected git command: {args}")

                    def fake_capture_local_screenshots(
                        repo_root: Path,
                        out_dir: Path,
                        screenshot_route: str,
                        *,
                        label: str,
                        view_context: dict[str, object] | None = None,
                    ) -> dict[str, object]:
                        repo_path = Path(repo_root).resolve()
                        capture_dir = Path(out_dir).resolve()
                        screenshot_calls.append((label, repo_path))
                        capture_dir.mkdir(parents=True, exist_ok=True)
                        png_path = capture_dir / f"{label}.png"
                        png_path.write_bytes(b"fake-png")
                        result = {
                            "schema": "ralph-local-screenshot-v1",
                            "label": label,
                            "status": "passed",
                            "reason": "captured locally",
                            "capture_dir": str(capture_dir),
                            "requested_route": screenshot_route,
                            "routes": [screenshot_route],
                            "view_id": "home-default",
                            "fixture_id": "home-fixture",
                            "device_profile": "pixel-6",
                            "fixture_set_hash": "fixture-hash-123",
                            "pngs": [str(png_path)],
                            "captures": [
                                {
                                    "route": screenshot_route,
                                    "path": str(png_path),
                                    "orientation": "portrait",
                                    "launch_target": "home",
                                    "sha256": "a" * 64,
                                }
                            ],
                        }
                        if view_context is not None:
                            result["view_context"] = view_context
                        return result

                    with (
                        patch.object(
                            orchestrator.github_screenshots,
                            "run_remote_screenshots",
                            side_effect=AssertionError("apply/commit modes should not dispatch screenshots"),
                        ),
                        patch.object(orchestrator, "_prepare_scratch_checkout", side_effect=fake_prepare_scratch_checkout),
                        patch.object(orchestrator, "_capture_local_screenshots", side_effect=fake_capture_local_screenshots),
                        patch.object(orchestrator.subprocess, "run", side_effect=fake_run),
                    ):
                        result = orchestrator.run(args)

                    resolved_root = root.resolve()
                    resolved_run_dir = (resolved_root / ".ralph-loop/current").resolve()
                    self.assertEqual("passed", result["status"])
                    self.assertEqual("commit-accepted" if commit_mode else "apply-accepted", result["mode"])
                    self.assertTrue(result["source_mutated"])
                    self.assertTrue(result["cleanup_pending"])
                    self.assertEqual("committed" if commit_mode else "applied", result["decision"])
                    self.assertIn("apply_result", result)
                    self.assertEqual("passed", cast(dict[str, object], result["apply_result"])["status"])
                    if commit_mode:
                        self.assertIn("commit_result", result)
                        self.assertEqual("passed", cast(dict[str, object], result["commit_result"])["status"])
                    else:
                        self.assertNotIn("commit_result", result)
                    self.assertEqual(
                        [
                            ("design", resolved_root),
                            ("uitester", resolved_root),
                            ("prepare", resolved_root),
                            ("agent", scratch_checkout.resolve()),
                            ("design", resolved_root),
                        ],
                        calls,
                    )
                    self.assertEqual([("before", resolved_root), ("after", scratch_checkout.resolve())], screenshot_calls)
                    self.assertEqual(["git", "apply", "--check", str(patch_path)], git_calls[0])
                    self.assertEqual(["git", "apply", str(patch_path)], git_calls[1])
                    if commit_mode:
                        self.assertEqual(["git", "add", "-A"], git_calls[2])
                        self.assertEqual("git", git_calls[3][0])
                        self.assertEqual("commit", git_calls[3][1])
                    else:
                        self.assertEqual(2, len(git_calls))
                    expected_texts = dict(source_texts)
                    expected_texts["app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt"] = expected_texts[
                        "app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt"
                    ].replace('Text("Study now")', 'Text("Study now!")')
                    self.assertEqual(expected_texts, self._current_source_texts(root))
                    mode_state = resolved_run_dir / "mode-state.json"
                    self.assertTrue(mode_state.exists())
                    saved_state = json.loads(mode_state.read_text(encoding="utf-8"))
                    self.assertEqual(result, saved_state)

    def test_design_schema_problems_become_backlog_items(self) -> None:
        review = {
            "file": "app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt",
            "design": {
                "status": "passed",
                "prompt_path": "/tmp/design.prompt.txt",
                "result_path": "/tmp/design.result.json",
                "parsed": {
                    "file": "app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt",
                    "visual_problems": [
                        {
                            "severity": "high",
                            "component": "HomePrimaryCta",
                            "problem": "CTA hierarchy is ambiguous",
                            "evidence": "Primary and secondary actions use the same treatment",
                        }
                    ],
                    "interaction_a11y_problems": [
                        {
                            "severity": "medium",
                            "component": "Sync button",
                            "problem": "Loading state lacks assistive text",
                            "evidence": "Stateful action only changes spinner visibility",
                        }
                    ],
                },
            },
        }

        items = orchestrator._design_backlog_items(review)

        self.assertEqual(["design-visual-problem", "design-interaction-a11y-problem"], [item["kind"] for item in items])
        self.assertEqual([0, 1], [item["priority"] for item in items])
        self.assertIn("HomePrimaryCta", str(items[0]["title"]))
        self.assertEqual("app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt", items[0]["file"])
        self.assertEqual("/tmp/design.prompt.txt", items[0]["prompt_path"])

    def _write_fixture_repo(self, root: Path) -> dict[str, str]:
        fixtures = {
            "app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt": """
                package dev.bee.kanjianki

                import androidx.compose.runtime.Composable

                @Composable
                fun HomeScreen() {
                    HomePrimaryCta()
                }

                @Composable
                private fun HomePrimaryCta() {
                    Button(onClick = {}, modifier = Modifier.testTag(\"primary_home_cta\")) {
                        Text(\"Study now\")
                    }
                }
            """,
            "app/src/main/kotlin/dev/bee/kanjianki/StudyFlashcardCompose.kt": """
                package dev.bee.kanjianki

                import androidx.compose.runtime.Composable

                @Composable
                fun StudyFlashcard() {
                    TextField(value = \"\", onValueChange = {})
                }
            """,
            "app/src/main/kotlin/dev/bee/kanjianki/SettingsStudyLadderCompose.kt": """
                package dev.bee.kanjianki

                import androidx.compose.runtime.Composable

                @Composable
                fun SettingsStudyLadderPanel() {
                    Switch(checked = true, onCheckedChange = {})
                    Button(onClick = {}) {
                        Text(\"Up\")
                    }
                    Button(onClick = {}) {
                        Text(\"Restore defaults\")
                    }
                }
            """,
            "app/src/main/kotlin/dev/bee/kanjianki/ui/theme/KaniTheme.kt": """
                package dev.bee.kanjianki.ui.theme

                import androidx.compose.runtime.Composable

                @Composable
                fun KaniTheme(content: @Composable () -> Unit) {
                    content()
                }
            """,
            "app/src/androidTest/kotlin/dev/bee/kanjianki/HomeScreenComposeTest.kt": """
                package dev.bee.kanjianki

                class HomeScreenComposeTest {
                    @Test
                    fun home_button_is_clickable() {
                        compose.onNodeWithText(\"Study now\").performClick()
                    }
                }
            """,
            "app/src/androidTest/kotlin/dev/bee/kanjianki/StudyFlashcardComposeTest.kt": """
                package dev.bee.kanjianki

                class StudyFlashcardComposeTest {
                    @Test
                    fun study_field_accepts_input() {
                        compose.onNodeWithText(\"Answer\").performTextInput(\"abc\")
                    }
                }
            """,
            "app/src/androidTest/kotlin/dev/bee/kanjianki/SettingsStudyLadderComposeTest.kt": """
                package dev.bee.kanjianki

                class SettingsStudyLadderComposeTest {
                    @Test
                    fun ladder_switch_is_clickable() {
                        compose.onNodeWithText(\"Up\").performClick()
                    }
                }
            """,
        }
        written: dict[str, str] = {}
        for relative, text in fixtures.items():
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            content = text.strip() + "\n"
            path.write_text(content, encoding="utf-8")
            if "/main/" in relative:
                written[relative] = content
        return written

    def _current_source_texts(self, root: Path) -> dict[str, str]:
        return {
            relative: (root / relative).read_text(encoding="utf-8")
            for relative in (
                "app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt",
                "app/src/main/kotlin/dev/bee/kanjianki/StudyFlashcardCompose.kt",
                "app/src/main/kotlin/dev/bee/kanjianki/SettingsStudyLadderCompose.kt",
                "app/src/main/kotlin/dev/bee/kanjianki/ui/theme/KaniTheme.kt",
            )
        }


if __name__ == "__main__":
    unittest.main()
