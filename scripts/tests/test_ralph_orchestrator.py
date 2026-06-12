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
