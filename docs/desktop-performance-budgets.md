# Desktop Performance Budgets

Goal 203 requires desktop budgets set from **desktop** measurements. Android's
Goal 165 numbers in `docs/desktop-conversion-baseline.md` are regression budgets
for the Android host only; nothing here is derived from them, and nothing here
should be compared against them. A 1,852 ms `am start -W` first frame on an
emulated phone says nothing about a JVM process starting Skia on a workstation.

This document records what is measured, what is not yet measurable, and why the
budgets are the size they are.

## The defect this gate found on its first run

The first measured launch of the packaged image did not produce a timing. It
produced this, out of the composition root:

```
java.lang.NoClassDefFoundError: java/net/http/HttpConnectTimeoutException
  at dev.bee.kanjianki.desktop.DesktopProviderProbe$Companion.forLoopbackEndpoint
  at dev.bee.kanjianki.desktop.DesktopShellScaffoldKt.DesktopShellScaffold
```

`jpackage` builds a minimal `jlink` runtime image. Kani's contained
`java.base java.datatransfer java.xml java.prefs java.desktop java.logging
jdk.crypto.ec` and no `java.net.http` — which is the entire AnkiConnect
transport. The **packaged** application launched, rendered, and then failed the
moment it probed for Anki, while every `:desktop-app:run` and Gradle-run launch
had always worked, because those use the full JDK.

The fix is `KaniDesktopRuntimeModules.REQUIRED`, pinned from
`./gradlew :desktop-app:suggestRuntimeModules` and asserted in
`KaniDesktopIdentityTest`. `java.instrument` and `jdk.unsupported` came from the
same scan; they are needed by the Kotlin/Compose runtime rather than by Kani's
own code, which is why they cannot be reasoned about from Kani's sources.

Two things worth keeping from this: a startup budget measured against anything
other than the real installed image would have reported a healthy startup for
this build, and no unit test at any coverage level could have caught it. That is
why `measure_desktop_startup_budget` imports the smoke runner instead of
launching the image its own way.

## Measurement host

| Property | Value |
| --- | --- |
| Kernel / architecture | Linux 6.12.94-123.192.amzn2023.x86_64, x86_64 |
| CPU | Intel Xeon Platinum 8488C, 16 logical CPUs |
| Memory | 124 GiB |
| Display | Headless; `xvfb-run -a`, Skiko `SOFTWARE_FAST` (the `--smoke-test` Linux default) |
| Bundled runtime | OpenJDK 17.0.19, from the packaged image |
| App version | 0.4.33 |
| Commit | `d174cf4964220b5f8d1bad787a9e642be7d68917` (plus this change) |
| Installed-image `lib/app` manifest SHA-256 | `b633399695565d506895036dbe84054f23695a9535628eb9c2e1f16547264a07` |
| Host load at measurement | 0.89 / 1.31 / 1.58 on 16 logical CPUs |

Software rendering matters to these numbers. This host has no GPU, so the
recorded startup includes a software Skia raster path that a real user's machine
usually does not pay. That direction is the useful one for a ceiling.

## Measured: startup to rendered, and peak memory

One discarded warm-up launch, then three measured launches, medians reported.
The warm-up exists because the first launch on a host pays for cold page and font
caches no later launch pays; reporting it would describe the filesystem.

| Metric | Measured | Budget | Headroom |
| --- | ---: | ---: | ---: |
| Startup to rendered-and-settled, median of 3 | 3.131 s | 30 s | ~9.6x |
| Startup, individual rounds | 3.131 / 3.133 / 3.043 s | — | — |
| Peak resident memory, all launches, whole process tree | 245.5 MiB | 768 MiB | ~3.1x |

A second run of the same image on the same host reported 3.059 s median
(3.059 / 3.075 / 3.051) and 242.0 MiB, so the run-to-run spread here is about
2%. That spread is what makes a tight budget dishonest on a host that is not
this one.

"Rendered-and-settled" is the `--smoke-test` sentinel: the shell composed, three
frames drawn, a 250 ms settle, and the sentinel file written and verified. It is
a stricter boundary than Android's `am start -W`, which ends at the first frame
and waits for no asynchronous work — do not compare the two numbers.

Peak memory is read from `ru_maxrss` for child processes. It is one ceiling
across every launch rather than a per-round figure, because that counter is a
tree-wide high-water mark: it cannot be attributed to a single round, and an
earlier attempt to do so by differencing reported ~0 MiB and would have passed
any regression smaller than the warm-up peak. It includes the Xvfb server, so it
overstates Kani's own footprint. For a ceiling, over-reporting is the safe error.

The figures above were recorded on the Linux host described earlier, so they are
unaffected by the two portability defects found later while getting the Windows
and macOS lanes to run this gate at all.

### Peak memory is not measured identically on every host

| Host | Peak memory | Why |
| --- | --- | --- |
| Linux | Enforced; `ru_maxrss` in kilobytes | The recorded baseline host. |
| macOS | Enforced; `ru_maxrss` in **bytes** | Same counter, different unit. Dividing by 1024 as though it were kilobytes over-reported a real 245 MiB launch as 251,136 MiB, so the macOS lane could only ever breach the 768 MiB budget. `ru_maxrss_per_mib` picks the divisor per host. |
| Windows | **Not enforced** | Python's `resource` module is Unix-only, so there is no tree-wide high-water mark to read. Recorded as `null` with a note, never as `0.0`, and reported as `unmeasured` rather than as a number. |

The startup budget is enforced on all three hosts.

`null` is load-bearing. A zero would compare fine against every budget forever,
which turns a missing measurement into a permanently passing check, and a release
record showing `0.0 MiB` would read later as evidence the image was inside its
memory budget on Windows. It is evidence of nothing on that axis. For the same
reason the exemption is keyed to Windows specifically and re-checked when a
recorded measurement is verified: if "absent" alone bought the exemption, a future
breakage of the read on Linux or macOS would quietly stop enforcing the budget
there instead of failing.

Measuring peak memory on Windows needs a different counter than this gate has
(`GetProcessMemoryInfo` over the process tree, via `psutil` or `ctypes`). It is
left unmeasured rather than approximated.

### Why the budgets are loose

They are ~9.6x and ~3.1x the measured medians, and that is deliberate. This is a
regression gate, not a benchmark. It runs on shared CI runners with fewer cores,
no GPU, and neighbours; a budget tight enough to catch a 10% drift there fails
for reasons that are not Kani's, and a gate that fails for reasons that are not
Kani's gets bumped or deleted rather than believed. A loose budget that stays is
worth more than a tight one that does not.

What these budgets do catch: a missing runtime module, a synchronous provider
call added to startup, a blocking migration on the composition path, an asset
loaded eagerly instead of lazily, a leak that doubles the heap. Those are
order-of-magnitude changes, which is the size of regression worth failing a
build over.

Raise a budget only with a fresh recorded measurement here saying why. Do not
raise one to make a run pass.

### A flake that was a measurement bug, not a tight budget

`StatsPrecomputePerformanceSmokeTest.twoHundredItemForecastStaysWithinJvmBudget`
failed twice in ten SonarQube runs (2026-08-07) while `Android CI` passed the
same commits. Measured on an idle Cloud Desktop it took 3.1s of its 5s budget —
under 2x headroom on a test whose first iteration carries its own JIT
compilation and Robolectric class loading.

The test took the fastest of three timed runs, but with no warmup the first run
could dominate that minimum on a loaded shared runner. Fixed by discarding one
warmup run before measuring. **The 5s budget was not changed** — it was chosen
for a reason, and the correct repair was to make the measurement mean what the
budget assumes, not to move the line until the flake stopped.

`ManualKanjiAdmissionPolicyTest.fiveThousandCandidatesPlanAndMergeWithinRegressionBudget`
had the same defect in a worse form: a *single* cold timed run against a 2s
budget, so the number was mostly JIT compilation of the merge path. It failed
twice on Windows Desktop CI while Linux and macOS passed the same commits, and
passed on re-run with nothing changed. With one warmup discarded it measures
**0.43s** — the cold run was the entire margin. The 2s budget is unchanged.

The general lesson, since two tests had it: a wall-clock budget whose first
iteration is also its only iteration is not measuring the code. Before
suspecting a shared runner, check whether the test warms up.

## Not yet measured, and not estimated

Goal 203 also asks for first sync, a 7,000+ note sync, dashboard load, Study
action latency, chart rendering, and backup/restore. These are recorded as
unmeasured rather than filled in with plausible numbers.

| Metric | Blocked on |
| --- | --- |
| First sync; 7,000+ note sync | Goal 191's live provider. The local Anki instance is read-only for Kani's evidence, and no AnkiConnect endpoint was reachable during this pass (`127.0.0.1:8765` refused). A sync timing needs a throwaway fixture profile, not the user's active one. |
| Dashboard load; Study action latency; chart rendering | Goal 200's remaining composition-root wiring (task #21). The routes render, but a latency number measured before diagnostics, update delivery, and background scheduling are wired would be re-measured the moment they are. |
| Backup/restore | Goal 200 as above, plus a seeded profile of a realistic size; a restore timed against an empty profile measures file creation. |

An invented budget is worse than an absent one: it looks like evidence, it passes
forever, and it silently sets the bar at whatever the guesser imagined. When
Goals 191 and 200 close, measure these on a named host, record them in this
table with their own budgets, and extend
`tools/measure_desktop_startup_budget.py` to enforce them.

## Reproducing

Requires a built installed image and, on a headless Linux host, an X server.

```sh
./gradlew createDesktopDistributable
xvfb-run -a python3 -m tools.measure_desktop_startup_budget \
  --image-root "$PWD/desktop-app/build/compose/binaries/main/app" \
  --json-out /tmp/kani-desktop-baseline.json
```

Run it with no other Gradle client active and record `/proc/loadavg` alongside
the result. In CI it runs as part of `./gradlew ciDesktopPackage`, after the
installed-image smoke gate — there is no point timing an image that does not
render.

On a host without Debian packaging tools, `ciDesktopPackage` stops earlier than
this gate: `:desktop-app:packageDeb` fails with jpackage's
`Error: Invalid or unsupported type: [deb]` when `dpkg-deb` and `fakeroot` are
absent, which was the case on the measurement host above. That is a host gap and
not a Kani defect — the native `.deb` still builds on the workflow's
`ubuntu-24.04` runner. The image, smoke, budget, and package-verification gates
all hang off `createDesktopDistributable` rather than off the native package, so
they can be run directly on such a host:

```sh
xvfb-run -a ./gradlew smokeDesktopInstalledImage measureDesktopStartupBudget \
  verifyDesktopPackage
```
