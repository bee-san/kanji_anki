#!/usr/bin/env python3
"""Run Kani's current-host installed application image under its smoke contract."""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
import tempfile
from collections.abc import Callable, Mapping, Sequence
from pathlib import Path


APPLICATION_NAME = "Kani"
SMOKE_ARGUMENTS = ("--smoke-test", "--temporary-data")
SMOKE_READY_MARKER = "KANI_DESKTOP_SMOKE_READY temporary_data=true"
SMOKE_RESULT_FILE_ENVIRONMENT_VARIABLE = "KANI_DESKTOP_SMOKE_RESULT_FILE"
SMOKE_RESULT_FILENAME = "smoke-ready"
SMOKE_TEMPORARY_PREFIX = "kani-desktop-smoke-"
DEFAULT_TIMEOUT_SECONDS = 120


class DesktopInstalledImageSmokeError(RuntimeError):
    """Raised when the installed desktop image violates its smoke contract."""


def normalized_platform(platform: str) -> str:
    if platform == "linux" or platform.startswith("linux"):
        return "linux"
    if platform in {"darwin", "macos"}:
        return "macos"
    if platform in {"win32", "cygwin", "msys", "windows"}:
        return "windows"
    raise DesktopInstalledImageSmokeError(
        f"unsupported desktop smoke platform: {platform}",
    )


def installed_image_launcher(image_root: Path, platform: str) -> Path:
    host = normalized_platform(platform)
    relative_launcher = {
        "linux": Path(APPLICATION_NAME, "bin", APPLICATION_NAME),
        "macos": Path(
            f"{APPLICATION_NAME}.app",
            "Contents",
            "MacOS",
            APPLICATION_NAME,
        ),
        "windows": Path(APPLICATION_NAME, f"{APPLICATION_NAME}.exe"),
    }[host]
    launcher = image_root.resolve() / relative_launcher
    if not launcher.is_file():
        raise DesktopInstalledImageSmokeError(
            f"installed-image launcher is missing for {host}: {launcher}",
        )
    if host != "windows" and not os.access(launcher, os.X_OK):
        raise DesktopInstalledImageSmokeError(
            f"installed-image launcher is not executable: {launcher}",
        )
    return launcher


def isolated_temporary_environment(
    temporary_root: Path,
    base_environment: Mapping[str, str],
) -> dict[str, str]:
    environment = dict(base_environment)
    temporary_path = str(temporary_root)
    environment.update(
        {
            "TMPDIR": temporary_path,
            "TEMP": temporary_path,
            "TMP": temporary_path,
        },
    )
    return environment


def verify_render_environment(
    platform: str,
    environment: Mapping[str, str],
) -> None:
    if normalized_platform(platform) == "linux" and not environment.get("DISPLAY"):
        raise DesktopInstalledImageSmokeError(
            "Linux installed-image smoke requires DISPLAY; "
            "run the Gradle gate under `xvfb-run -a` on a headless host",
        )


def verify_process_result(
    result: subprocess.CompletedProcess[str],
    *,
    platform: str,
) -> None:
    if result.returncode != 0:
        raise DesktopInstalledImageSmokeError(
            "installed image exited non-zero "
            f"({result.returncode}); stdout={result.stdout!r}; "
            f"stderr={result.stderr!r}",
        )
    expected_stdout = f"{SMOKE_READY_MARKER}\n"
    allowed_stdout = (
        {"", expected_stdout}
        if normalized_platform(platform) == "windows"
        else {expected_stdout}
    )
    if result.stdout not in allowed_stdout:
        raise DesktopInstalledImageSmokeError(
            "installed image stdout violated the host smoke policy; "
            f"allowed={sorted(allowed_stdout)!r}; actual={result.stdout!r}",
        )
    if result.stderr:
        raise DesktopInstalledImageSmokeError(
            "installed image emitted unexpected stderr; "
            f"actual={result.stderr!r}",
        )


def verify_result_file(result_file: Path) -> None:
    if result_file.is_symlink() or not result_file.is_file():
        raise DesktopInstalledImageSmokeError(
            f"installed image did not create the smoke result file: {result_file}",
        )
    expected_bytes = f"{SMOKE_READY_MARKER}\n".encode("utf-8")
    actual_bytes = result_file.read_bytes()
    if actual_bytes != expected_bytes:
        raise DesktopInstalledImageSmokeError(
            "installed image smoke result content was not exact; "
            f"expected={expected_bytes!r}; actual={actual_bytes!r}",
        )


def find_leaked_temporary_roots(temporary_root: Path) -> tuple[Path, ...]:
    return tuple(
        sorted(
            temporary_root.glob(f"{SMOKE_TEMPORARY_PREFIX}*"),
            key=lambda path: path.name,
        ),
    )


def run_installed_image_smoke(
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
        prefix="kani-desktop-smoke-runner-",
    ) as temporary:
        isolated_root = Path(temporary)
        result_file = isolated_root / SMOKE_RESULT_FILENAME
        if result_file.exists():
            raise DesktopInstalledImageSmokeError(
                f"smoke result path unexpectedly exists before launch: {result_file}",
            )
        environment = isolated_temporary_environment(
            isolated_root,
            base_environment,
        )
        environment[SMOKE_RESULT_FILE_ENVIRONMENT_VARIABLE] = str(result_file)
        command: Sequence[str] = (str(launcher), *SMOKE_ARGUMENTS)
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
            raise DesktopInstalledImageSmokeError(
                f"installed image exceeded the {timeout_seconds}-second timeout",
            ) from error

        verify_process_result(result, platform=host)
        verify_result_file(result_file)
        leaked_roots = find_leaked_temporary_roots(isolated_root)
        if leaked_roots:
            leaked_names = ", ".join(path.name for path in leaked_roots)
            raise DesktopInstalledImageSmokeError(
                f"installed image leaked temporary data roots: {leaked_names}",
            )


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--image-root",
        required=True,
        type=Path,
        help="Compose installed-image root containing Kani or Kani.app",
    )
    parser.add_argument(
        "--timeout-seconds",
        type=int,
        default=DEFAULT_TIMEOUT_SECONDS,
    )
    args = parser.parse_args(argv)
    if args.timeout_seconds <= 0:
        parser.error("--timeout-seconds must be positive")
    return args


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        run_installed_image_smoke(
            args.image_root,
            timeout_seconds=args.timeout_seconds,
        )
    except DesktopInstalledImageSmokeError as error:
        print(f"desktop installed-image smoke failed: {error}", file=sys.stderr)
        return 1
    print(SMOKE_READY_MARKER)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
