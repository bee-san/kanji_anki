#!/usr/bin/env python3

from __future__ import annotations

import json
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
                "passed",
                "accepted_issues",
                "rejected_issues",
                "highest_priority_issue",
                "do_not_touch",
            },
            "ralph_ui_implementer.md": {
                "accepted_issue",
                "changed_files",
                "tests_first",
                "tests_run",
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
                    "manifest_json": '{"schema":"ui-manifest-v1","files":[]}',
                    "button_contract_json": '{"schema":"button-contract-v1","rows":[]}',
                    "screenshots_json": '{"pngs":[]}',
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
