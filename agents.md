# Agent Testing Runbook

This repo is an Android app that syncs against AnkiDroid's flashcard content
provider. Provider bugs can look fixed in unit tests while still failing on a
real AnkiDroid install, so release fixes must include a live AnkiDroid emulator
run when the change touches sync/provider behavior.

## Gradle Command Note

This repo includes a checked-in Gradle wrapper. Run Gradle tasks from the repo
root with `./gradlew`, for example `./gradlew :core:test`.

For Android tasks on this machine, prefer the prepared SDK under `/tmp` when no
`local.properties` file is present:

```sh
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
  ./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac
```

## CI And Static Analysis Notes

The normal PR confidence gate is `./gradlew ciFast`. It runs deterministic JVM
tests, coverage reports/checks, app unit tests, Android instrumentation
compilation, lint, and Python asset tests. Do not use SonarCloud or CodeQL as a
substitute for the normal Android CI workflow.

`./gradlew ciQuality` produces the deterministic bytecode and coverage inputs
used by SonarQube. `./gradlew ciRelease` runs the release confidence gate and
assembles the signed release APK when signing environment variables are set.

SonarCloud and CodeQL both run on pushes to `main` and on internal pull
requests. If you change either workflow, push it and watch the first GitHub
Actions run to completion; local Gradle success alone is not enough to validate
the service integration.

The deterministic AnkiDroid fixture workflow runs on `main`, nightly,
workflow-dispatch, and release. It generates a small sanitized Kiku collection
in CI, installs pinned AnkiDroid in an emulator, grants the real provider
permission, and runs the live-provider sync subset with
`kanjiLiveAnkiDroid=true` and a small `kanjiLiveMinimumNotes` value.

The local real-collection live gate remains stricter. Do not cut a release for
provider/sync changes unless the local copied user-collection AnkiDroid run
passes with the default 7,000-note threshold.

Useful commands:

```sh
gh run list --repo bee-san/kanji_anki --limit 10
gh run watch RUN_ID --repo bee-san/kanji_anki --exit-status
```

The CodeQL workflow uses manual Java/Kotlin analysis for this Android Gradle
project. Keep the CodeQL build step after `github/codeql-action/init` as a
forced clean compile:

```sh
./gradlew clean :fsrs-java:compileJava :core:compileJava :app:compileDebugJavaWithJavac --no-daemon --no-build-cache
```

Do not simplify that to a normal compile. Gradle can mark the compile tasks
up-to-date from cache, and then CodeQL fails with "this run didn't build any of
it" because it saw no compiler activity to extract.

For SonarCloud test assertions, avoid direct `assertFalse(value.equals(...))`
because `java:S5785` asks for `assertNotEquals`. Also avoid `assertNotEquals`
between intentionally different types because `java:S5845` reports it as a bug.
If you need to cover the non-matching `equals` branch, assign the result to a
boolean and assert on that boolean:

```java
Object nonPoint = "not a point";
boolean equalsNonPoint = point.equals(nonPoint);
assertFalse(equalsNonPoint);
```

## Study Scheduler Notes

The study scheduler is centered on a single ladder state machine, not on side
queues. Every persisted study item has exactly one current rung and one phase.

Default ladder order from lowest to highest rung:

1. `write_kanji`
2. `similar_kanji`
3. `type_meaning`
4. `kanji_meaning`
5. `font_meaning`
6. `word_reading`

Users can turn rungs off or move them in Settings. New cards start at
`kanji_meaning`; if that rung is disabled, they start at the nearest enabled
rung, preferring the lower/easier rung when the distance ties. The
`similar_kanji` rung exists only when the app can produce valid similar-kanji
content for that card (the `hasSimilarKanji` predicate is answered by the
`similar_kanji_pairs` table). When the predicate is false, promotion and
demotion cross over that rung without pausing. Settings must keep at least one
always-available rung enabled; `similar_kanji` alone is not enough because it
depends on per-card data.

Phases: `new_learning`, `review`, `relearning`. Learning and relearning follow
Anki semantics:

- `Again` returns to the first step.
- `Good` advances one step; graduates past the last step.
- `Hard` on the first step uses a delay between Again and Good; on later
  steps it repeats the current step.
- `Easy` graduates the card immediately.

Learning and relearning repeats are practice-only. They do not advance
promotion, demotion, or any long-term scheduler threshold. Only persisted
FSRS-due review attempts in the `review` phase count toward ladder movement.
The boundary is the task's persisted FSRS due time, not the calendar day or
any learning-repeat queue.

Ladder movement uses real FSRS due-review evidence, not learning-repeat
practice. A due-review `Hard`, `Good`, or `Easy` promotes the rung only when
the FSRS result schedules the next review strictly more than
`ladder_promotion_interval_days` into the future (default 21 days). A
due-review `Again` increments a consecutive fail streak and demotes the rung
when it reaches `ladder_demotion_fail_streak` (default 3 fails). At
`write_kanji` the demotion floor is reached and further `Again`s keep the
card on that rung. At `word_reading` the promotion ceiling is reached and
further passes keep the card on that rung.

On a due review `Again`, the card enters `relearning` at step 0 if
relearning steps exist. If relearning steps are empty, the card skips
relearning and gets the default post-lapse interval (1 day) per the Anki
manual.

The scheduler core keeps all four ratings (`again`, `hard`, `good`, `easy`).
For ladder-streak counting, `hard`, `good`, and `easy` all count as a pass;
only `again` counts as a fail.

Study UI renders one current rung at a time. Rung rendering:

- `write_kanji` → handwriting pad and writing evaluation.
- `type_meaning` → typed answer box.
- `similar_kanji` → multiple-choice selector from visually similar kanji.
- `kanji_meaning` → standard recognition card.
- `font_meaning` → recognition card with font variation.
- `word_reading` → reading prompt.

The study UI exposes `Pass` and `Fail` labels. In the core scheduler the
wire format stays `good`/`again`/`hard`/`easy`; the UI translates
`Pass` → `good` and `Fail` → `again` at the boundary. The `write_kanji` rung
offers only `Pass` and `Fail`; `Hard` and `Easy` are not shown for that rung.

The study subsystem must never keep a parallel "main study item plus side
task queue" model. `learning_repeats`, `similar_kanji_choice_state`, and
`similar_kanji_repair_queue` are not scheduler queues. The scheduler
consumes `study_items` only. `similar_kanji_pairs` is retained as the data
source for `hasSimilarKanji(row)`, not as a queue.

Legacy field mapping used by the DB v16 migration (fresh start):

- `writing_remediation_pending = 1` → rung `write_kanji`
- `recognition_stage = -1` → rung `type_meaning`
- `recognition_stage = 0` → rung `kanji_meaning` (new-card default)
- `recognition_stage = 1` → rung `font_meaning`
- `recognition_stage = 2` → rung `word_reading`

The `similar_kanji` rung has no legacy source; it is reached through configured
ladder movement when `hasSimilarKanji` is true.

## What Was Tested For v0.3.6

The `_id is unknown` / `queue _id is unknown` fix was validated with:

- Real AnkiDroid `2.24.0` installed in an Android emulator.
- The user's desktop Anki collection copied into the emulator.
- The AnkiDroid provider authority `com.ichi2.anki.flashcards`.
- The app permission `com.ichi2.anki.permission.READ_WRITE_DATABASE` granted to
  `dev.bee.kanjianki`.
- Full Android instrumentation with `kanjiLiveAnkiDroid=true`.
- The actual app button path: tap `Sync AnkiDroid`, confirm
  `Sync and tag archive`, then wait for a successful sync row and non-empty
  dashboard/study queue.
- Local production gate: JVM tests, Android test compilation, lint, and signed
  release APK assembly.
- GitHub Actions release workflow for tag `v0.3.6`.

Passing results from the latest live release testing:

- Live emulator instrumentation: `OK (20 tests)`.
- Local production gate: `BUILD SUCCESSFUL`.
- Release APK metadata: `dev.bee.kanjianki`, versionName `0.3.6`, versionCode
  `3006`.
- Release APK signature: verified with APK Signature Scheme v2.
- GitHub Actions release run: success.

## Live AnkiDroid Emulator Setup

Use a throwaway emulator copy of the user's collection. Do not modify the
desktop collection directly.

1. Start or create an Android emulator with Google APIs available. For the
   previous release, the AVD was `kanji_anki_api35`.

   ```sh
   env ANDROID_SDK_ROOT=/tmp/android-sdk ANDROID_HOME=/tmp/android-sdk \
     /tmp/android-sdk/emulator/emulator \
     -avd kanji_anki_api35 \
     -wipe-data \
     -no-window \
     -no-audio \
     -no-snapshot \
     -gpu swiftshader_indirect
   ```

2. Wait for boot.

   ```sh
   adb wait-for-device
   adb shell getprop sys.boot_completed
   ```

   Continue only when the second command prints `1`.

3. Install real AnkiDroid.

   ```sh
   adb install -r /tmp/ankidroid-2.24.0/variant-abi-AnkiDroid-2.24.0-x86_64.apk
   ```

4. Launch AnkiDroid once so it creates its preferences.

   ```sh
   adb shell monkey -p com.ichi2.anki 1
   ```

5. Copy the live collection into AnkiDroid's app-specific emulator directory.
   Media is not needed for the sync tests because the app reads note/card data
   from the provider, not rendered card media.

   ```sh
   adb shell mkdir -p /storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid/collection.media
   adb push "/home/bee/.local/share/Anki2/User 1/collection.anki2" \
     /storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid/collection.anki2
   ```

   If the provider smoke query returns `No result found` or AnkiDroid logs
   `No write access to AnkiDroid directory`, inspect ownership:

   ```sh
   adb shell ls -ld /storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid
   ```

   After a root/shell push, the copied `AnkiDroid` directory may be owned by
   `shell` instead of the AnkiDroid app uid. Fix the emulator copy before
   continuing:

   ```sh
   adb shell 'chown -R u0_aNNN:ext_data_rw /storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid && chmod -R u+rwX,g+rwX /storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid'
   ```

   Replace `u0_aNNN` with the owner of
   `/storage/emulated/0/Android/data/com.ichi2.anki`.

6. Point AnkiDroid at that copied collection path.

   ```sh
   adb root
   adb shell sed -i \
     s#/storage/emulated/0/AnkiDroid#/storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid# \
     /data/user/0/com.ichi2.anki/shared_prefs/com.ichi2.anki_preferences.xml
   adb shell am force-stop com.ichi2.anki
   adb shell monkey -p com.ichi2.anki 1
   ```

7. Smoke-test the real provider.

   ```sh
   adb shell content query --uri content://com.ichi2.anki.flashcards/models
   ```

   This should return model rows from the copied collection. Avoid huge repeated
   provider dumps after this point.

## Running The Live Tests

Build and install the debug app plus instrumentation APK.

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant dev.bee.kanjianki com.ichi2.anki.permission.READ_WRITE_DATABASE
```

Run the targeted live release gate with the live fixture enabled. This keeps the
slow real-collection run focused on the provider and the foreground sync button
path; unrelated UI tests use the fake provider in the normal local gate.

```sh
adb logcat -c
adb shell am instrument -w \
  -e kanjiLiveAnkiDroid true \
  -e class dev.bee.kanjianki.MainActivityInstrumentedTest#testManualSyncButtonWorksAgainstLiveAnkiDroid,dev.bee.kanjianki.anki.AnkiDroidGatewayProviderInstrumentedTest,dev.bee.kanjianki.anki.RealAnkiDroidLiveProviderInstrumentedTest \
  dev.bee.kanjianki.test/androidx.test.runner.AndroidJUnitRunner
```

Expected result:

```text
OK (10 tests)
```

Important live tests:

- `MainActivityInstrumentedTest.testManualSyncButtonWorksAgainstLiveAnkiDroid`
  taps `Sync AnkiDroid`, confirms `Sync and tag archive`, and verifies a
  successful sync, dashboard rows, and study items.
- `RealAnkiDroidLiveProviderInstrumentedTest` reads the copied Kiku collection
  through the real AnkiDroid provider once and asserts at least 7,000 Kiku
  notes/cards plus real scheduler state.
- `AnkiDroidGatewayProviderInstrumentedTest` uses the fake provider to reject
  explicit `_id` projections, unsupported scheduler projections, and deferred
  cursor-time errors such as `Queue "queue" is unknown`.

If the live test fails with `SQLiteDatabaseLockedException` in a test poller,
retry only after confirming the app did not crash. The test poller should treat
`SQLITE_BUSY` as "sync still committing", not as a product failure.

## Local Production Gate

Before pushing a release tag, run:

```sh
./gradlew ciRelease \
  -PKANI_SIGNING_STORE_FILE=/tmp/kanji_anki_temp_release.jks \
  -PKANI_SIGNING_STORE_PASSWORD=temporary123 \
  -PKANI_SIGNING_KEY_ALIAS=kanji_temp \
  -PKANI_SIGNING_KEY_PASSWORD=temporary123
```

Then verify the generated APK:

```sh
/tmp/android-sdk/build-tools/36.0.0/apksigner verify --verbose \
  app/build/outputs/apk/release/app-release.apk
/tmp/android-sdk/build-tools/36.0.0/aapt dump badging \
  app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk
```

For `v0.3.6`, the local test APK was only a local keystore build. The public APK
must come from the GitHub Actions release workflow, which uses repository
secrets.

## Release Flow

1. Commit only the relevant files.
2. Push the branch.
3. Confirm the release tag is unused.

   ```sh
   git ls-remote --tags origin vX.Y.Z
   ```

4. Create and push the tag.

   ```sh
   git tag -a vX.Y.Z -m "vX.Y.Z"
   git push origin vX.Y.Z
   ```

5. Watch the release workflow.

   ```sh
   gh run list --repo bee-san/kanji_anki --workflow android-release.yml --limit 5
   gh run watch RUN_ID --repo bee-san/kanji_anki --exit-status
   ```

If you create a GitHub Release directly in the UI, publishing that release also
triggers this same workflow automatically.

6. Fetch the release asset URLs.

   ```sh
   gh release view vX.Y.Z --repo bee-san/kanji_anki \
     --json url,tagName,name,assets,isDraft,isPrerelease,publishedAt
   ```

Do not cut a release for provider/sync changes unless the live AnkiDroid
instrumentation suite and local production gate both pass.
