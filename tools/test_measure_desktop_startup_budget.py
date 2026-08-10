from __future__ import annotations

import io
import json
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path

from tools import measure_desktop_startup_budget as budget
from tools import run_desktop_installed_image_smoke as smoke


class FakeLaunches:
    """A process runner that satisfies the smoke contract on a scripted clock.

    Written as a fake rather than mocks because every test here is about the
    arithmetic over several launches — which rounds count, which median is taken,
    which memory delta is attributed to which round — and that arithmetic is only
    visible when the launches are a sequence rather than one call.
    """

    def __init__(self, seconds: list[float], memory_mib: list[float]) -> None:
        self.seconds = list(seconds)
        self.memory_mib = list(memory_mib)
        self.clock = 0.0
        # A high-water mark, like the real `ru_maxrss`: it never decreases, which
        # is the property the delta arithmetic has to cope with.
        self.peak = 0.0
        self.launches = 0
        self.commands: list[tuple[str, ...]] = []

    def monotonic(self) -> float:
        return self.clock

    def peak_memory(self) -> float:
        return self.peak

    def run(
        self,
        command: tuple[str, ...],
        **kwargs: object,
    ) -> subprocess.CompletedProcess[str]:
        index = self.launches
        self.launches += 1
        self.commands.append(command)
        self.clock += self.seconds[index]
        self.peak = max(self.peak, self.memory_mib[index])
        result_file = Path(
            kwargs["env"][smoke.SMOKE_RESULT_FILE_ENVIRONMENT_VARIABLE],
        )
        result_file.write_bytes(f"{smoke.SMOKE_READY_MARKER}\n".encode("utf-8"))
        return subprocess.CompletedProcess(
            command,
            0,
            stdout=f"{smoke.SMOKE_READY_MARKER}\n",
            stderr="",
        )


def image_for(relative_launcher: str) -> tempfile.TemporaryDirectory[str]:
    class Image(tempfile.TemporaryDirectory[str]):
        def __enter__(self) -> Path:
            root = Path(super().__enter__())
            launcher = root / relative_launcher
            launcher.parent.mkdir(parents=True, exist_ok=True)
            launcher.touch(mode=0o700)
            return root

    return Image(prefix="kani-budget-image-")


def linux_image() -> tempfile.TemporaryDirectory[str]:
    return image_for("Kani/bin/Kani")


def windows_image() -> tempfile.TemporaryDirectory[str]:
    # A Windows-layout fixture, because the launcher path is per-host: measuring
    # `platform="win32"` against a Linux image fails on the launcher rather than on
    # the memory semantics the test is about.
    return image_for("Kani/Kani.exe")


LINUX_ENVIRONMENT = {"DISPLAY": ":99", "PATH": "/reviewed/path"}


class StartupMeasurementTest(unittest.TestCase):
    def measure(
        self,
        image_root: Path,
        launches: FakeLaunches,
        **kwargs: object,
    ) -> dict[str, object]:
        return budget.measure_installed_image(
            image_root,
            platform="linux",
            base_environment=LINUX_ENVIRONMENT,
            process_runner=launches.run,
            monotonic=launches.monotonic,
            peak_memory=launches.peak_memory,
            **kwargs,
        )

    def test_measures_the_real_smoke_launch_and_not_a_reduced_one(self) -> None:
        # The defect this whole gate was written alongside — a packaged runtime
        # image with no `java.net.http` — was invisible until the *real* launch
        # path ran, so a budget measured against anything else would have reported
        # a healthy startup for an app that crashed on its first provider call.
        with linux_image() as image_root:
            launches = FakeLaunches([1.0, 1.0], [10.0, 10.0])
            self.measure(image_root, launches, warmup_rounds=1, measured_rounds=1)

            for command in launches.commands:
                self.assertEqual(
                    (
                        str((image_root / "Kani/bin/Kani").resolve()),
                        "--smoke-test",
                        "--temporary-data",
                    ),
                    command,
                )

    def test_discards_warmup_rounds_from_the_reported_median(self) -> None:
        # The first launch on a host pays for cold page and font caches no later
        # launch pays. Reporting it would describe the filesystem, not the app.
        with linux_image() as image_root:
            launches = FakeLaunches([9.0, 2.0, 3.0, 4.0], [99.0, 20.0, 30.0, 40.0])
            measurement = self.measure(
                image_root,
                launches,
                warmup_rounds=1,
                measured_rounds=3,
            )

            self.assertEqual(4, launches.launches)
            self.assertEqual([2.0, 3.0, 4.0], measurement["startup_seconds"])
            self.assertEqual(3.0, measurement["startup_seconds_median"])
            self.assertNotIn(9.0, measurement["startup_seconds"])

    def test_reports_memory_as_one_peak_across_every_launch(self) -> None:
        # Memory is a ceiling, not a per-round sample. `ru_maxrss` for children is a
        # tree-wide high-water mark, so an earlier launch's peak is inside every
        # later read of it — the first version of this gate subtracted a "before"
        # from an "after" per round and therefore reported ~0 MiB every time,
        # passing any regression smaller than the warm-up peak. The warm-up counts
        # here, because it is a real launch of the same image.
        with linux_image() as image_root:
            launches = FakeLaunches([1.0, 1.0, 1.0], [500.0, 40.0, 60.0])
            measurement = self.measure(
                image_root,
                launches,
                warmup_rounds=1,
                measured_rounds=2,
            )

            self.assertEqual(500.0, measurement["peak_memory_mib"])
            # Time still drops its warm-up round; only memory cannot.
            self.assertEqual([1.0, 1.0], measurement["startup_seconds"])

    def test_a_late_memory_spike_is_not_hidden_by_a_quiet_warmup(self) -> None:
        with linux_image() as image_root:
            launches = FakeLaunches([1.0, 1.0, 1.0], [40.0, 60.0, 900.0])
            measurement = self.measure(
                image_root,
                launches,
                warmup_rounds=1,
                measured_rounds=2,
            )

            self.assertEqual(900.0, measurement["peak_memory_mib"])

    def test_a_launch_that_never_rendered_is_not_a_fast_launch(self) -> None:
        # Exit zero is not evidence of a rendered window. Timing a launch that did
        # not render would report the best startup time the app has ever had, for
        # a build that shows the user nothing.
        with linux_image() as image_root:

            def silent_runner(
                command: tuple[str, ...],
                **kwargs: object,
            ) -> subprocess.CompletedProcess[str]:
                return subprocess.CompletedProcess(command, 0, stdout="", stderr="")

            with self.assertRaises(smoke.DesktopInstalledImageSmokeError):
                budget.measure_installed_image(
                    image_root,
                    platform="linux",
                    base_environment=LINUX_ENVIRONMENT,
                    process_runner=silent_runner,
                    warmup_rounds=0,
                    measured_rounds=1,
                )

    def test_a_timeout_is_reported_as_having_no_measurement(self) -> None:
        with linux_image() as image_root:

            def timing_out(
                command: tuple[str, ...],
                **kwargs: object,
            ) -> subprocess.CompletedProcess[str]:
                raise subprocess.TimeoutExpired(command, 12)

            with self.assertRaises(budget.DesktopStartupBudgetError) as raised:
                budget.measure_installed_image(
                    image_root,
                    platform="linux",
                    base_environment=LINUX_ENVIRONMENT,
                    process_runner=timing_out,
                    warmup_rounds=0,
                    measured_rounds=1,
                    timeout_seconds=12,
                )
            self.assertIn("12-second timeout", str(raised.exception))

    def test_inherits_the_headless_render_precondition(self) -> None:
        # Reused from the smoke runner rather than restated: a Linux launch with no
        # DISPLAY fails to render, and "it did not render" must not be measured as
        # a startup time.
        with linux_image() as image_root:
            with self.assertRaises(smoke.DesktopInstalledImageSmokeError) as raised:
                budget.measure_installed_image(
                    image_root,
                    platform="linux",
                    base_environment={"PATH": "/reviewed/path"},
                    process_runner=FakeLaunches([1.0], [1.0]).run,
                    warmup_rounds=0,
                    measured_rounds=1,
                )
            self.assertIn("xvfb-run", str(raised.exception))

    def test_rejects_a_round_count_that_would_measure_nothing(self) -> None:
        with linux_image() as image_root:
            for rounds in ({"measured_rounds": 0}, {"warmup_rounds": -1}):
                with self.subTest(rounds=rounds):
                    with self.assertRaises(budget.DesktopStartupBudgetError):
                        budget.measure_installed_image(
                            image_root,
                            platform="linux",
                            base_environment=LINUX_ENVIRONMENT,
                            process_runner=FakeLaunches([1.0], [1.0]).run,
                            **rounds,
                        )


class PeakMemoryPortabilityTest(unittest.TestCase):
    """The peak-memory read must be portable, and honest where it cannot be taken.

    Two defects live here, and neither one fails loudly on its own:

      - `import resource` at module top level is Unix-only, so the Windows and
        macOS desktop lanes died with `ModuleNotFoundError` before running a
        single test -- `testDesktopTooling` imports this module on all three hosts.
      - `ru_maxrss` is in kilobytes on Linux and in **bytes** on macOS, so one
        `/1024` over-reported every macOS peak by 1024x: a real 245 MiB launch
        recorded as 251,136 MiB, which cannot pass a 768 MiB budget at any real
        footprint.
    """

    def test_the_module_imports_on_a_host_with_no_resource_module(self) -> None:
        # Reproduces the Windows failure by importing with `resource` blocked,
        # rather than by trusting the guard's shape by reading it.
        script = (
            "import sys\n"
            "sys.modules['resource'] = None\n"
            "import importlib\n"
            "module = importlib.import_module('tools.measure_desktop_startup_budget')\n"
            "assert module.peak_child_memory_mib('win32') is None\n"
            "print('imported')\n"
        )
        result = subprocess.run(
            [sys.executable, "-c", script],
            cwd=str(Path(__file__).resolve().parents[1]),
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("imported", result.stdout)

    def test_macos_ru_maxrss_is_read_as_bytes_and_linux_as_kilobytes(self) -> None:
        self.assertEqual(1024.0, budget.ru_maxrss_per_mib("linux"))
        self.assertEqual(1024.0 * 1024.0, budget.ru_maxrss_per_mib("darwin"))
        self.assertEqual(1024.0, budget.ru_maxrss_per_mib("win32"))

    def test_the_same_launch_measures_the_same_on_linux_and_macos(self) -> None:
        # One physical 245 MiB launch, as each kernel reports it. The conversion is
        # correct exactly when both land on the same number; under the old single
        # `/1024` the macOS figure was 251,136 MiB and could not pass any budget.
        mib = 245.25
        linux_raw = mib * 1024  # kilobytes
        macos_raw = mib * 1024 * 1024  # bytes

        self.assertAlmostEqual(mib, linux_raw / budget.ru_maxrss_per_mib("linux"))
        self.assertAlmostEqual(mib, macos_raw / budget.ru_maxrss_per_mib("darwin"))

        # And the old conversion, kept explicit so the regression is recognisable.
        self.assertEqual(251136.0, macos_raw / 1024)
        with self.assertRaises(budget.DesktopStartupBudgetError) as raised:
            budget.verify_budgets(
                {
                    "host": "macos",
                    "startup_seconds_median": 1.0,
                    "peak_memory_mib": macos_raw / 1024,
                },
                startup_budget_seconds=30.0,
                peak_memory_budget_mib=768.0,
            )
        self.assertIn("251136.0 MiB exceeds", str(raised.exception))

        # The corrected figure passes, because 245 MiB genuinely is inside budget.
        budget.verify_budgets(
            {
                "host": "macos",
                "startup_seconds_median": 1.0,
                "peak_memory_mib": macos_raw / budget.ru_maxrss_per_mib("darwin"),
            },
            startup_budget_seconds=30.0,
            peak_memory_budget_mib=768.0,
        )

    def test_a_real_macos_regression_still_fails_after_the_unit_fix(self) -> None:
        # The fix must not become a way of making macOS pass: a genuine 4 GiB peak
        # is over five times the budget and must still breach it.
        four_gib_in_bytes = 4 * 1024 * 1024 * 1024
        as_reported = four_gib_in_bytes / budget.ru_maxrss_per_mib("darwin")

        self.assertEqual(4096.0, as_reported)
        with self.assertRaises(budget.DesktopStartupBudgetError):
            budget.verify_budgets(
                {
                    "host": "macos",
                    "startup_seconds_median": 1.0,
                    "peak_memory_mib": as_reported,
                },
                startup_budget_seconds=30.0,
                peak_memory_budget_mib=768.0,
            )

    def test_an_unmeasured_peak_is_recorded_as_null_with_a_note_not_as_zero(self) -> None:
        # Zero would compare fine against every budget forever, so a missing
        # measurement would read as a passing one.
        with windows_image() as image_root:
            launches = FakeLaunches([1.0, 2.0], [0.0, 0.0])
            measurement = budget.measure_installed_image(
                image_root,
                platform="win32",
                base_environment={"PATH": "/reviewed/path"},
                process_runner=launches.run,
                monotonic=launches.monotonic,
                peak_memory=lambda: None,
                warmup_rounds=1,
                measured_rounds=1,
            )

            self.assertEqual("windows", measurement["host"])
            self.assertIsNone(measurement["peak_memory_mib"])
            self.assertIn("NOT enforced", str(measurement["peak_memory_note"]))
            # The startup budget is still enforced on that host.
            self.assertEqual(2.0, measurement["startup_seconds_median"])

    def test_a_host_that_should_measure_but_did_not_fails_rather_than_being_excused(
        self,
    ) -> None:
        # The exemption is for the one host that cannot measure. If it applied
        # wherever the figure is absent, then any future breakage of the read on
        # Linux or macOS would silently take the memory budget out of enforcement
        # there -- the failure mode this whole gate is supposed to prevent.
        with linux_image() as image_root:
            launches = FakeLaunches([1.0], [0.0])
            with self.assertRaises(budget.DesktopStartupBudgetError) as raised:
                budget.measure_installed_image(
                    image_root,
                    platform="linux",
                    base_environment=LINUX_ENVIRONMENT,
                    process_runner=launches.run,
                    monotonic=launches.monotonic,
                    peak_memory=lambda: None,
                    warmup_rounds=0,
                    measured_rounds=1,
                )
            self.assertIn("linux", str(raised.exception))
            self.assertIn("no peak memory", str(raised.exception))

    def test_a_recorded_null_peak_cannot_buy_an_exemption_for_the_wrong_host(
        self,
    ) -> None:
        # A measurement can arrive from a baseline JSON file rather than from a live
        # run, so the verifying end checks the host too.
        with self.assertRaises(budget.DesktopStartupBudgetError) as raised:
            budget.verify_budgets(
                {
                    "host": "macos",
                    "startup_seconds_median": 1.0,
                    "peak_memory_mib": None,
                },
                startup_budget_seconds=30.0,
                peak_memory_budget_mib=768.0,
            )
        self.assertIn("expected to measure it", str(raised.exception))

    def test_the_windows_report_says_unmeasured_rather_than_showing_a_number(
        self,
    ) -> None:
        # A release record must not be readable later as evidence the image was
        # inside its memory budget on a host that never measured it.
        report = budget.format_report(
            {
                "host": "windows",
                "warmup_rounds": 1,
                "measured_rounds": 3,
                "startup_seconds": [1.0],
                "startup_seconds_median": 1.0,
                "peak_memory_mib": None,
            },
            startup_budget_seconds=30.0,
            peak_memory_budget_mib=768.0,
        )

        self.assertIn("peak_memory_mib=unmeasured", report)
        self.assertIn("not enforced on this host", report)
        self.assertNotIn("0.0", report.split("peak_memory_mib=")[1].split()[0])

    def test_windows_still_passes_its_startup_budget_with_no_peak(self) -> None:
        budget.verify_budgets(
            {
                "host": "windows",
                "startup_seconds_median": 1.0,
                "peak_memory_mib": None,
            },
            startup_budget_seconds=30.0,
            peak_memory_budget_mib=768.0,
        )
        with self.assertRaises(budget.DesktopStartupBudgetError) as raised:
            budget.verify_budgets(
                {
                    "host": "windows",
                    "startup_seconds_median": 99.0,
                    "peak_memory_mib": None,
                },
                startup_budget_seconds=30.0,
                peak_memory_budget_mib=768.0,
            )
        self.assertIn("startup median", str(raised.exception))


class BudgetVerdictTest(unittest.TestCase):
    def measurement(self, startup: float, memory: float) -> dict[str, object]:
        return {
            "host": "linux",
            "warmup_rounds": 1,
            "measured_rounds": 3,
            "startup_seconds": [startup],
            "startup_seconds_median": startup,
            "peak_memory_mib": memory,
        }

    def test_passes_at_the_budget_and_fails_above_it(self) -> None:
        # Inclusive at the boundary: a budget stated as a limit that rejects the
        # limit is a budget nobody can state honestly.
        budget.verify_budgets(
            self.measurement(30.0, 768.0),
            startup_budget_seconds=30.0,
            peak_memory_budget_mib=768.0,
        )
        with self.assertRaises(budget.DesktopStartupBudgetError):
            budget.verify_budgets(
                self.measurement(30.001, 768.0),
                startup_budget_seconds=30.0,
                peak_memory_budget_mib=768.0,
            )

    def test_reports_both_breaches_from_one_run(self) -> None:
        # A memory regression hidden behind a startup regression comes back as a
        # second failing run after the first is fixed, which reads as a new defect.
        with self.assertRaises(budget.DesktopStartupBudgetError) as raised:
            budget.verify_budgets(
                self.measurement(99.0, 9999.0),
                startup_budget_seconds=30.0,
                peak_memory_budget_mib=768.0,
            )
        message = str(raised.exception)
        self.assertIn("startup median", message)
        self.assertIn("peak memory", message)

    def test_a_memory_regression_alone_fails(self) -> None:
        with self.assertRaises(budget.DesktopStartupBudgetError) as raised:
            budget.verify_budgets(
                self.measurement(1.0, 9999.0),
                startup_budget_seconds=30.0,
                peak_memory_budget_mib=768.0,
            )
        self.assertNotIn("startup median", str(raised.exception))

    def test_default_budgets_are_regression_sized_not_benchmark_sized(self) -> None:
        # Pinned so a future tightening is a deliberate edit with a recorded
        # measurement behind it. A gate tight enough to catch 10% drift on a
        # shared runner gets bumped or deleted instead of believed.
        self.assertEqual(30.0, budget.DEFAULT_STARTUP_BUDGET_SECONDS)
        self.assertEqual(768.0, budget.DEFAULT_PEAK_MEMORY_BUDGET_MIB)
        self.assertEqual(1, budget.DEFAULT_WARMUP_ROUNDS)
        self.assertEqual(3, budget.DEFAULT_MEASURED_ROUNDS)


class BudgetEntryPointTest(unittest.TestCase):
    def test_prints_the_numbers_before_the_verdict(self) -> None:
        # A breach must ship the measurement that caused it. A gate that fails
        # with only "over budget" makes the next person re-run it to learn by how
        # much, and on a CI runner they cannot.
        with linux_image() as image_root:
            launches = FakeLaunches([1.0, 40.0, 41.0, 42.0], [1.0, 1.0, 1.0, 1.0])
            stdout, stderr = io.StringIO(), io.StringIO()
            with redirect_stdout(stdout), redirect_stderr(stderr):
                exit_code = self.run_main(image_root, launches, ["--startup-budget-seconds", "30"])

            self.assertEqual(1, exit_code)
            self.assertIn("median=41.000", stdout.getvalue())
            self.assertIn("budget=30.000", stdout.getvalue())
            self.assertIn("41.000s exceeds", stderr.getvalue())

    def test_writes_a_baseline_record_on_request(self) -> None:
        with linux_image() as image_root, tempfile.TemporaryDirectory() as out:
            json_out = Path(out) / "baseline.json"
            launches = FakeLaunches([1.0, 2.0, 3.0, 4.0], [1.0, 1.0, 1.0, 1.0])
            stdout = io.StringIO()
            with redirect_stdout(stdout):
                exit_code = self.run_main(
                    image_root,
                    launches,
                    ["--json-out", str(json_out)],
                )

            self.assertEqual(0, exit_code)
            recorded = json.loads(json_out.read_text(encoding="utf-8"))
            self.assertEqual(3.0, recorded["startup_seconds_median"])
            self.assertEqual([2.0, 3.0, 4.0], recorded["startup_seconds"])

    def test_rejects_nonsense_budgets_before_launching_anything(self) -> None:
        for argument in (
            "--startup-budget-seconds",
            "--peak-memory-budget-mib",
            "--timeout-seconds",
        ):
            with self.subTest(argument=argument):
                with self.assertRaises(SystemExit):
                    with redirect_stderr(io.StringIO()):
                        budget.parse_args(
                            ["--image-root", "/nonexistent", argument, "0"],
                        )

    def run_main(
        self,
        image_root: Path,
        launches: FakeLaunches,
        extra: list[str],
    ) -> int:
        real_measure = budget.measure_installed_image

        def measure(root: Path, **kwargs: object) -> dict[str, object]:
            return real_measure(
                root,
                platform="linux",
                base_environment=LINUX_ENVIRONMENT,
                process_runner=launches.run,
                monotonic=launches.monotonic,
                peak_memory=launches.peak_memory,
                **kwargs,
            )

        budget.measure_installed_image = measure  # type: ignore[assignment]
        try:
            return budget.main(["--image-root", str(image_root), *extra])
        finally:
            budget.measure_installed_image = real_measure  # type: ignore[assignment]


if __name__ == "__main__":
    unittest.main()
