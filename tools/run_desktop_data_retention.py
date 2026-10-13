#!/usr/bin/env python3
"""Verify an installed desktop image retains user data across a second launch.

Goal 204 requires install/upgrade/uninstall journeys that keep user data. The
part that is worth automating is the invariant underneath all three: the
application's profile is *outside* the installed image, so replacing the image
cannot disturb it.

This gate proves that directly rather than through a package manager. It runs
the installed image once against a pinned profile, plants a file in that
profile, runs the image again over the same profile, and asserts the file
survived with its bytes intact. An upgrade is exactly this sequence with a
different image in between, and an uninstall is its first half — so a failure
here is a failure of all three, findable without `dpkg-deb`, WiX, or Xcode.

What it deliberately does not claim: that a real `.deb` upgrade preserves data.
Package scripts can delete directories this gate never touches. That check
belongs on a host with the packaging tooling, against the built package; this
one pins the application-side half of the contract, which is the half Kani's
own code controls.
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
import tempfile
from collections.abc import Callable, Mapping, Sequence
from pathlib import Path

from tools.run_desktop_installed_image_smoke import (
    installed_image_launcher,
    isolated_temporary_environment,
    normalized_platform,
    verify_render_environment,
)

SMOKE_RESULT_FILE_ENVIRONMENT_VARIABLE = "KANI_DESKTOP_SMOKE_RESULT_FILE"
SMOKE_RESULT_FILENAME = "smoke-result.txt"

# The pinned-profile marker, which is *not* the throwaway one. A run that
# reported `temporary_data=true` deleted its profile, so asking whether data
# survived it would be meaningless — this gate must only accept a run that
# promised to keep the root it was given.
PINNED_READY_MARKER = "KANI_DESKTOP_SMOKE_READY temporary_data=false"

# Bytes rather than a marker string: retention means the same content, not
# merely a file of the same name. A truncating rewrite would pass a name check.
RETAINED_RELATIVE_PATH = "retained/user-data.txt"
RETAINED_CONTENT = "kani desktop retained user data\n"

DEFAULT_TIMEOUT_SECONDS = 300


class DesktopDataRetentionError(RuntimeError):
    """Raised when an installed image fails the data-retention contract."""


def launch_against_pinned_profile(
    launcher: Path,
    profile_root: Path,
    *,
    label: str,
    base_environment: Mapping[str, str],
    timeout_seconds: int,
    process_runner: Callable[..., subprocess.CompletedProcess[str]],
) -> None:
    """Runs the image once in smoke mode against [profile_root], keeping it."""
    with tempfile.TemporaryDirectory(
        prefix="kani-desktop-retention-run-",
    ) as temporary:
        isolated_root = Path(temporary)
        result_file = isolated_root / SMOKE_RESULT_FILENAME
        environment = isolated_temporary_environment(
            isolated_root,
            base_environment,
        )
        environment[SMOKE_RESULT_FILE_ENVIRONMENT_VARIABLE] = str(result_file)
        command: Sequence[str] = (
            str(launcher),
            "--smoke-test",
            f"--data-root={profile_root}",
        )
        try:
            result = process_runner(
                command,
                cwd=launcher.parent,
                env=environment,
                capture_output=True,
                text=True,
                check=False,
                timeout=timeout_seconds,
                stdin=subprocess.DEVNULL,
            )
        except subprocess.TimeoutExpired as error:
            raise DesktopDataRetentionError(
                f"{label} launch exceeded the {timeout_seconds}-second timeout",
            ) from error

        if result.returncode != 0:
            raise DesktopDataRetentionError(
                f"{label} launch exited {result.returncode}: {result.stderr.strip()}",
            )
        if PINNED_READY_MARKER not in result.stdout:
            raise DesktopDataRetentionError(
                f"{label} launch did not report a pinned-profile readiness marker; "
                f"stdout was {result.stdout.strip()!r}",
            )
        if not result_file.is_file():
            raise DesktopDataRetentionError(
                f"{label} launch wrote no readiness result file",
            )
        # The profile must still exist after the run. A pinned root that the
        # app deleted anyway is the exact regression this gate exists to catch,
        # and it would otherwise surface as a confusing "file missing" below.
        if not profile_root.is_dir():
            raise DesktopDataRetentionError(
                f"{label} launch deleted the pinned profile at {profile_root}",
            )


def verify_data_retention(
    image_root: Path,
    *,
    platform: str = sys.platform,
    timeout_seconds: int = DEFAULT_TIMEOUT_SECONDS,
    base_environment: Mapping[str, str] = os.environ,
    process_runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run,
) -> None:
    launcher = installed_image_launcher(image_root, platform)
    verify_render_environment(platform, base_environment)
    host = normalized_platform(platform)

    with tempfile.TemporaryDirectory(
        prefix="kani-desktop-retention-profile-",
    ) as profile:
        profile_root = Path(profile)

        # First run: a fresh profile, as a first install produces.
        launch_against_pinned_profile(
            launcher,
            profile_root,
            label=f"{host} first",
            base_environment=base_environment,
            timeout_seconds=timeout_seconds,
            process_runner=process_runner,
        )

        # Plant user data *after* the first run, so it is data the second run
        # inherits rather than data the first run could have written itself.
        retained = profile_root / RETAINED_RELATIVE_PATH
        retained.parent.mkdir(parents=True, exist_ok=True)
        retained.write_text(RETAINED_CONTENT, encoding="utf-8")

        # Second run over the same profile. With a different image this is an
        # upgrade; with the same image it still proves the profile is external
        # to the installation and survives being opened again.
        launch_against_pinned_profile(
            launcher,
            profile_root,
            label=f"{host} second",
            base_environment=base_environment,
            timeout_seconds=timeout_seconds,
            process_runner=process_runner,
        )

        if not retained.is_file():
            raise DesktopDataRetentionError(
                f"user data did not survive the second launch: {retained}",
            )
        actual = retained.read_text(encoding="utf-8")
        if actual != RETAINED_CONTENT:
            raise DesktopDataRetentionError(
                "user data was modified across the second launch: "
                f"expected {RETAINED_CONTENT!r}, found {actual!r}",
            )


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--image-root",
        required=True,
        type=Path,
        help="The installed desktop application image directory.",
    )
    parser.add_argument(
        "--timeout-seconds",
        default=DEFAULT_TIMEOUT_SECONDS,
        type=int,
        help="Per-launch timeout.",
    )
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    arguments = parse_args(argv)
    try:
        verify_data_retention(
            arguments.image_root,
            timeout_seconds=arguments.timeout_seconds,
        )
    except (DesktopDataRetentionError, RuntimeError) as error:
        print(f"desktop data retention failed: {error}", file=sys.stderr)
        return 1
    print("KANI_DESKTOP_DATA_RETAINED launches=2")
    return 0


if __name__ == "__main__":
    sys.exit(main())
