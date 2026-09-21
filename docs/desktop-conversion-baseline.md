# Desktop Conversion Baseline

**Date:** 2026-07-26
**Frozen commit:** `6459eafc91f0430bead139c867012232def11b31`
**Branch:** `desktop/support`
**Status:** Source, build, test, compile-time, and Android normal-launch
baselines recorded

This document freezes the pre-conversion shape of Kani after
[desktop-support Goal 164](../plans/desktop-support-goals-2026-07-26.md).
Later module moves must compare against this commit and the Goal 165 golden
fixtures instead of recounting a partially converted tree.

## Counting rules

- Counts use tracked files at the frozen commit. Generated sources and
  untracked Goal 165 work are excluded.
- Project source counts include Kotlin and Java files under `src/main`,
  `src/test`, and `src/androidTest`.
- The app's four `src/debug` Kotlin files and one `src/release` Kotlin file
  are reported separately because they are outside `src/main`.
- `build-logic` is an included build, not one of the eight projects. Its
  precompiled script plugins use `.gradle.kts`, so its count includes those
  scripts explicitly.
- Import counts are file counts. The Compose count is a subset of the
  Android/AndroidX count and must not be added to it.
- Test totals are executed test cases, not source files or `@Test` text
  matches.

## Project and source inventory

`settings.gradle.kts` includes eight projects: seven portable JVM libraries
and the Android application.

| Project | Build type | Direct project dependencies | Main | Unit-test | Android-test |
| --- | --- | --- | ---: | ---: | ---: |
| `:fsrs-java` | Kotlin/JVM library | none | 9 | 2 | 0 |
| `:core` | Kotlin/JVM library | `:dictionary-core`, `:domain`, `:sync-domain`, `:fsrs-java`, `:update-core` | 180 | 183 | 0 |
| `:domain` | Kotlin/JVM library | none | 1 | 1 | 0 |
| `:sync-domain` | Kotlin/JVM library | `:domain` | 8 | 10 | 0 |
| `:writing-core` | Kotlin/JVM library | `:domain` | 29 | 17 | 0 |
| `:dictionary-core` | Kotlin/JVM library | none | 7 | 6 | 0 |
| `:update-core` | Kotlin/JVM library | none | 18 | 17 | 0 |
| `:app` | Android application | `:core`, `:dictionary-core`, `:update-core`, `:writing-core` | 378 | 258 | 93 |
| **Project total** |  |  | **630** | **494** | **93** |

The seven non-app projects therefore contain 252 portable production source
files. The app has 378 main-source files plus four debug-variant and one
release-variant Kotlin files.

The `build-logic` included build contains:

- seven production Kotlin/precompiled-script files;
- two real Kotlin test classes; and
- five Kotlin, Java, or Gradle-script files in its Android Compose convention
  fixture.

Of the app's 378 main Kotlin files, 251 import `android.*` or `androidx.*`,
and 115 import `androidx.compose.*`.

### Reproduce the source counts

Run from a clean checkout of the frozen commit:

```bash
modules=(
  fsrs-java
  core
  domain
  sync-domain
  writing-core
  dictionary-core
  update-core
  app
)

printf '%-18s %8s %8s %12s\n' module main test androidTest
for module in "${modules[@]}"; do
  main_count="$(
    git ls-files "${module}/src/main" |
      awk '/\.(kt|java)$/ { count++ } END { print count + 0 }'
  )"
  test_count="$(
    git ls-files "${module}/src/test" |
      awk '/\.(kt|java)$/ { count++ } END { print count + 0 }'
  )"
  android_test_count="$(
    git ls-files "${module}/src/androidTest" |
      awk '/\.(kt|java)$/ { count++ } END { print count + 0 }'
  )"
  printf ':%-17s %8d %8d %12d\n' \
    "${module}" "${main_count}" "${test_count}" "${android_test_count}"
done

app_main_files="$(
  git ls-files app/src/main |
    awk '/\.kt$/ { print }'
)"
android_importers="$(
  printf '%s\n' "${app_main_files}" |
    xargs -r rg -l '^[[:space:]]*import[[:space:]]+(android|androidx)\.' |
    wc -l
)"
compose_importers="$(
  printf '%s\n' "${app_main_files}" |
    xargs -r rg -l '^[[:space:]]*import[[:space:]]+androidx\.compose\.' |
    wc -l
)"
printf 'android_or_androidx_importers=%s compose_importers=%s\n' \
  "${android_importers}" "${compose_importers}"
```

## Gradle and compiler baseline

| Concern | Frozen configuration |
| --- | --- |
| Gradle wrapper | 9.4.1, distribution SHA-256 `2ab2958f2a1e51120c326cad6f385153bb11ee93b3c216c5fccebfdfbb7ec6cb` |
| Gradle runtime Kotlin | 2.3.0 |
| Android Gradle Plugin | 9.1.0 |
| Catalog Kotlin request | 2.0.21 for both `kotlin-jvm` and `kotlin-compose` |
| Effective project compiler | 2.2.10: AGP's built-in app kotlinc and the conflict-resolved KGP/Compose plugin classpath |
| Java toolchain / bytecode target | 17 |
| Android SDK levels | compile 36, target 36, minimum 26 |
| Application identity / local version | `dev.bee.kanjianki`; latest reachable tag `v0.4.232` resolves to 0.4.232 / 4232 (catalog fallback 0.4.33 / 4033) |
| Android Compose graph | BOM 2026.04.01; UI/foundation 1.11.0 and Material3 1.4.0 |
| Build behavior | parallel execution, build cache, and configuration cache enabled |
| Gradle JVM | `-Xmx3072m`, UTF-8 |
| Android resources | non-transitive `R` classes |
| Dependency verification | metadata verification enabled, signatures disabled; normal mode is strict |

The seven library projects apply `kani.kotlin-library-conventions`.
`:app` applies `com.android.application`,
`org.jetbrains.kotlin.plugin.compose`, `kani.release-integrity`, and JaCoCo.
It deliberately uses AGP's built-in Kotlin compiler rather than
`org.jetbrains.kotlin.android`.

The catalog's requested Kotlin version is not the effective compiler version.
AGP 9.1.0 brings Kotlin Gradle Plugin 2.2.10 onto the plugin runtime
classpath, and Gradle resolves the requested 2.0.21 Kotlin JVM and Compose
plugin artifacts to 2.2.10. Goal 166 owns making that coupling explicit.

Useful configuration checks:

```bash
./gradlew --version

./gradlew -p build-logic dependencyInsight \
  --configuration runtimeClasspath \
  --dependency org.jetbrains.kotlin:kotlin-gradle-plugin \
  --no-configuration-cache

./gradlew -p build-logic dependencyInsight \
  --configuration runtimeClasspath \
  --dependency org.jetbrains.kotlin:compose-compiler-gradle-plugin \
  --no-configuration-cache
```

## Deterministic test baseline

The latest successful Goal 164 `ciFast ciQuality` run produced the following
JUnit XML counts. That run preceded only the evidence-document commit, so the
executable tree is identical to the frozen commit.

| Test task | Executed | Failed | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| `build-logic:test` | 11 | 0 | 0 | 0 |
| `:fsrs-java:test` | 14 | 0 | 0 | 0 |
| `:core:test` | 1,329 | 0 | 0 | 0 |
| `:domain:test` | 1 | 0 | 0 | 0 |
| `:sync-domain:test` | 55 | 0 | 0 | 0 |
| `:writing-core:test` | 157 | 0 | 0 | 0 |
| `:dictionary-core:test` | 58 | 0 | 0 | 0 |
| `:update-core:test` | 93 | 0 | 0 | 0 |
| `:app:testDebugUnitTest` | 1,518 | 0 | 0 | 0 |
| **Gradle/JUnit total** | **3,236** | **0** | **0** | **0** |

Fresh runs at the frozen commit added 73 `tools` tests, 94 `scripts/tests`
tests, and 73 `ci/tests` tests. All 240 passed. The deterministic `ciFast`
surface therefore executed **3,476 tests** in aggregate.

The 93 Android instrumentation source files are compiled by `ciFast` but are
not executed by that deterministic gate. Do not add their static `@Test`
count, the nightly suite, or the 62-test live-provider subset to the executed
total unless that exact device run took place.

The full Goal 164 validation command was:

```bash
ANDROID_HOME=/home/bee/.cache/codex-android-sdk \
ANDROID_SDK_ROOT=/home/bee/.cache/codex-android-sdk \
  ./gradlew ciFast ciQuality --no-daemon --console=plain
```

It completed successfully in 12m03s with 110 actionable tasks: 99 executed,
two from cache, and nine up-to-date.

The three Python suites can be reproduced independently:

```bash
python3 -m unittest discover -s tools -p 'test_*.py'
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
python3 -m unittest discover -s ci/tests -p 'test_*.py'
```

## Android UI golden baseline

Goal 165 freezes 63 sanitized Android route/state captures as paired PNG and
normalized-semantics assets. The device contract is 360x640 at 160 dpi, light
theme, and `en-GB`: 19 durable routes are captured at font scales 1.0 and 2.0,
and 25 representative loading/error/empty/data states are captured at 1.0.

The executable comparison gate is deliberately process-isolated. It runs the
44 normal-scale cases as four 11-case instrumentation processes and the 19
accessibility cases as 10- and 9-case processes. The host runner pins display
state, restores the prior device settings on exit, requires clean JUnit and
instrumentation terminal codes, and rejects fatal/UiAutomation errors from
logcat:

```bash
ANDROID_SERIAL=emulator-5554 \
ANDROID_HOME=/home/bee/.cache/codex-android-sdk \
ANDROID_SDK_ROOT=/home/bee/.cache/codex-android-sdk \
  ci/scripts/run_goal165_ui_baselines.sh
```

Recording is an explicit approval operation. `--record` clears only the
app-owned remote candidate directory, stages and validates an exact 63/63
asset set, rolls back the prior local set on failure, repackages the new
assets, and runs the lightweight catalog/alias contracts:

```bash
ANDROID_SERIAL=emulator-5554 \
ANDROID_HOME=/home/bee/.cache/codex-android-sdk \
ANDROID_SDK_ROOT=/home/bee/.cache/codex-android-sdk \
  ci/scripts/run_goal165_ui_baselines.sh --record
```

`ci/scripts/run_device_risk_suite.sh` intentionally executes only the
lightweight `@DeviceRisk` catalog, normalizer, and checked-in asset contracts.
It does not execute the 63-Activity capture method in one instrumentation
process. Route or renderer moves that claim UI preservation must invoke the
dedicated sharded comparison command above. The checked-in set can also be
audited without a device:

```bash
python3 ci/scripts/validate_goal165_ui_baselines.py \
  --root app/src/androidTest/assets/goal165/ui \
  --contract \
    app/src/androidTest/assets/goal165/ui/route-state-catalog.snapshot.txt
```

## Compile timing baseline

### Measurement host

| Property | Value |
| --- | --- |
| Kernel / architecture | Linux 7.1.4-1-cachyos, x86_64 |
| CPU | Intel Core Ultra 7 165U, 14 logical CPUs |
| Memory | 62 GiB |
| JDK | OpenJDK 17.0.19 |
| Gradle | 9.4.1 |
| Android SDK | `/home/bee/.cache/codex-android-sdk` |

The probe used a detached local clone pinned to the frozen commit. It disabled
the Gradle daemon and reusable build cache while retaining the repository's
normal parallel execution and configuration cache. A clean measurement ran
`clean :app:compileDebugKotlin`. Its paired incremental measurement appended
one comment to `MainActivityStats.kt`, ran `:app:compileDebugKotlin` without
`clean`, and immediately removed the comment.

Run 1 includes first-time compilation of the included build and overlapped an
unrelated Gradle client, so it is retained as environment warm-up evidence but
excluded from the declared median. Runs 2-4 are the three measured pairs.
The host was still busy: the one-minute load average at measured starts ranged
from 8.18 to 11.61 on 14 logical CPUs. These are reproducible workstation
observations, not a claim about a pristine benchmark host.
Load cells below are the one-, five-, and fifteen-minute averages from
`/proc/loadavg`.

| Run | Treatment | Clean start load | Clean wall time | Incremental start load | Incremental wall time |
| --- | --- | --- | ---: | --- | ---: |
| 1 | Environment warm-up | 6.80 / 9.53 / 9.95 | 177.772s | 11.04 / 10.42 / 10.23 | 34.912s |
| 2 | Measured | 10.10 / 10.27 / 10.19 | 78.459s | 11.28 / 10.56 / 10.30 | 19.189s |
| 3 | Measured | 11.55 / 10.66 / 10.33 | 70.342s | 8.18 / 9.85 / 10.08 | 18.618s |
| 4 | Measured | 11.36 / 10.40 / 10.25 | 94.870s | 11.61 / 11.52 / 10.72 | 14.814s |
| **Declared median, runs 2-4** |  |  | **78.459s** |  | **18.618s** |

For audit only, the median across all four rows is 86.665s clean and 18.904s
incremental. The source file's SHA-256 returned to
`33c0bf0302526f13ebd82c82a07ba1df8f8cbe5e9b31d008c9c7db1e26085e7a`,
and `git diff --exit-code` passed after the probe.

### Reproduce the compile timings

Run only when no other Kani Gradle client is active, and preserve the load
snapshots with the results:

```bash
baseline_sha=6459eafc91f0430bead139c867012232def11b31
probe_dir="$(mktemp -d /tmp/kani-goal165-timing-XXXXXX)"
git clone --quiet --shared --no-checkout . "${probe_dir}"
git -C "${probe_dir}" checkout --quiet --detach "${baseline_sha}"
target="${probe_dir}/app/src/main/kotlin/dev/bee/kanjianki/MainActivityStats.kt"

for run in 1 2 3 4; do
  printf 'RUN %s CLEAN start_load=%s\n' "${run}" "$(cat /proc/loadavg)"
  TIMEFORMAT="TIMING run=${run} kind=clean wall_s=%R user_s=%U sys_s=%S"
  time env \
    ANDROID_HOME=/home/bee/.cache/codex-android-sdk \
    ANDROID_SDK_ROOT=/home/bee/.cache/codex-android-sdk \
    "${probe_dir}/gradlew" -p "${probe_dir}" \
      clean :app:compileDebugKotlin \
      --no-daemon --no-build-cache --console=plain --quiet

  sed -i '$a// Goal 165 temporary incremental timing probe.' "${target}"
  printf 'RUN %s INCREMENTAL start_load=%s\n' "${run}" "$(cat /proc/loadavg)"
  TIMEFORMAT="TIMING run=${run} kind=incremental wall_s=%R user_s=%U sys_s=%S"
  time env \
    ANDROID_HOME=/home/bee/.cache/codex-android-sdk \
    ANDROID_SDK_ROOT=/home/bee/.cache/codex-android-sdk \
    "${probe_dir}/gradlew" -p "${probe_dir}" \
      :app:compileDebugKotlin \
      --no-daemon --no-build-cache --console=plain --quiet
  sed -i '$d' "${target}"
done

sha256sum "${target}"
git -C "${probe_dir}" diff --exit-code
```

Treat run 1 as the included-build/environment warm-up and take the median of
runs 2-4.

## Android normal-launch startup baseline

### Availability audit and measurement boundary

No adb device was connected during the static baseline pass. The existing
`kanji_anki_api35` AVD data directory points to an API 35 system image that
was not installed in either available SDK. Running:

```bash
KANI_TRACE_PROBE_DIR=/tmp/kani-goal165-startup \
  ci/scripts/capture_kani_trace_probe.sh home
```

therefore produced `capture_status=device_pending` with reason
`no connected adb device`.

That trace command is an availability check, not the production startup
measurement. It launches with `EXTRA_SCREENSHOT_ROUTE`; the button-latency
harness uses `EXTRA_BENCHMARK_ROUTE`. `MainActivityStartup` intentionally
disables normal background startup work for both extras. A conversion baseline
must launch `MainActivity` without either extra.

The app has a ten-line checked-in baseline profile but no Macrobenchmark
module. The measurement below is consequently a coarse `am start -W`
first-frame proxy, not a Macrobenchmark startup metric.

### Device and build metadata

| Property | Value |
| --- | --- |
| AVD | `kani_goal165_api35_20260726` |
| Device model | `sdk_gphone64_x86_64` |
| API level / Android | 35 / Android 15 |
| Build fingerprint | `google/sdk_gphone64_x86_64/emu64xa:15/AE3A.240806.043/12960925:userdebug/dev-keys` |
| ABI | `x86_64` |
| Emulator | 36.6.11, build 15507667 |
| APK | Debug APK from frozen commit `6459eafc`; SHA-256 `c96d43cdbe2fc82cd458e5ee82f1cad121f275d8e2bfe16aba1471486d9284a7` |
| App version | 0.4.232 / 4232 |
| Data state | Deterministic `ButtonLatencyBenchmarkFixtureSeeder` store: 22 dashboard rows, three study items, one sync row |
| Animation scales | Disabled |
| Host load | 8.75 / 15.95 / 13.34 near the start; 8.77 / 15.84 / 13.32 near the end, on 14 logical CPUs |

The frozen seeder populated the three deterministic study items
`[弱, 点, 未]`. Its instrumentation assertion still expected
`[保, 弱, 点]`, so the seed command reported that stale test-only expectation
after the database had been created. The database was inspected directly
before measurement to verify the 22/3/1 row counts above. Goal 165's fixture
commit aligns the debug-only expectation with actual admission without
changing production code. The recorded timing used the frozen APK hash and
the pre-correction seeded database. An exact frozen-commit reproduction should
expect that assertion failure after seeding, verify the database directly,
and continue only when `[弱, 点, 未]` and the 22/3/1 counts match. A
post-correction reproduction must verify the same state before comparing
timings.

### Five-round result

One normal launch is discarded after install and fixture seeding. Each
measured round force-stops the process, waits for process exit, and launches
the normal launcher Activity without a screenshot or benchmark extra. App data
is not cleared between rounds.

| Round | LaunchState | ThisTime (ms) | TotalTime (ms) | WaitTime (ms) |
| --- | --- | ---: | ---: | ---: |
| 1 | COLD | Not reported | 1,395 | 1,401 |
| 2 | COLD | Not reported | 2,314 | 2,333 |
| 3 | COLD | Not reported | 1,931 | 1,949 |
| 4 | COLD | Not reported | 1,852 | 1,855 |
| 5 | COLD | Not reported | 1,797 | 1,809 |
| **Median** | **COLD** | **Not reported** | **1,852** | **1,855** |

`TotalTime` is the primary comparison value. `am start -W` ends at the
Activity launch/first-frame boundary; it does not wait for route-settle or
asynchronous scheduler, asset-warmup, or maintenance completion. Later desktop
startup budgets must not relabel this number as full readiness.
Android 15 did not emit a `ThisTime` field for any of the five launches, so
the table records that absence rather than deriving or copying another field.

### Reproduce the startup measurement

Use a disposable emulator. The fixture command deletes and recreates Kani's
local database:

```bash
ANDROID_HOME=/home/bee/.cache/codex-android-sdk \
ANDROID_SDK_ROOT=/home/bee/.cache/codex-android-sdk \
  ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest \
    --no-daemon --console=plain

adb install -r -d app/build/outputs/apk/debug/app-debug.apk
adb install -r -d \
  app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm clear dev.bee.kanjianki
adb shell pm grant \
  dev.bee.kanjianki android.permission.POST_NOTIFICATIONS
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell am instrument -w -r \
  -e class \
  'dev.bee.kanjianki.ButtonLatencyFixtureInstrumentedTest#seedRepresentativeLocalStoreForButtonLatencyBenchmark' \
  dev.bee.kanjianki.test/androidx.test.runner.AndroidJUnitRunner

startup_probe="$(mktemp -d /tmp/kani-goal165-startup-live-XXXXXX)"
adb shell am force-stop dev.bee.kanjianki
adb shell am start -W \
  -n dev.bee.kanjianki/.MainActivity \
  > "${startup_probe}/warmup-discarded.txt"

for round in 1 2 3 4 5; do
  adb shell am force-stop dev.bee.kanjianki
  while adb shell pidof dev.bee.kanjianki | grep -q .; do
    sleep 0.2
  done
  adb shell am start -W \
    -n dev.bee.kanjianki/.MainActivity |
    tee "${startup_probe}/run-${round}.txt"
done

adb shell getprop ro.product.model
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.fingerprint
adb shell getprop ro.product.cpu.abi
```

Extract the five rows and medians without editing the raw output:

```bash
python3 - "${startup_probe}" <<'PY'
from pathlib import Path
import re
import statistics
import sys

root = Path(sys.argv[1])
fields = ("ThisTime", "TotalTime", "WaitTime")
values = {field: [] for field in fields}

for path in sorted(root.glob("run-*.txt")):
    text = path.read_text(encoding="utf-8")
    state = re.search(r"^LaunchState:\s*(\S+)", text, re.MULTILINE)
    row = {"LaunchState": state.group(1) if state else "UNKNOWN"}
    for field in fields:
        match = re.search(rf"^{field}:\s*(\d+)", text, re.MULTILINE)
        row[field] = int(match.group(1)) if match else None
        if row[field] is not None:
            values[field].append(row[field])
    print(path.name, row)

print(
    "medians",
    {
        field: statistics.median(samples) if samples else None
        for field, samples in values.items()
    },
)
PY
```
