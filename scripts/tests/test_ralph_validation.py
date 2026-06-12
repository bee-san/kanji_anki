#!/usr/bin/env python3

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from scripts.ralph_loop import button_contract
from scripts.ralph_loop import ui_manifest
from scripts.ralph_loop import validation


class RalphValidationTest(unittest.TestCase):
    def test_passes_when_ui_change_has_required_artifacts_and_gates(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            home_file, manifest, contract = self._build_home_ui_fixture(root, with_click_test=True)

            report = validation.build_validation_report(
                {
                    "branch": "feature/ralph-validation",
                    "default_branch": "main",
                    "changed_files": [home_file],
                    "dirty_paths": [],
                    "diff_lines": 42,
                    "commits_ahead": 0,
                    "manifest": manifest,
                    "button_contract": contract,
                    "targeted_compose_tests": [
                        {
                            "command": "./gradlew :app:testDebugUnitTest --tests dev.bee.kanjianki.HomeScreenComposeTest",
                            "status": "passed",
                        }
                    ],
                    "ci_fast_result": {"status": "passed"},
                    "ci_quality_result": {"status": "passed"},
                    "screenshot_result": {"status": "passed", "run_id": 77},
                    "design_review": self._passing_design_comparison(),
                    "button_review": {
                        "status": "passed",
                        "passed": True,
                        "missing_contract_rows": [],
                        "missing_click_tests": [],
                        "missing_disabled_state_tests": [],
                        "a11y_gaps": [],
                    },
                    "reviewer_result": {"status": "passed", "model": "gpt5.4-codex-mini"},
                    "require_remote_green": False,
                }
            )

        self.assertEqual("passed", report["status"])
        self.assertEqual("passed", self._gate(report, "branch_guard")["status"])
        self.assertEqual("passed", self._gate(report, "diff_size_guard")["status"])
        self.assertEqual("passed", self._gate(report, "button_contract_delta_guard")["status"])
        self.assertEqual("passed", self._gate(report, "targeted_compose_tests")["status"])
        self.assertEqual("passed", self._gate(report, "ci_fast_gate")["status"])
        self.assertEqual("passed", self._gate(report, "ci_quality_gate")["status"])
        self.assertEqual("passed", self._gate(report, "screenshot_availability")["status"])
        self.assertEqual("passed", self._gate(report, "design_comparison")["status"])
        self.assertEqual("passed", self._gate(report, "button_qa_review")["status"])
        self.assertEqual("passed", self._gate(report, "commit_push_frequency")["status"])
        self.assertEqual("passed", self._gate(report, "independent_review_gate")["status"])

    def test_branch_guard_and_diff_size_guard_fail_on_protected_branch_and_large_diff(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            changed_files = [f"app/src/main/kotlin/dev/bee/kanjianki/Home{index}.kt" for index in range(6)]
            report = validation.build_validation_report(
                {
                    "branch": "main",
                    "default_branch": "main",
                    "changed_files": changed_files,
                    "dirty_paths": [],
                    "diff_lines": 501,
                    "commits_ahead": 0,
                    "targeted_compose_tests": [],
                    "reviewer_result": {"status": "passed", "model": "gpt5.4-codex-mini"},
                    "require_remote_green": False,
                }
            )

        self.assertEqual("failed", report["status"])
        branch_guard = self._gate(report, "branch_guard")
        diff_size_guard = self._gate(report, "diff_size_guard")
        self.assertEqual("failed", branch_guard["status"])
        self.assertIn("non-main PR branch", branch_guard["message"])
        self.assertEqual("failed", diff_size_guard["status"])
        self.assertIn("6 changed files", diff_size_guard["message"])
        self.assertIn("501 diff lines", diff_size_guard["message"])

    def test_forbidden_file_guard_rejects_ci_workflow_changes(self) -> None:
        report = validation.build_validation_report(
            {
                "branch": "feature/ralph-validation",
                "default_branch": "main",
                "changed_files": [".github/workflows/android-ci.yml"],
                "dirty_paths": [],
                "diff_lines": 12,
                "commits_ahead": 0,
                "reviewer_result": {"status": "passed", "model": "gpt5.4-codex-mini"},
                "require_remote_green": False,
            }
        )

        forbidden = self._gate(report, "forbidden_file_guard")
        self.assertEqual("failed", forbidden["status"])
        self.assertIn("forbidden", forbidden["message"].lower())

    def test_dirty_work_guard_rejects_unrelated_dirty_paths(self) -> None:
        report = validation.build_validation_report(
            {
                "branch": "feature/docs-cleanup",
                "default_branch": "main",
                "changed_files": ["docs/ci-sonar-reliability-runbook.md"],
                "focus_paths": ["docs/ci-sonar-reliability-runbook.md"],
                "dirty_paths": [
                    "docs/ci-sonar-reliability-runbook.md",
                    "notes/tmp.txt",
                ],
                "diff_lines": 12,
                "commits_ahead": 0,
                "reviewer_result": {"status": "passed", "model": "gpt5.4-codex-mini"},
                "require_remote_green": False,
            }
        )

        dirty_guard = self._gate(report, "dirty_work_guard")
        self.assertEqual("failed", dirty_guard["status"])
        self.assertIn("unrelated dirty work", dirty_guard["message"].lower())
        self.assertIn("notes/tmp.txt", dirty_guard["message"])

    def test_button_contract_delta_guard_rejects_touched_interactive_file_without_click_coverage(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            home_file, manifest, contract = self._build_home_ui_fixture(root, with_click_test=False)

            report = validation.build_validation_report(
                {
                    "branch": "feature/ralph-validation",
                    "default_branch": "main",
                    "changed_files": [home_file],
                    "dirty_paths": [],
                    "diff_lines": 34,
                    "commits_ahead": 0,
                    "manifest": manifest,
                    "button_contract": contract,
                    "targeted_compose_tests": [
                        {
                            "command": "./gradlew :app:testDebugUnitTest --tests dev.bee.kanjianki.HomeScreenComposeTest",
                            "status": "passed",
                        }
                    ],
                    "ci_fast_result": {"status": "passed"},
                    "ci_quality_result": {"status": "passed"},
                    "screenshot_result": {"status": "passed"},
                    "design_review": self._passing_design_comparison(),
                    "button_review": {
                        "status": "passed",
                        "passed": True,
                        "missing_contract_rows": [],
                        "missing_click_tests": [],
                        "missing_disabled_state_tests": [],
                        "a11y_gaps": [],
                    },
                    "reviewer_result": {"status": "passed", "model": "gpt5.4-codex-mini"},
                    "require_remote_green": False,
                }
            )

        delta_guard = self._gate(report, "button_contract_delta_guard")
        self.assertEqual("failed", delta_guard["status"])
        self.assertTrue(
            any("missing direct selector/click coverage" in message for message in delta_guard["details"]["missing_tests"]),
            delta_guard,
        )

    def test_emulator_dependent_gates_report_needs_host_when_screenshot_and_reviews_are_missing(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            home_file, manifest, contract = self._build_home_ui_fixture(root, with_click_test=True)

            report = validation.build_validation_report(
                {
                    "branch": "feature/ralph-validation",
                    "default_branch": "main",
                    "changed_files": [home_file],
                    "dirty_paths": [],
                    "diff_lines": 42,
                    "commits_ahead": 0,
                    "manifest": manifest,
                    "button_contract": contract,
                    "targeted_compose_tests": [
                        {
                            "command": "./gradlew :app:testDebugUnitTest --tests dev.bee.kanjianki.HomeScreenComposeTest",
                            "status": "passed",
                        }
                    ],
                    "ci_fast_result": {"status": "passed"},
                    "ci_quality_result": {"status": "passed"},
                    "reviewer_result": {"status": "passed", "model": "gpt5.4-codex-mini"},
                    "require_remote_green": False,
                }
            )

        screenshot_gate = self._gate(report, "screenshot_availability")
        design_gate = self._gate(report, "design_comparison")
        button_qa_gate = self._gate(report, "button_qa_review")
        self.assertIn(screenshot_gate["status"], {"needs_host", "pending"})
        self.assertIn(design_gate["status"], {"needs_host", "pending"})
        self.assertIn(button_qa_gate["status"], {"needs_host", "pending"})
        self.assertNotEqual("passed", report["status"])

    def test_remote_ci_and_sonar_are_required_when_flag_is_set(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            home_file, manifest, contract = self._build_home_ui_fixture(root, with_click_test=True)

            report = validation.build_validation_report(
                {
                    "branch": "feature/ralph-validation",
                    "default_branch": "main",
                    "changed_files": [home_file],
                    "dirty_paths": [],
                    "diff_lines": 42,
                    "commits_ahead": 0,
                    "manifest": manifest,
                    "button_contract": contract,
                    "targeted_compose_tests": [
                        {
                            "command": "./gradlew :app:testDebugUnitTest --tests dev.bee.kanjianki.HomeScreenComposeTest",
                            "status": "passed",
                        }
                    ],
                    "ci_fast_result": {"status": "passed"},
                    "ci_quality_result": {"status": "passed"},
                    "screenshot_result": {"status": "passed"},
                    "design_review": self._passing_design_comparison(),
                    "button_review": {
                        "status": "passed",
                        "passed": True,
                        "missing_contract_rows": [],
                        "missing_click_tests": [],
                        "missing_disabled_state_tests": [],
                        "a11y_gaps": [],
                    },
                    "reviewer_result": {"status": "passed", "model": "gpt5.4-codex-mini"},
                    "require_remote_green": True,
                }
            )

        remote_gate = self._gate(report, "remote_ci_sonar_gate")
        self.assertIn(remote_gate["status"], {"needs_host", "pending"})
        self.assertNotEqual("passed", report["status"])

    def test_cli_writes_validation_json(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            state = {
                "branch": "feature/cli-smoke",
                "default_branch": "main",
                "changed_files": ["docs/notes.md"],
                "dirty_paths": [],
                "diff_lines": 1,
                "commits_ahead": 0,
                "reviewer_result": {"status": "passed", "model": "gpt5.4-codex-mini"},
            }
            state_path = root / "state.json"
            out_path = root / "validation.json"
            state_path.write_text(json.dumps(state), encoding="utf-8")

            exit_code = validation.main([
                "--repo-root", str(root),
                "--run-dir", str(root),
                "--state-json", str(state_path),
                "--out", str(out_path),
            ])

            self.assertEqual(0, exit_code)
            self.assertTrue(out_path.exists())
            loaded = json.loads(out_path.read_text(encoding="utf-8"))
            self.assertEqual("ralph-validation-v1", loaded["schema"])
            self.assertEqual("passed", loaded["status"])

    def test_docs_only_changes_skip_sonar_and_ui_validation_gates(self) -> None:
        report = validation.build_validation_report(
            {
                "branch": "feature/docs-cleanup",
                "default_branch": "main",
                "changed_files": ["docs/ci-sonar-reliability-runbook.md"],
                "dirty_paths": [],
                "diff_lines": 12,
                "commits_ahead": 0,
                "reviewer_result": {"status": "passed", "model": "gpt5.4-codex-mini"},
                "require_remote_green": False,
            }
        )

        self.assertEqual("passed", report["status"])
        self.assertEqual("skipped", self._gate(report, "ci_quality_gate")["status"])
        self.assertEqual("skipped", self._gate(report, "screenshot_availability")["status"])
        self.assertEqual("skipped", self._gate(report, "button_contract_delta_guard")["status"])

    def test_design_comparison_requires_after_better_and_score_delta(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            home_file, manifest, contract = self._build_home_ui_fixture(root, with_click_test=True)
            base_state: dict[str, object] = {
                "branch": "feature/ralph-validation",
                "default_branch": "main",
                "changed_files": [home_file],
                "dirty_paths": [],
                "diff_lines": 42,
                "commits_ahead": 0,
                "manifest": manifest,
                "button_contract": contract,
                "targeted_compose_tests": [{"command": "./gradlew :app:testDebugUnitTest", "status": "passed"}],
                "ci_fast_result": {"status": "passed"},
                "ci_quality_result": {"status": "passed"},
                "screenshot_result": {"status": "passed", "run_id": 77},
                "button_review": {
                    "status": "passed",
                    "passed": True,
                    "missing_contract_rows": [],
                    "missing_click_tests": [],
                    "missing_disabled_state_tests": [],
                    "a11y_gaps": [],
                },
                "reviewer_result": {"status": "passed", "model": "gpt5.4-codex-mini"},
                "require_remote_green": False,
            }

            missing_report = validation.build_validation_report(
                {**base_state, "design_review": {"status": "passed", "passed": True, "accepted_issues": []}}
            )
            low_delta = self._passing_design_comparison()
            low_delta["score_delta"] = 0.04
            low_delta["score_after"] = 0.66
            low_delta_report = validation.build_validation_report(
                {**base_state, "design_review": low_delta, "min_design_score_delta": 0.10}
            )
            not_better = self._passing_design_comparison()
            not_better["after_better"] = False
            not_better_report = validation.build_validation_report({**base_state, "design_review": not_better})
            inconsistent_delta = self._passing_design_comparison()
            inconsistent_delta["score_before"] = 0.95
            inconsistent_delta["score_after"] = 0.10
            inconsistent_delta["score_delta"] = 0.14
            inconsistent_delta_report = validation.build_validation_report(
                {**base_state, "design_review": inconsistent_delta, "min_design_score_delta": 0.10}
            )
            non_finite_delta = self._passing_design_comparison()
            non_finite_delta["score_delta"] = "nan"
            non_finite_delta_report = validation.build_validation_report({**base_state, "design_review": non_finite_delta})
            out_of_range_scores = self._passing_design_comparison()
            out_of_range_scores["score_before"] = 1.2
            out_of_range_scores["score_after"] = 2.4
            out_of_range_scores["score_delta"] = 1.2
            out_of_range_scores_report = validation.build_validation_report(
                {**base_state, "design_review": out_of_range_scores, "min_design_score_delta": 0.10}
            )
            alias_report = validation.build_validation_report(
                {**base_state, "profile_reviews": {"design_comparison": self._passing_design_comparison()}}
            )

        missing_gate = self._gate(missing_report, "design_comparison")
        low_delta_gate = self._gate(low_delta_report, "design_comparison")
        not_better_gate = self._gate(not_better_report, "design_comparison")
        inconsistent_delta_gate = self._gate(inconsistent_delta_report, "design_comparison")
        non_finite_delta_gate = self._gate(non_finite_delta_report, "design_comparison")
        out_of_range_scores_gate = self._gate(out_of_range_scores_report, "design_comparison")
        alias_gate = self._gate(alias_report, "design_comparison")
        self.assertEqual("passed", alias_gate["status"])
        self.assertEqual("failed", missing_gate["status"])
        self.assertIn("after-better", str(missing_gate["message"]))
        self.assertEqual("failed", low_delta_gate["status"])
        self.assertIn("score delta", str(low_delta_gate["message"]))
        self.assertEqual("failed", not_better_gate["status"])
        self.assertIn("after screenshot is better", str(not_better_gate["message"]))
        self.assertEqual("failed", inconsistent_delta_gate["status"])
        self.assertIn("score_after - score_before", str(inconsistent_delta_gate["message"]))
        self.assertEqual("failed", non_finite_delta_gate["status"])
        self.assertIn("score_delta finite number", str(non_finite_delta_gate["details"]))
        self.assertEqual("failed", out_of_range_scores_gate["status"])
        self.assertIn("score_before finite 0.0..1.0 score", str(out_of_range_scores_gate["details"]))
        self.assertIn("score_after finite 0.0..1.0 score", str(out_of_range_scores_gate["details"]))

    def test_cli_accepts_min_design_score_delta(self) -> None:
        parser = validation.build_parser()
        args = parser.parse_args(["--min-design-score-delta", "0.25"])
        self.assertEqual(0.25, args.min_design_score_delta)
        for invalid_value in ("-0.1", "nan", "inf", "-inf"):
            with self.subTest(invalid_value=invalid_value):
                with self.assertRaises(SystemExit):
                    parser.parse_args([f"--min-design-score-delta={invalid_value}"])

    def _passing_design_comparison(self) -> dict[str, object]:
        return {
            "schema": "cheap-ralph-design-comparison-v1",
            "status": "passed",
            "passed": True,
            "after_better": True,
            "score_before": 0.62,
            "score_after": 0.76,
            "score_delta": 0.14,
            "issue_resolved": True,
            "new_regressions": [],
            "learning_correctness_risk": False,
            "rationale": "After screenshot gives the primary action clearer hierarchy without changing study behavior.",
        }

    def _build_home_ui_fixture(self, root: Path, *, with_click_test: bool) -> tuple[str, dict[str, object], dict[str, object]]:
        home_file = root / "app/src/main/kotlin/dev/bee/kanjianki/MainActivityHome.kt"
        home_file.parent.mkdir(parents=True, exist_ok=True)
        home_file.write_text(
            """
            package dev.bee.kanjianki

            import androidx.compose.runtime.Composable

            @Composable
            fun HomeScreen(model: HomeScreenModel) {
                Button(onClick = model.onStudy) {
                    Text("Study now")
                }
            }
            """.strip()
            + "\n",
            encoding="utf-8",
        )

        test_file = root / "app/src/androidTest/kotlin/dev/bee/kanjianki/HomeScreenComposeTest.kt"
        test_file.parent.mkdir(parents=True, exist_ok=True)
        if with_click_test:
            test_file.write_text(
                """
                package dev.bee.kanjianki

                class HomeScreenComposeTest {
                    fun study_button_clicks() {
                        compose.onNodeWithText("Study now").performClick()
                    }
                }
                """.strip()
                + "\n",
                encoding="utf-8",
            )
        else:
            test_file.write_text(
                """
                package dev.bee.kanjianki

                class HomeScreenComposeTest {
                    fun unrelated_assertion() {
                        assert(true)
                    }
                }
                """.strip()
                + "\n",
                encoding="utf-8",
            )

        manifest = ui_manifest.build_manifest(root)
        manifest_path = root / ".ralph-loop/current/ui-manifest.json"
        manifest_path.parent.mkdir(parents=True, exist_ok=True)
        manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True), encoding="utf-8")

        contract = button_contract.build_contract(root, manifest_path)
        contract_path = root / ".ralph-loop/current/button-contract.json"
        contract_path.write_text(json.dumps(contract, indent=2, sort_keys=True), encoding="utf-8")

        return home_file.relative_to(root).as_posix(), manifest, contract

    def _gate(self, report: dict[str, object], gate_id: str) -> dict[str, object]:
        return next(gate for gate in report["gates"] if gate["id"] == gate_id)


if __name__ == "__main__":
    unittest.main()
