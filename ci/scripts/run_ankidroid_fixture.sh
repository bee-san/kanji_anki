#!/usr/bin/env bash
set -euo pipefail

ankidroid_apk="${1:?AnkiDroid APK path is required}"
collection_path="${2:?Fixture collection path is required}"
logcat_path="${RUNNER_TEMP:-/tmp}/ankidroid-fixture-logcat.txt"

dump_logcat() {
  adb logcat -d > "${logcat_path}" 2>/dev/null || true
}

trap 'status=$?; if [ "${status}" -ne 0 ]; then dump_logcat; fi; exit "${status}"' EXIT

./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --parallel -Dorg.gradle.parallel=true

adb wait-for-device
adb install -r "${ankidroid_apk}"
adb shell monkey -p com.ichi2.anki 1
sleep 5

adb root || true
adb wait-for-device
adb shell mkdir -p /storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid/collection.media
adb push "${collection_path}" /storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid/collection.anki2
adb shell "owner=\$(stat -c '%u' /storage/emulated/0/Android/data/com.ichi2.anki 2>/dev/null || true); if [ -n \"\$owner\" ]; then chown -R \"\$owner\":ext_data_rw /storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid || true; fi"
adb shell 'chmod -R u+rwX,g+rwX /storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid || true'

adb shell "prefs=/data/user/0/com.ichi2.anki/shared_prefs/com.ichi2.anki_preferences.xml; if [ -f \"\$prefs\" ]; then sed -i 's#/storage/emulated/0/AnkiDroid#/storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid#g' \"\$prefs\"; fi"
adb shell am force-stop com.ichi2.anki
adb shell monkey -p com.ichi2.anki 1
sleep 5

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant dev.bee.kanjianki com.ichi2.anki.permission.READ_WRITE_DATABASE || true
adb shell content query --uri content://com.ichi2.anki.flashcards/models

adb logcat -c
adb shell am instrument -w \
  -e kanjiLiveAnkiDroid true \
  -e kanjiLiveMinimumNotes 2 \
  -e class dev.bee.kanjianki.MainActivityInstrumentedTest#testManualSyncButtonWorksAgainstLiveAnkiDroid,dev.bee.kanjianki.anki.AnkiDroidGatewayProviderInstrumentedTest,dev.bee.kanjianki.anki.RealAnkiDroidLiveProviderInstrumentedTest \
  dev.bee.kanjianki.test/androidx.test.runner.AndroidJUnitRunner
