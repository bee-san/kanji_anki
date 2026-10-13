from __future__ import annotations

import io
import os
import subprocess
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from unittest import mock

from tools import run_desktop_installed_image_smoke as smoke


class InstalledImageLauncherTest(unittest.TestCase):
    def test_resolves_each_supported_compose_image_layout(self) -> None:
        layouts = {
            "linux": Path("Kani/bin/Kani"),
            "darwin": Path("Kani.app/Contents/MacOS/Kani"),
            "win32": Path("Kani/Kani.exe"),
        }
        with tempfile.TemporaryDirectory(prefix="kani-smoke-layout-") as temporary:
            image_root = Path(temporary)
            for platform, relative_launcher in layouts.items():
                launcher = image_root / relative_launcher
                launcher.parent.mkdir(parents=True, exist_ok=True)
                launcher.touch()
                launcher.chmod(0o755)

                self.assertEqual(
                    launcher.resolve(),
                    smoke.installed_image_launcher(image_root, platform),
                )

    def test_rejects_unsupported_platform(self) -> None:
        with self.assertRaisesRegex(
            smoke.DesktopInstalledImageSmokeError,
            "unsupported desktop smoke platform",
        ):
            smoke.normalized_platform("plan9")

    def test_rejects_missing_launcher(self) -> None:
        with tempfile.TemporaryDirectory(prefix="kani-smoke-missing-") as temporary:
            with self.assertRaisesRegex(
                smoke.DesktopInstalledImageSmokeError,
                "launcher is missing",
            ):
                smoke.installed_image_launcher(Path(temporary), "linux")

    @unittest.skipIf(os.name == "nt", "POSIX executable bits are unavailable")
    def test_rejects_non_executable_unix_launcher(self) -> None:
        with tempfile.TemporaryDirectory(prefix="kani-smoke-mode-") as temporary:
            launcher = Path(temporary) / "Kani/bin/Kani"
            launcher.parent.mkdir(parents=True)
            launcher.touch(mode=0o600)

            with self.assertRaisesRegex(
                smoke.DesktopInstalledImageSmokeError,
                "launcher is not executable",
            ):
                smoke.installed_image_launcher(Path(temporary), "linux")


class InstalledImageSmokeContractTest(unittest.TestCase):
    def test_runs_paired_arguments_in_an_isolated_temporary_directory(self) -> None:
        with self.linux_image() as image_root:
            observed: dict[str, object] = {}

            def successful_runner(
                command: tuple[str, ...],
                **kwargs: object,
            ) -> subprocess.CompletedProcess[str]:
                observed["command"] = command
                observed.update(kwargs)
                result_file = Path(
                    kwargs["env"][smoke.SMOKE_RESULT_FILE_ENVIRONMENT_VARIABLE],
                )
                self.assertFalse(result_file.exists())
                result_file.write_bytes(
                    f"{smoke.SMOKE_READY_MARKER}\n".encode("utf-8"),
                )
                return subprocess.CompletedProcess(
                    command,
                    0,
                    stdout=f"{smoke.SMOKE_READY_MARKER}\n",
                    stderr="",
                )

            smoke.run_installed_image_smoke(
                image_root,
                platform="linux",
                base_environment={
                    "DISPLAY": ":99",
                    "PATH": "/reviewed/path",
                },
                process_runner=successful_runner,
            )

            command = observed["command"]
            self.assertEqual(
                (
                    str((image_root / "Kani/bin/Kani").resolve()),
                    "--smoke-test",
                    "--temporary-data",
                ),
                command,
            )
            environment = observed["env"]
            self.assertEqual("/reviewed/path", environment["PATH"])
            self.assertEqual(":99", environment["DISPLAY"])
            self.assertEqual(environment["TMPDIR"], environment["TEMP"])
            self.assertEqual(environment["TMPDIR"], environment["TMP"])
            self.assertEqual(
                str(Path(environment["TMPDIR"]) / smoke.SMOKE_RESULT_FILENAME),
                environment[smoke.SMOKE_RESULT_FILE_ENVIRONMENT_VARIABLE],
            )
            self.assertFalse(Path(environment["TMPDIR"]).exists())
            self.assertEqual(subprocess.DEVNULL, observed["stdin"])
            self.assertEqual(smoke.DEFAULT_TIMEOUT_SECONDS, observed["timeout"])

    def test_rejects_nonzero_exit(self) -> None:
        self.assert_result_rejected(
            subprocess.CompletedProcess(
                ["Kani"],
                9,
                stdout="",
                stderr="renderer failed",
            ),
            "exited non-zero",
        )

    def test_rejects_missing_or_additional_stdout(self) -> None:
        for stdout in (
            "",
            smoke.SMOKE_READY_MARKER,
            f"warning\n{smoke.SMOKE_READY_MARKER}\n",
            f"{smoke.SMOKE_READY_MARKER}\nextra\n",
        ):
            with self.subTest(stdout=stdout):
                self.assert_result_rejected(
                    subprocess.CompletedProcess(
                        ["Kani"],
                        0,
                        stdout=stdout,
                        stderr="",
                    ),
                    "stdout violated",
                )

    def test_windows_gui_launcher_may_omit_stdout(self) -> None:
        smoke.verify_process_result(
            subprocess.CompletedProcess(
                ["Kani.exe"],
                0,
                stdout="",
                stderr="",
            ),
            platform="windows",
        )

    def test_windows_gui_launcher_uses_result_file_without_console_stdout(self) -> None:
        with tempfile.TemporaryDirectory(prefix="kani-smoke-windows-image-") as temporary:
            image_root = Path(temporary)
            launcher = image_root / "Kani/Kani.exe"
            launcher.parent.mkdir(parents=True)
            launcher.touch()

            def gui_runner(
                command: tuple[str, ...],
                **kwargs: object,
            ) -> subprocess.CompletedProcess[str]:
                environment = kwargs["env"]
                Path(
                    environment[smoke.SMOKE_RESULT_FILE_ENVIRONMENT_VARIABLE],
                ).write_bytes(
                    f"{smoke.SMOKE_READY_MARKER}\n".encode("utf-8"),
                )
                return subprocess.CompletedProcess(
                    command,
                    0,
                    stdout="",
                    stderr="",
                )

            smoke.run_installed_image_smoke(
                image_root,
                platform="win32",
                base_environment={"PATH": r"C:\reviewed"},
                process_runner=gui_runner,
            )

    def test_non_windows_launcher_must_publish_stdout_marker(self) -> None:
        self.assert_result_rejected(
            subprocess.CompletedProcess(
                ["Kani"],
                0,
                stdout="",
                stderr="",
            ),
            "stdout violated",
            platform="linux",
        )

    def test_macos_accepts_only_the_known_software_gl_warning(self) -> None:
        warning = (
            "WARNING: GL pipe is running in software mode "
            "(Renderer ID=0x1020400)\n"
        )
        smoke.verify_process_result(
            subprocess.CompletedProcess(
                ["Kani"],
                0,
                stdout=f"{smoke.SMOKE_READY_MARKER}\n",
                stderr=warning,
            ),
            platform="darwin",
        )

        for platform, stderr in (
            ("linux", warning),
            ("macos", warning.rstrip()),
            ("macos", f"{warning}unexpected\n"),
            ("macos", "WARNING: unrelated renderer warning\n"),
        ):
            with self.subTest(platform=platform, stderr=stderr):
                self.assert_result_rejected(
                    subprocess.CompletedProcess(
                        ["Kani"],
                        0,
                        stdout=f"{smoke.SMOKE_READY_MARKER}\n",
                        stderr=stderr,
                    ),
                    "unexpected stderr",
                    platform=platform,
                )

    def test_rejects_any_stderr(self) -> None:
        self.assert_result_rejected(
            subprocess.CompletedProcess(
                ["Kani"],
                0,
                stdout=f"{smoke.SMOKE_READY_MARKER}\n",
                stderr="native warning\n",
            ),
            "unexpected stderr",
        )

    def test_rejects_leaked_smoke_data_root(self) -> None:
        with self.linux_image() as image_root:
            def leaking_runner(
                command: tuple[str, ...],
                **kwargs: object,
            ) -> subprocess.CompletedProcess[str]:
                environment = kwargs["env"]
                temporary_root = Path(environment["TMPDIR"])
                (temporary_root / "kani-desktop-smoke-leaked").mkdir()
                Path(
                    environment[smoke.SMOKE_RESULT_FILE_ENVIRONMENT_VARIABLE],
                ).write_bytes(
                    f"{smoke.SMOKE_READY_MARKER}\n".encode("utf-8"),
                )
                return subprocess.CompletedProcess(
                    command,
                    0,
                    stdout=f"{smoke.SMOKE_READY_MARKER}\n",
                    stderr="",
                )

            with self.assertRaisesRegex(
                smoke.DesktopInstalledImageSmokeError,
                "leaked temporary data roots",
            ):
                smoke.run_installed_image_smoke(
                    image_root,
                    platform="linux",
                    base_environment={"DISPLAY": ":99"},
                    process_runner=leaking_runner,
                )

    def test_rejects_missing_result_file_even_when_stdout_is_exact(self) -> None:
        with self.linux_image() as image_root:
            def no_result_runner(
                command: tuple[str, ...],
                **kwargs: object,
            ) -> subprocess.CompletedProcess[str]:
                return subprocess.CompletedProcess(
                    command,
                    0,
                    stdout=f"{smoke.SMOKE_READY_MARKER}\n",
                    stderr="",
                )

            with self.assertRaisesRegex(
                smoke.DesktopInstalledImageSmokeError,
                "did not create the smoke result file",
            ):
                smoke.run_installed_image_smoke(
                    image_root,
                    platform="linux",
                    base_environment={"DISPLAY": ":99"},
                    process_runner=no_result_runner,
                )

    def test_rejects_wrong_result_file_content(self) -> None:
        with self.linux_image() as image_root:
            def wrong_result_runner(
                command: tuple[str, ...],
                **kwargs: object,
            ) -> subprocess.CompletedProcess[str]:
                environment = kwargs["env"]
                Path(
                    environment[smoke.SMOKE_RESULT_FILE_ENVIRONMENT_VARIABLE],
                ).write_bytes(b"almost ready\n")
                return subprocess.CompletedProcess(
                    command,
                    0,
                    stdout=f"{smoke.SMOKE_READY_MARKER}\n",
                    stderr="",
                )

            with self.assertRaisesRegex(
                smoke.DesktopInstalledImageSmokeError,
                "result content was not exact",
            ):
                smoke.run_installed_image_smoke(
                    image_root,
                    platform="linux",
                    base_environment={"DISPLAY": ":99"},
                    process_runner=wrong_result_runner,
                )

    def test_reports_timeout_without_accepting_partial_output(self) -> None:
        with self.linux_image() as image_root:
            def timing_out_runner(
                command: tuple[str, ...],
                **kwargs: object,
            ) -> subprocess.CompletedProcess[str]:
                raise subprocess.TimeoutExpired(
                    command,
                    kwargs["timeout"],
                    output=f"{smoke.SMOKE_READY_MARKER}\n",
                )

            with self.assertRaisesRegex(
                smoke.DesktopInstalledImageSmokeError,
                "exceeded the 120-second timeout",
            ):
                smoke.run_installed_image_smoke(
                    image_root,
                    platform="linux",
                    base_environment={"DISPLAY": ":99"},
                    process_runner=timing_out_runner,
                )

    def test_linux_headless_failure_names_the_xvfb_contract(self) -> None:
        with self.linux_image() as image_root:
            with self.assertRaisesRegex(
                smoke.DesktopInstalledImageSmokeError,
                "xvfb-run -a",
            ):
                smoke.run_installed_image_smoke(
                    image_root,
                    platform="linux",
                    base_environment={"PATH": "/reviewed/path"},
                    process_runner=mock.Mock(),
                )

    def test_main_prints_only_the_verified_marker(self) -> None:
        stdout = io.StringIO()
        with mock.patch.object(smoke, "run_installed_image_smoke"), redirect_stdout(stdout):
            result = smoke.main(["--image-root", "/tmp/application-image"])

        self.assertEqual(0, result)
        self.assertEqual(f"{smoke.SMOKE_READY_MARKER}\n", stdout.getvalue())

    def test_main_fails_closed_with_diagnostic_on_stderr(self) -> None:
        stderr = io.StringIO()
        with (
            mock.patch.object(
                smoke,
                "run_installed_image_smoke",
                side_effect=smoke.DesktopInstalledImageSmokeError("bad image"),
            ),
            redirect_stderr(stderr),
        ):
            result = smoke.main(["--image-root", "/tmp/application-image"])

        self.assertEqual(1, result)
        self.assertEqual(
            "desktop installed-image smoke failed: bad image\n",
            stderr.getvalue(),
        )

    def assert_result_rejected(
        self,
        result: subprocess.CompletedProcess[str],
        message: str,
        *,
        platform: str = "linux",
    ) -> None:
        with self.assertRaisesRegex(
            smoke.DesktopInstalledImageSmokeError,
            message,
        ):
            smoke.verify_process_result(result, platform=platform)

    @staticmethod
    def linux_image() -> tempfile.TemporaryDirectory[str]:
        class LinuxImage(tempfile.TemporaryDirectory[str]):
            def __enter__(self) -> Path:
                root = Path(super().__enter__())
                launcher = root / "Kani/bin/Kani"
                launcher.parent.mkdir(parents=True)
                launcher.touch(mode=0o700)
                return root

        return LinuxImage(prefix="kani-smoke-image-")


if __name__ == "__main__":
    unittest.main()
