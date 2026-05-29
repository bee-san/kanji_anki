#!/usr/bin/env python3

from __future__ import annotations

import shutil
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from io import StringIO
from pathlib import Path
from unittest.mock import patch

from scripts.ralph_loop import orchestrator


class RalphOrchestratorTest(unittest.TestCase):
    def test_remote_screenshot_flags_call_github_renderer_without_mutating_checkout(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            project_root = Path(__file__).resolve().parents[2]
            shutil.copytree(project_root / "scripts" / "prompts", root / "scripts" / "prompts")
            with patch.object(orchestrator.github_screenshots, "run_remote_screenshots") as remote, patch.object(
                orchestrator, "_run_profile_command"
            ) as profile_command:
                manifest_path = root / "manifest.json"
                manifest_path.write_text('{"schema":"ui-manifest-v1","files":[]}', encoding="utf-8")
                remote.return_value = {"status": "passed", "run_id": 77, "manifest": str(manifest_path), "pngs": [str(root / "home.png")]}
                profile_command.return_value = {"status": "passed"}

                with redirect_stdout(StringIO()):
                    exit_code = orchestrator.main(
                        [
                            "--repo-root",
                            str(root),
                            "--audit-only",
                            "--remote-screenshot-workflow",
                            "android-screenshots.yml",
                            "--screenshot-artifact",
                            "android-screenshots",
                            "--screenshot-route",
                            "home",
                            "--require-remote-screenshots",
                            "--max-iterations",
                            "1",
                        ]
                    )

            self.assertEqual(0, exit_code)
            remote.assert_called_once()
            kwargs = remote.call_args.kwargs
            self.assertEqual(root.resolve(), kwargs["repo_root"])
            self.assertEqual("android-screenshots.yml", kwargs["workflow"])
            self.assertEqual("android-screenshots", kwargs["artifact"])
            self.assertEqual("home", kwargs["screenshot_route"])
            self.assertFalse(kwargs["push_pr_branch"])
            self.assertTrue(kwargs["require_remote_screenshots"])
            self.assertEqual(2, profile_command.call_count)
            design_prompt = profile_command.call_args_list[0].args[1]
            button_prompt = profile_command.call_args_list[1].args[1]
            self.assertIn("Ralph's independent Kani design critic", design_prompt)
            self.assertIn('"schema":"ui-manifest-v1"', design_prompt)
            self.assertIn("Ralph's Kani button-contract reviewer", button_prompt)
            self.assertIn('"schema": "button-contract-v1"', button_prompt)

    def test_pending_remote_visual_status_is_not_accepted_at_top_level(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            with patch.object(orchestrator.github_screenshots, "run_remote_screenshots") as remote, patch.object(
                orchestrator, "_run_profile_command"
            ) as profile_command:
                remote.return_value = {"status": "remote_visual_pending", "message": "workflow is unavailable"}

                output = StringIO()
                with redirect_stdout(output):
                    exit_code = orchestrator.main(
                        [
                            "--repo-root",
                            str(root),
                            "--audit-only",
                            "--max-iterations",
                            "1",
                        ]
                    )

            self.assertEqual(1, exit_code)
            self.assertIn('"status": "remote_visual_pending"', output.getvalue())
            self.assertIn('"remote_visual_pending"', output.getvalue())
            profile_command.assert_not_called()

    def test_iterations_cap_is_required_and_hard_limited(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            with redirect_stderr(StringIO()):
                with self.assertRaises(SystemExit):
                    orchestrator.build_parser().parse_args(["--repo-root", str(root), "--iterations", "0"])
                with self.assertRaises(SystemExit):
                    orchestrator.build_parser().parse_args(["--repo-root", str(root), "--max-iterations", "0"])
                with self.assertRaises(SystemExit):
                    orchestrator.build_parser().parse_args(["--repo-root", str(root), "--iterations", "2", "--max-iterations", "1"])


if __name__ == "__main__":
    unittest.main()
