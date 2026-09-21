"""Tests for the installed-image data-retention gate.

Every case drives `verify_data_retention` with a fake process runner standing in
for the installed image, because the point under test is what the *gate*
accepts, not what the real image does. A gate that passes an image which wiped
the profile is worse than no gate: it reports the retention promise as kept.
"""

from __future__ import annotations

import os
import shutil
import subprocess
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from tools.run_desktop_data_retention import (
    DesktopDataRetentionError,
    RETAINED_CONTENT,
    RETAINED_RELATIVE_PATH,
    verify_data_retention,
)

PINNED_MARKER = "KANI_DESKTOP_SMOKE_READY temporary_data=false\n"
THROWAWAY_MARKER = "KANI_DESKTOP_SMOKE_READY temporary_data=true\n"


def pinned_profile(command):
    return next(
        argument.split("=", 1)[1]
        for argument in command
        if argument.startswith("--data-root=")
    )


def write_marker(environment, marker=PINNED_MARKER):
    Path(environment["KANI_DESKTOP_SMOKE_RESULT_FILE"]).write_text(
        marker,
        encoding="utf-8",
    )
    return marker


class RunDesktopDataRetentionTest(unittest.TestCase):
    def setUp(self):
        self._image = TemporaryDirectory(prefix="kani-retention-image-")
        self.addCleanup(self._image.cleanup)
        image_root = Path(self._image.name)
        launcher = image_root / "Kani" / "bin" / "Kani"
        launcher.parent.mkdir(parents=True)
        launcher.write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
        launcher.chmod(0o755)
        self.image_root = image_root
        # `verify_render_environment` requires DISPLAY on Linux, and these tests
        # never render: the fake runner replaces the process entirely.
        self.environment = dict(os.environ) | {"DISPLAY": ":99"}

    def run_gate(self, runner):
        verify_data_retention(
            self.image_root,
            platform="linux",
            base_environment=self.environment,
            process_runner=runner,
        )

    def test_accepts_an_image_that_leaves_the_profile_alone(self):
        launches = []

        def honest(command, **kwargs):
            launches.append(pinned_profile(command))
            return subprocess.CompletedProcess(
                command,
                0,
                write_marker(kwargs["env"]),
                "",
            )

        self.run_gate(honest)

        # Two launches, and both against the *same* profile — a gate that gave
        # each run its own root would prove nothing about retention.
        self.assertEqual(2, len(launches))
        self.assertEqual(launches[0], launches[1])

    def test_rejects_an_image_that_deletes_the_pinned_profile(self):
        def wipes(command, **kwargs):
            profile = pinned_profile(command)
            shutil.rmtree(profile, ignore_errors=True)
            os.makedirs(profile, exist_ok=True)
            return subprocess.CompletedProcess(
                command,
                0,
                write_marker(kwargs["env"]),
                "",
            )

        with self.assertRaises(DesktopDataRetentionError) as raised:
            self.run_gate(wipes)
        self.assertIn("did not survive", str(raised.exception))

    def test_rejects_an_image_that_rewrites_retained_data(self):
        def truncates(command, **kwargs):
            retained = Path(pinned_profile(command)) / RETAINED_RELATIVE_PATH
            if retained.exists():
                retained.write_text("truncated", encoding="utf-8")
            return subprocess.CompletedProcess(
                command,
                0,
                write_marker(kwargs["env"]),
                "",
            )

        # Same filename, different bytes. Checking only for the file's existence
        # would call this retention.
        with self.assertRaises(DesktopDataRetentionError) as raised:
            self.run_gate(truncates)
        self.assertIn("was modified", str(raised.exception))

    def test_rejects_a_run_that_reports_a_throwaway_profile(self):
        def throwaway(command, **kwargs):
            return subprocess.CompletedProcess(
                command,
                0,
                write_marker(kwargs["env"], THROWAWAY_MARKER),
                "",
            )

        # `temporary_data=true` means the app deleted the root it was given, so
        # any surviving file would be an accident rather than the contract.
        with self.assertRaises(DesktopDataRetentionError) as raised:
            self.run_gate(throwaway)
        self.assertIn("pinned-profile readiness marker", str(raised.exception))

    def test_rejects_a_failed_launch(self):
        def crashes(command, **kwargs):
            write_marker(kwargs["env"])
            return subprocess.CompletedProcess(
                command,
                1,
                "",
                "skiko failed to initialize",
            )

        with self.assertRaises(DesktopDataRetentionError) as raised:
            self.run_gate(crashes)
        self.assertIn("exited 1", str(raised.exception))

    def test_rejects_a_launch_that_writes_no_result_file(self):
        def silent(command, **kwargs):
            return subprocess.CompletedProcess(command, 0, PINNED_MARKER, "")

        # Stdout alone is not enough: it can be produced by anything wrapping
        # the launcher, whereas the result file is written by the app itself.
        with self.assertRaises(DesktopDataRetentionError) as raised:
            self.run_gate(silent)
        self.assertIn("no readiness result file", str(raised.exception))

    def test_rejects_a_launch_that_times_out(self):
        def hangs(command, **kwargs):
            raise subprocess.TimeoutExpired(command, kwargs["timeout"])

        with self.assertRaises(DesktopDataRetentionError) as raised:
            self.run_gate(hangs)
        self.assertIn("timeout", str(raised.exception))

    def test_plants_user_data_only_after_the_first_launch(self):
        observed = []

        def records(command, **kwargs):
            retained = Path(pinned_profile(command)) / RETAINED_RELATIVE_PATH
            observed.append(retained.is_file())
            return subprocess.CompletedProcess(
                command,
                0,
                write_marker(kwargs["env"]),
                "",
            )

        self.run_gate(records)

        # The first run must not see the file, or the file could be something
        # that run wrote rather than data it inherited.
        self.assertEqual([False, True], observed)

    def test_requires_a_launcher_in_the_image(self):
        with TemporaryDirectory(prefix="kani-retention-empty-") as empty:
            with self.assertRaises(RuntimeError):
                verify_data_retention(
                    Path(empty),
                    platform="linux",
                    base_environment=self.environment,
                    process_runner=lambda *a, **k: self.fail(
                        "a missing launcher must not be executed",
                    ),
                )

    def test_retained_content_ends_with_a_newline(self):
        # Written and compared as text, so a trailing-newline mismatch would be
        # a spurious failure rather than a real retention bug.
        self.assertTrue(RETAINED_CONTENT.endswith("\n"))


if __name__ == "__main__":
    unittest.main()
