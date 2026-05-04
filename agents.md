# Agent Testing Runbook

This repo is an Android app that syncs against AnkiDroid's flashcard content
provider. Provider bugs can look fixed in unit tests while still failing on a
real AnkiDroid install, so release fixes must include a live AnkiDroid emulator
run when the change touches sync/provider behavior.

## What Was Tested For v0.3.6

The `_id is unknown` / `queue _id is unknown` fix was validated with:

- Real AnkiDroid `2.24.0` installed in an Android emulator.
- The user's desktop Anki collection copied into the emulator.
- The AnkiDroid provider authority `com.ichi2.anki.flashcards`.
- The app permission `com.ichi2.anki.permission.READ_WRITE_DATABASE` granted to
  `dev.bee.kanjianki`.
- Full Android instrumentation with `kanjiLiveAnkiDroid=true`.
- The actual app button path: tap `Sync AnkiDroid now`, then wait for a
  successful sync row and non-empty dashboard/study queue.
- Local production gate: JVM tests, Android test compilation, lint, and signed
  release APK assembly.
- GitHub Actions release workflow for tag `v0.3.6`.

Passing results from that release:

- Live emulator instrumentation: `OK (19 tests)`.
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
gradle :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant dev.bee.kanjianki com.ichi2.anki.permission.READ_WRITE_DATABASE
```

Run the full instrumentation suite with the live fixture enabled.

```sh
adb logcat -c
adb shell am instrument -w \
  -e kanjiLiveAnkiDroid true \
  dev.bee.kanjianki.test/androidx.test.runner.AndroidJUnitRunner
```

Expected result:

```text
OK (19 tests)
```

Important live tests:

- `MainActivityInstrumentedTest.testManualSyncButtonWorksAgainstLiveAnkiDroid`
  taps the real `Sync AnkiDroid now` button and verifies a successful sync,
  dashboard rows, and study items.
- `RealAnkiDroidLiveProviderInstrumentedTest.manualSyncReadsUserKikuCollectionThroughRealAnkiDroid`
  reads the copied Kiku collection through the real AnkiDroid provider and
  asserts at least 7,000 Kiku notes/cards plus real scheduler state.
- `AnkiDroidGatewayProviderInstrumentedTest` uses the fake provider to reject
  explicit `_id` projections and unsupported scheduler projections.

If the live test fails with `SQLiteDatabaseLockedException` in a test poller,
retry only after confirming the app did not crash. The test poller should treat
`SQLITE_BUSY` as "sync still committing", not as a product failure.

## Local Production Gate

Before pushing a release tag, run:

```sh
gradle :core:test \
  :app:testDebugUnitTest \
  :app:compileDebugAndroidTestJavaWithJavac \
  :app:lintDebug \
  :app:assembleRelease \
  -PKANJI_ANKI_SIGNING_STORE_FILE=/tmp/kanji_anki_temp_release.jks \
  -PKANJI_ANKI_SIGNING_STORE_PASSWORD=temporary123 \
  -PKANJI_ANKI_SIGNING_KEY_ALIAS=kanji_temp \
  -PKANJI_ANKI_SIGNING_KEY_PASSWORD=temporary123
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

6. Fetch the release asset URLs.

   ```sh
   gh release view vX.Y.Z --repo bee-san/kanji_anki \
     --json url,tagName,name,assets,isDraft,isPrerelease,publishedAt
   ```

Do not cut a release for provider/sync changes unless the live AnkiDroid
instrumentation suite and local production gate both pass.
