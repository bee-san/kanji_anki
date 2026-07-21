#!/usr/bin/env python3

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from scripts.ralph_loop import prompts


class RalphPromptsTest(unittest.TestCase):
    def test_required_project_prompts_load_and_render_with_parseable_json_contracts(self) -> None:
        root = Path(__file__).resolve().parents[2]
        required = {
            "ralph_design_file_auditor.md": {
                "file",
                "components",
                "visual_strengths",
                "visual_problems",
                "interaction_a11y_problems",
                "one_best_fix",
                "do_not_touch",
            },
            "ralph_button_contract_reviewer.md": {
                "passed",
                "missing_contract_rows",
                "missing_click_tests",
                "missing_disabled_state_tests",
                "a11y_gaps",
                "highest_priority_fix",
            },
            "ralph_design_critic.md": {
                "schema",
                "passed",
                "view_id",
                "before_screenshot_sha256",
                "score_before",
                "accepted_issue",
                "target_view_spec",
                "target_screenshot",
                "target_screenshot_unavailable_reason",
                "rejected_issues",
                "do_not_touch",
            },
            "ralph_design_comparison.md": {
                "schema",
                "passed",
                "after_better",
                "score_before",
                "score_after",
                "score_delta",
                "issue_resolved",
                "new_regressions",
                "learning_correctness_risk",
                "rationale",
            },
            "ralph_ui_implementer.md": {
                "schema",
                "passed",
                "accepted_issue",
                "target_view_spec",
                "changed_files",
                "tests_first",
                "tests_run",
                "patch_path",
                "after_screenshot_should_improve",
                "blocked_reason",
            },
        }

        for prompt_name, expected_keys in required.items():
            with self.subTest(prompt=prompt_name):
                prompt = prompts.load_project_prompt(root, prompt_name)
                self.assertEqual(prompt_name, prompt.name)
                self.assertTrue(prompt.template.strip())
                self.assertTrue(expected_keys.issubset(set(prompt.output_schema)))
                context = {
                    "file": "app/src/main/java/dev/bee/kanjianki/MainActivity.kt",
                    "repo_root": "/repo/root",
                    "scratch_checkout": "/tmp/cheap-ralph-scratch",
                    "run_dir": "/tmp/cheap-ralph-run",
                    "patch_path": "/tmp/cheap-ralph-run/dry-run/implementation/candidate.patch",
                    "max_changed_files": "5",
                    "max_diff_lines": "500",
                    "manifest_json": '{"schema":"ui-manifest-v1","files":[]}',
                    "view_matrix_json": '{"schema":"ui-view-matrix-v1","views":[]}',
                    "button_contract_json": '{"schema":"button-contract-v1","rows":[]}',
                    "screenshots_json": '{"schema":"ui-screenshots-v1","files":[]}',
                    "required_tests_json": '["./gradlew testDebugUnitTest"]',
                    "forbidden_paths_json": '[".github/workflows/**"]',
                    "target_view_spec_json": '{"summary":"Main screen","hierarchy":["root"],"copy_changes":["Tighten the CTA"],"spacing_touch_targets":["Keep the button within reach"],"accessibility":["Preserve labels"],"material_expectations":["Use existing Material styles"]}',
                    "accepted_issue_json": '{"id":"home-primary-cta","file":"MainActivity.kt"}',
                }
                rendered = prompt.render(**{key: context[key] for key in prompt.placeholders})
                self.assertIn("Return JSON only", rendered)
                self.assertNotIn("{{", rendered)
                self.assertNotIn("}}", rendered)

    def test_render_rejects_unknown_or_missing_placeholders(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            prompt_path = Path(temp) / "prompt.md"
            prompt_path.write_text(
                "---\n"
                "output_schema_json: '{\"answer\": \"string\"}'\n"
                "---\n"
                "Hello {{name}}",
                encoding="utf-8",
            )
            prompt = prompts.load_prompt(prompt_path)
            self.assertEqual({"answer": "string"}, prompt.output_schema)
            self.assertEqual("Hello Kani", prompt.render(name="Kani"))
            with self.assertRaises(KeyError):
                prompt.render(other="Kani")
            with self.assertRaises(ValueError):
                prompts.ProjectPrompt("bad.md", "Hello {{unknown}}", {}).render(unknown="x")


if __name__ == "__main__":
    unittest.main()
