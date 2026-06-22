# Kani trace probe harness

This harness captures route startup/navigation traces without UiAutomator polling.
It is intentionally app-pid-only: the script launches a route with `am start -W`, waits for the app pid, and records pid-filtered `KaniPerf` / `Choreographer` / `HWUI` logcat plus `dumpsys gfxinfo`.

## Usage

Capture the default route matrix:

```bash
ci/scripts/capture_kani_trace_probe.sh all
```

Capture a single route:

```bash
KANI_TRACE_PROBE_DIR=reports/kani-trace-probe \
  ci/scripts/capture_kani_trace_probe.sh stats
```

## Assumptions

- An adb-connected device or emulator is already booted, unlocked, and ready for shell commands.
- The debug APK exists at `app/build/outputs/apk/debug/app-debug.apk`.
- The local store is already seeded with the same representative data state used in the diagnosis run:
  `pm clear dev.bee.kanjianki`, then `ButtonLatencyFixtureInstrumentedTest#seedRepresentativeLocalStoreForButtonLatencyBenchmark`.
- The probe does not use UiAutomator polling or UI tree matching.

## Outputs

For each captured route the script writes:

- `am-start.txt`
- `pid.txt`
- `logcat.txt`
- `gfxinfo.txt`
- `manifest.json`

The root manifest summarizes the captured routes and includes the device/data-state assumptions used for the run.
