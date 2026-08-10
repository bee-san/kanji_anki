#!/usr/bin/env python3
"""Measure Kani's installed desktop image against its startup and memory budgets.

The measured path is the installed-image smoke path, reused from
`run_desktop_installed_image_smoke` rather than reimplemented: the launcher, the
isolated temporary environment, the render-environment precondition, and the
"did it really render" result file all come from there. That reuse is the point.
A startup number measured against a stripped-down launch would describe a launch
no user performs, and would have missed the defect this gate was written
alongside — a packaged runtime image with no `java.net.http`, which launched and
rendered and then crashed on the first provider call.

What this gate is and is not:

  - It is a **regression** budget. It catches an order-of-magnitude change in how
    long the packaged app takes to render and how much memory it needs to get
    there, on hardware it has never seen.
  - It is **not** a benchmark. The budgets are deliberately several times the
    measured median (see `DEFAULT_STARTUP_BUDGET_SECONDS`), because a gate tight
    enough to catch a 10% drift on a shared CI runner is a gate that fails for
    reasons that are not Kani's and then gets bumped or deleted. A loose budget
    that stays is worth more than a tight one that does not.
  - It measures only what a launch can show: time to rendered-and-settled, and
    peak resident memory of the whole process tree. Goal 203's remaining
    measurements — first sync, a 7,000-note sync, dashboard load, Study action
    latency, chart rendering, backup/restore — need a live provider and the
    complete composition root, and are recorded as not-yet-measured rather than
    estimated. See `docs/desktop-performance-budgets.md`.
"""

from __future__ import annotations

import argparse
import json
import os
import statistics
import subprocess
import sys
import tempfile
import time
from collections.abc import Callable, Mapping, Sequence
from pathlib import Path

from tools.run_desktop_installed_image_smoke import (
    SMOKE_ARGUMENTS,
    SMOKE_RESULT_FILE_ENVIRONMENT_VARIABLE,
    SMOKE_RESULT_FILENAME,
    DesktopInstalledImageSmokeError,
    installed_image_launcher,
    isolated_temporary_environment,
    normalized_platform,
    verify_process_result,
    verify_render_environment,
    verify_result_file,
)

try:
    # Unix-only stdlib. Imported lazily-by-guard rather than at the top because a
    # bare `import resource` fails the *whole module import* on Windows, and this
    # module is imported by `tools.test_measure_desktop_startup_budget`, which
    # `testDesktopTooling` runs on all three hosts. The Windows and macOS desktop
    # lanes were failing with `ModuleNotFoundError: No module named 'resource'`
    # before a single test ran, which reads as a broken gate rather than as the
    # one honest fact it is: this host cannot supply a tree-wide peak.
    import resource
except ModuleNotFoundError:  # pragma: no cover - only reachable on Windows
    resource = None  # type: ignore[assignment]


# Measured on the recorded baseline host: 3.131s median startup and 245.5 MiB peak
# (docs/desktop-performance-budgets.md). The budgets are ~9.6x and ~3.1x those,
# which is the headroom between a 16-core workstation and a shared CI runner with
# fewer cores, no GPU, and neighbours. Raise one only with a fresh recorded
# measurement in that document saying why; never to make a run pass.
DEFAULT_STARTUP_BUDGET_SECONDS = 30.0
DEFAULT_PEAK_MEMORY_BUDGET_MIB = 768.0

# One discarded warm-up plus three measured rounds. The warm-up exists because the
# first launch on a host pays for page cache and font cache misses that no later
# launch pays, and reporting it as a startup time would describe the filesystem
# rather than the app. Three measured rounds so the reported value is a median and
# not a single sample that happened to land next to another process.
DEFAULT_WARMUP_ROUNDS = 1
DEFAULT_MEASURED_ROUNDS = 3
DEFAULT_TIMEOUT_SECONDS = 180

KIB_PER_MIB = 1024.0
BYTES_PER_MIB = 1024.0 * 1024.0

# The one host allowed to report no peak at all. Named, and checked at both the
# measuring and the verifying end, so "unmeasurable" cannot quietly spread to a
# host that *can* measure and take the memory budget out of enforcement there.
HOST_WITHOUT_PEAK_MEMORY = "windows"

PEAK_MEMORY_UNMEASURED_NOTE = (
    "peak memory not measured: this host has no `resource` module, so there is no "
    "tree-wide high-water mark to read. The memory budget is NOT enforced here; "
    "the startup budget still is. Linux and macOS enforce both."
)


class DesktopStartupBudgetError(RuntimeError):
    """Raised when the installed image misses its startup or memory budget."""


def ru_maxrss_per_mib(platform: str = sys.platform) -> float:
    """The divisor that converts this host's `ru_maxrss` into MiB.

    `ru_maxrss` units are platform-dependent: Linux reports kilobytes, macOS
    reports **bytes**. A single `/1024` therefore over-reported every macOS peak by
    1024x — a real 245 MiB launch recorded as 251,136 MiB — so the macOS lane could
    only ever breach the 768 MiB budget, no matter how small the image's real
    footprint was.

    That is a false failure rather than a missed regression, which is the less
    dangerous direction but not a harmless one: a gate that fails for a reason that
    is not Kani's is the gate this module's own header says gets bumped or deleted,
    and the tempting "fix" is to raise the memory budget past 251,136 MiB, which
    would un-enforce it everywhere.
    """
    return BYTES_PER_MIB if normalized_platform(platform) == "macos" else KIB_PER_MIB


def peak_child_memory_mib(platform: str = sys.platform) -> float | None:
    """Peak resident memory of every child this process has waited on, in MiB.

    Returns `None` when the host cannot supply the figure at all (Windows, which
    has no `resource` module). `None` means *unmeasured*, and is deliberately not
    `0.0`: a zero compares fine against every budget forever, which turns a
    missing measurement into a permanently passing check.

    `ru_maxrss` for `RUSAGE_CHILDREN` is a tree-wide high-water mark that never
    decreases, and that shape decides how it can honestly be used. It cannot be
    attributed to one round: the first launch sets the mark and every later launch
    of the same image reads the same value, so a per-round delta is ~0 by
    construction — which is exactly what the first version of this gate reported,
    passing any memory regression smaller than the warm-up peak.
    So it is used as what it is: one maximum across every launch, warm-up
    included, because the warm-up is a real launch of the same image and the
    question is how much memory this image ever needs.

    It covers the whole tree, so on Linux under `xvfb-run` the X server is inside
    the number. That overstates Kani's own footprint and is kept anyway: for a
    ceiling, over-reporting is the safe direction to be wrong in.
    """
    if resource is None:
        return None
    peak = resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss
    return peak / ru_maxrss_per_mib(platform)


def launch_once(
    launcher: Path,
    *,
    host: str,
    timeout_seconds: int,
    base_environment: Mapping[str, str],
    process_runner: Callable[..., subprocess.CompletedProcess[str]],
    monotonic: Callable[[], float],
) -> float:
    """Launches the image once and returns how long it took to render, in seconds.

    Verifies the smoke contract on every round, not just the first. A launch that
    exited zero but never wrote its sentinel did not render, and timing a launch
    that did not render would report a fast startup for a broken app.
    """
    with tempfile.TemporaryDirectory(prefix="kani-desktop-budget-") as temporary:
        isolated_root = Path(temporary)
        result_file = isolated_root / SMOKE_RESULT_FILENAME
        environment = isolated_temporary_environment(isolated_root, base_environment)
        environment[SMOKE_RESULT_FILE_ENVIRONMENT_VARIABLE] = str(result_file)

        started = monotonic()
        try:
            result = process_runner(
                (str(launcher), *SMOKE_ARGUMENTS),
                cwd=launcher.parent,
                env=environment,
                capture_output=True,
                text=True,
                check=False,
                timeout=timeout_seconds,
                stdin=subprocess.DEVNULL,
            )
        except subprocess.TimeoutExpired as error:
            raise DesktopStartupBudgetError(
                f"installed image exceeded the {timeout_seconds}-second timeout, "
                "so it has no startup time to compare against the budget",
            ) from error
        elapsed = monotonic() - started

        verify_process_result(result, platform=host)
        verify_result_file(result_file)
        return elapsed


def measure_installed_image(
    image_root: Path,
    *,
    platform: str = sys.platform,
    warmup_rounds: int = DEFAULT_WARMUP_ROUNDS,
    measured_rounds: int = DEFAULT_MEASURED_ROUNDS,
    timeout_seconds: int = DEFAULT_TIMEOUT_SECONDS,
    base_environment: Mapping[str, str] = os.environ,
    process_runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run,
    monotonic: Callable[[], float] = time.monotonic,
    peak_memory: Callable[[], float | None] | None = None,
) -> dict[str, object]:
    """Launches the image `warmup + measured` times and reports the medians."""
    if measured_rounds < 1:
        raise DesktopStartupBudgetError("measured rounds must be at least 1")
    if warmup_rounds < 0:
        raise DesktopStartupBudgetError("warm-up rounds must not be negative")

    launcher = installed_image_launcher(image_root, platform)
    verify_render_environment(platform, base_environment)
    host = normalized_platform(platform)
    # Bound to the platform being measured rather than defaulted in the signature,
    # so the unit conversion follows the host under measurement instead of whatever
    # `sys.platform` happened to be at import time.
    read_peak = peak_memory or (lambda: peak_child_memory_mib(platform))

    seconds: list[float] = []
    for round_index in range(warmup_rounds + measured_rounds):
        elapsed = launch_once(
            launcher,
            host=host,
            timeout_seconds=timeout_seconds,
            base_environment=base_environment,
            process_runner=process_runner,
            monotonic=monotonic,
        )
        # Time is per-round and the warm-up round's time is discarded; memory is
        # not, because it cannot be — see `peak_child_memory_mib`. The peak is read
        # once at the end and covers every launch, warm-up included.
        if round_index >= warmup_rounds:
            seconds.append(elapsed)

    peak = read_peak()
    if peak is None and host != HOST_WITHOUT_PEAK_MEMORY:
        # A host that should be able to measure but did not is a broken gate, not a
        # host limitation, and must not be recorded as one — that is precisely how a
        # budget stops being enforced without anyone deciding to stop enforcing it.
        raise DesktopStartupBudgetError(
            f"host {host} reported no peak memory, but only "
            f"{HOST_WITHOUT_PEAK_MEMORY} is unable to measure it",
        )

    measurement: dict[str, object] = {
        "host": host,
        "warmup_rounds": warmup_rounds,
        "measured_rounds": measured_rounds,
        "startup_seconds": [round(value, 3) for value in seconds],
        "startup_seconds_median": round(statistics.median(seconds), 3),
        "peak_memory_mib": None if peak is None else round(peak, 1),
    }
    if peak is None:
        measurement["peak_memory_note"] = PEAK_MEMORY_UNMEASURED_NOTE
    return measurement


def verify_budgets(
    measurement: Mapping[str, object],
    *,
    startup_budget_seconds: float,
    peak_memory_budget_mib: float,
) -> None:
    """Fails when either median is over budget, naming both numbers.

    Both are checked before raising, so one run reports every breach rather than
    hiding a memory regression behind a startup regression.
    """
    breaches: list[str] = []
    startup = float(measurement["startup_seconds_median"])  # type: ignore[arg-type]
    recorded_memory = measurement["peak_memory_mib"]
    if startup > startup_budget_seconds:
        breaches.append(
            f"startup median {startup:.3f}s exceeds the "
            f"{startup_budget_seconds:.3f}s budget",
        )
    if recorded_memory is None:
        # Only the host that genuinely cannot measure may skip this check. Verified
        # here as well as at measurement time because a measurement can arrive from a
        # recorded baseline file, and a `null` peak with the wrong host on it would
        # otherwise silently buy an exemption for a host that has none.
        host = str(measurement.get("host", ""))
        if host != HOST_WITHOUT_PEAK_MEMORY:
            breaches.append(
                f"peak memory is missing for host {host!r}, which is expected to "
                "measure it",
            )
    elif float(recorded_memory) > peak_memory_budget_mib:  # type: ignore[arg-type]
        breaches.append(
            f"peak memory {float(recorded_memory):.1f} MiB exceeds the "
            f"{peak_memory_budget_mib:.1f} MiB budget",
        )
    if breaches:
        raise DesktopStartupBudgetError("; ".join(breaches))


def format_report(
    measurement: Mapping[str, object],
    *,
    startup_budget_seconds: float,
    peak_memory_budget_mib: float,
) -> str:
    startup = float(measurement["startup_seconds_median"])  # type: ignore[arg-type]
    recorded_memory = measurement["peak_memory_mib"]
    # Reported as the word "unmeasured", not as a number, so a release record from
    # the Windows lane cannot be read later as evidence the image was inside its
    # memory budget on Windows. It is evidence of nothing on that axis.
    memory_line = (
        f"peak_memory_mib=unmeasured budget={peak_memory_budget_mib:.1f} "
        "(not enforced on this host)"
        if recorded_memory is None
        else f"peak_memory_mib={float(recorded_memory):.1f} "  # type: ignore[arg-type]
        f"budget={peak_memory_budget_mib:.1f} (all launches, whole tree)"
    )
    return "\n".join(
        (
            f"host={measurement['host']} "
            f"warmup={measurement['warmup_rounds']} "
            f"measured={measurement['measured_rounds']}",
            f"startup_seconds={measurement['startup_seconds']} "
            f"median={startup:.3f} budget={startup_budget_seconds:.3f}",
            memory_line,
        ),
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
        "--startup-budget-seconds",
        type=float,
        default=DEFAULT_STARTUP_BUDGET_SECONDS,
    )
    parser.add_argument(
        "--peak-memory-budget-mib",
        type=float,
        default=DEFAULT_PEAK_MEMORY_BUDGET_MIB,
    )
    parser.add_argument("--warmup-rounds", type=int, default=DEFAULT_WARMUP_ROUNDS)
    parser.add_argument("--measured-rounds", type=int, default=DEFAULT_MEASURED_ROUNDS)
    parser.add_argument(
        "--timeout-seconds",
        type=int,
        default=DEFAULT_TIMEOUT_SECONDS,
    )
    parser.add_argument(
        "--json-out",
        type=Path,
        help="Write the raw measurement to this path for a baseline record",
    )
    args = parser.parse_args(argv)
    if args.timeout_seconds <= 0:
        parser.error("--timeout-seconds must be positive")
    if args.startup_budget_seconds <= 0:
        parser.error("--startup-budget-seconds must be positive")
    if args.peak_memory_budget_mib <= 0:
        parser.error("--peak-memory-budget-mib must be positive")
    return args


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        measurement = measure_installed_image(
            args.image_root,
            warmup_rounds=args.warmup_rounds,
            measured_rounds=args.measured_rounds,
            timeout_seconds=args.timeout_seconds,
        )
        report = format_report(
            measurement,
            startup_budget_seconds=args.startup_budget_seconds,
            peak_memory_budget_mib=args.peak_memory_budget_mib,
        )
        # Printed before the verdict, so a breach ships the numbers that caused it
        # rather than only the message saying there was one.
        print(report)
        if args.json_out is not None:
            args.json_out.write_text(
                json.dumps(measurement, indent=2, sort_keys=True) + "\n",
                encoding="utf-8",
            )
        verify_budgets(
            measurement,
            startup_budget_seconds=args.startup_budget_seconds,
            peak_memory_budget_mib=args.peak_memory_budget_mib,
        )
    except (DesktopStartupBudgetError, DesktopInstalledImageSmokeError) as error:
        print(f"desktop startup budget failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
