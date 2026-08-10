# Local AnkiDroid provider and UI automation testing

Kani sync/provider changes need confidence against a real AnkiDroid content
provider because JVM tests and fake providers can miss projection, cursor, and
foreground sync behavior. GitHub Actions runs a deterministic sanitized fixture;
this document shows the local equivalent and the stricter real-collection gate.

## Recommended tooling

Stay with AndroidX instrumentation, Compose UI test, and UIAutomator for this
project.

Reasons:

- The existing live path already needs provider permissions, instrumentation
  arguments, direct database assertions, and UIAutomator for AnkiDroid/system
  surfaces. AndroidX owns all of those without an extra server.
- Compose UI test gives semantic selectors inside the app; UIAutomator covers
  cross-app/provider-permission flows.
- Maestro is useful for black-box happy-path smoke tests, but it would duplicate
  the existing `MainActivityInstrumentedTest` sync button path and would still
  need the AndroidX live-provider assertions for release confidence.
- Appium has higher setup/runtime overhead and no concrete advantage for the
  current provider fixture.

Install Maestro or Appium only if Kani later needs non-engineer-authored
black-box scripts, multi-app visual smoke tests outside instrumentation, or a
cloud device-farm runner that standardizes on those tools.

## One-command sanitized local fixture

The local wrapper mirrors `.github/workflows/android-instrumented.yml`: install
or reuse an API 35 emulator, download pinned AnkiDroid `v2.24.0`, generate the
sanitized Kiku `.anki2` fixture, install Kani debug APKs, grant the provider
permission, probe the real AnkiDroid provider, and run the live subset.

Run from the repository root. The wrapper detects common macOS and Linux
Android SDK locations; set `JAVA_HOME`, `ANDROID_HOME`, and
`ANDROID_SDK_ROOT` first only when the installed Java 17 or SDK is elsewhere.

```sh
# First run only: accepts Android licenses and installs emulator + API 35 image.
KANJI_INSTALL_ANDROID_SDK_PACKAGES=1 ci/scripts/run_local_ankidroid_fixture.sh

# Later runs reuse the AVD and cached AnkiDroid APK.
ci/scripts/run_local_ankidroid_fixture.sh
```

Defaults:

- AVD: `kanji_anki_api35_local`
- System image: `system-images;android-35;google_atd;<host arch>`
- Work/cache directory: `${TMPDIR:-/tmp}/kanji-ankidroid-fixture`
- AnkiDroid release: `v2.24.0`
- Fixture threshold: `KANJI_LIVE_MINIMUM_NOTES=1`. The sanitized Kiku fixture
  contains four notes/cards: one suspended import plus an active weak
  `橋・箸・端` homophone trio that exercises `hasReadingKanji` end to end.
- The live Missing Kanji subset scans the complete fixture, creates and renders
  two notes in the dedicated model, retries them idempotently, and deletes the
  disposable notes. The compatible deck/model remain available for later runs.

Useful overrides:

```sh
KANJI_ANKIDROID_AVD=kanji_anki_api35 \
KANJI_ANKIDROID_WORK_DIR=/tmp/kanji-ankidroid-fixture \
KANJI_ANKIDROID_SYSTEM_IMAGE='system-images;android-35;google_apis;arm64-v8a' \
ci/scripts/run_local_ankidroid_fixture.sh
```

The fixture runs direct provider tests from the standalone
`:provider-ankidroid` test host, uninstalls that host, then runs the foreground
sync test from the app host. To run only the provider host where app UI is not
renderable, use the backward-compatible provider-only override:

```sh
KANJI_LIVE_TEST_CLASSES='dev.bee.kanjianki.anki.AnkiDroidGatewayProviderInstrumentedTest,dev.bee.kanjianki.anki.RealAnkiDroidLiveProviderInstrumentedTest' \
ci/scripts/run_local_ankidroid_fixture.sh
```

For explicit per-host selection, use `KANJI_LIVE_PROVIDER_TEST_CLASSES` and
`KANJI_LIVE_APP_TEST_CLASSES`; an empty value skips that host.

This smoke verifies emulator boot, AnkiDroid install, fixture install, provider
permission, provider probing, and direct provider reads. It is not a replacement
for the full UI sync-button gate on release-risk provider/sync changes.

Expected runtime on a warm machine is roughly 5-10 minutes. First run can take
longer because SDK packages, the system image, Gradle dependencies, and the
AnkiDroid APK may need to download.

## Real user-collection release gate

Use a copied local collection only. Do not upload raw user Anki data to GitHub,
CI artifacts, issue trackers, chat, or third-party services.

```sh
cp "$HOME/Library/Application Support/Anki2/User 1/collection.anki2" \
  /tmp/kani-real-collection.anki2

ci/scripts/run_local_ankidroid_fixture.sh /tmp/kani-real-collection.anki2
```

When a collection path is provided, the local wrapper does not pass
`kanjiLiveMinimumNotes`, so `RealAnkiDroidLiveProviderInstrumentedTest` keeps its
default stricter 7,000-note threshold. Only lower the threshold explicitly for a
smoke run:

```sh
KANJI_LIVE_MINIMUM_NOTES=2 \
ci/scripts/run_local_ankidroid_fixture.sh /tmp/kani-real-collection.anki2
```

Safe policy for real-collection testing:

1. Work on a local copied collection, never the desktop source collection.
2. Do not commit, upload, attach, or paste the raw collection or provider dumps.
3. For release-risk provider/sync changes, require the local real-collection run
   with the default 7,000-note gate before release decisions.
4. Use the sanitized fixture for CI and day-to-day regression coverage.
5. If logs are shared, sanitize note text, model names, deck names, and any
   personally identifying content first.

## Running only the already-booted device path

If an emulator or physical test device is already connected, run the lower-level
fixture script directly:

```sh
python3 ci/scripts/create_ankidroid_kiku_fixture.py /tmp/kiku-provider-fixture.anki2
# Set ANKIDROID_APK or pass a downloaded AnkiDroid v2.24.0 APK path.
bash ci/scripts/run_ankidroid_fixture.sh \
  /tmp/ankidroid-2.24.0/variant-abi-AnkiDroid-2.24.0-x86_64.apk \
  /tmp/kiku-provider-fixture.anki2
```

For real-collection release gating with the lower-level script, explicitly omit
the lowered CI threshold:

```sh
KANJI_LIVE_MINIMUM_NOTES= bash ci/scripts/run_ankidroid_fixture.sh \
  /tmp/ankidroid-2.24.0/AnkiDroid.apk \
  /tmp/kani-real-collection.anki2
```

## Diagnostics and artifacts

The fixture writes diagnostics to `${RUNNER_TEMP:-/tmp}`:

- `ankidroid-fixture-logcat.txt`: dumped automatically when the gate fails.
- `ankidroid-fixture-provider-probe.txt`: output of the provider model probe.
- `ankidroid-fixture-instrumentation.txt`: combined raw instrumentation output.
- `ankidroid-fixture-provider-instrumentation.txt`: provider-host output.
- `ankidroid-fixture-app-instrumentation.txt`: app-host output.

The local wrapper also writes emulator logs under
`${KANJI_ANKIDROID_WORK_DIR:-${TMPDIR:-/tmp}/kanji-ankidroid-fixture}`.

Manual diagnostics:

```sh
adb logcat -d > /tmp/kani-logcat.txt
adb exec-out screencap -p > /tmp/kani-screen.png
adb shell content query --uri content://com.ichi2.anki.flashcards/models \
  > /tmp/kani-provider-models.txt
```

Avoid large repeated provider dumps against real collections. Prefer narrow
queries and sanitize outputs before sharing.

## Current macOS host note

During this task, the macOS host had Android platform-tools/build-tools/platform
36, the emulator package, an API 35 ATD arm64 image, and a booted
`emulator-5554` device available. The provider-only smoke passed locally against
AnkiDroid `v2.24.0` and the sanitized Kiku fixture.

A full UI sync-button run on this host reached the real AnkiDroid provider but
failed because the Kani app rendered a black screen with only the shell/route
accessibility nodes exposed, so UIAutomator could not tap `Sync AnkiDroid` or
`Sync`. Use the provider-only smoke for local fixture plumbing on this host and
keep the full UI sync-button gate in GitHub Actions or another renderable
emulator/device before release-risk provider/sync decisions.
