#!/usr/bin/env python3

from __future__ import annotations

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
            with patch.object(orchestrator.github_screenshots, "run_remote_screenshots") as remote, patch.object(
                orchestrator, "_run_profile_command"
            ) as profile_command:
                remote.return_value = {"status": "passed", "run_id": 77, "manifest": str(root / "manifest.json"), "pngs": [str(root / "home.png")]}
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
            self.assertEqual(root, kwargs["repo_root"])
            self.assertEqual("android-screenshots.yml", kwargs["workflow"])
            self.assertEqual("android-screenshots", kwargs["artifact"])
            self.assertEqual("home", kwargs["screenshot_route"])
            self.assertFalse(kwargs["push_pr_branch"])
            self.assertTrue(kwargs["require_remote_screenshots"])
            self.assertEqual(2, profile_command.call_count)

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
