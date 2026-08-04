from __future__ import annotations

import io
import json
import subprocess
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


def linux_image() -> tempfile.TemporaryDirectory[str]:
    class LinuxImage(tempfile.TemporaryDirectory[str]):
        def __enter__(self) -> Path:
            root = Path(super().__enter__())
            launcher = root / "Kani/bin/Kani"
            launcher.parent.mkdir(parents=True)
            launcher.touch(mode=0o700)
            return root

    return LinuxImage(prefix="kani-budget-image-")


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
