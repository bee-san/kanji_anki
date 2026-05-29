#!/usr/bin/env bash
set -euo pipefail

ankidroid_apk="${1:?AnkiDroid APK path is required}"
collection_path="${2:?Fixture collection path is required}"
logcat_path="${RUNNER_TEMP:-/tmp}/ankidroid-fixture-logcat.txt"
instrumentation_output_path="${RUNNER_TEMP:-/tmp}/ankidroid-fixture-instrumentation.txt"
provider_probe_path="${RUNNER_TEMP:-/tmp}/ankidroid-fixture-provider-probe.txt"
ankidroid_dir="/storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid"

retry() {
  local description="$1"
  local attempts="$2"
  local delay_seconds="$3"
  shift 3

  local attempt=1
  while true; do
    if "$@"; then
      return 0
    fi

    if [ "${attempt}" -ge "${attempts}" ]; then
      echo "${description} failed after ${attempts} attempts" >&2
      return 1
    fi

    echo "${description} failed on attempt ${attempt}/${attempts}; retrying in ${delay_seconds}s" >&2
    sleep "${delay_seconds}"
    attempt=$((attempt + 1))
  done
}

dump_logcat() {
  adb logcat -d > "${logcat_path}" 2>/dev/null || true
}

wait_for_external_storage() {
  adb shell "mkdir -p ${ankidroid_dir}/collection.media"
}

probe_ankidroid_provider() {
  adb shell content query --uri content://com.ichi2.anki.flashcards/models \
    | tee "${provider_probe_path}"
  grep -Eq 'Row:|Kiku' "${provider_probe_path}"
}

run_instrumentation_gate() {
  set +e
  adb shell am instrument -w \
    -e kanjiLiveAnkiDroid true \
    -e kanjiLiveMinimumNotes 2 \
    -e class dev.bee.kanjianki.MainActivityInstrumentedTest#testManualSyncButtonWorksAgainstLiveAnkiDroid,dev.bee.kanjianki.anki.AnkiDroidGatewayProviderInstrumentedTest,dev.bee.kanjianki.anki.RealAnkiDroidLiveProviderInstrumentedTest \
    dev.bee.kanjianki.test/androidx.test.runner.AndroidJUnitRunner \
    | tee "${instrumentation_output_path}"
  local adb_status=${PIPESTATUS[0]}
  set -e

  if [ "${adb_status}" -ne 0 ]; then
    echo "Instrumentation command failed with exit status ${adb_status}" >&2
    return "${adb_status}"
  fi

  if grep -Eq 'FAILURES!!!|INSTRUMENTATION_RESULT: shortMsg=.|Process crashed' "${instrumentation_output_path}"; then
    echo "Instrumentation reported a failure; failing the live-provider gate" >&2
    return 1
  fi

  if ! grep -Eq '^OK \([0-9]+ tests?\)' "${instrumentation_output_path}"; then
    echo "Instrumentation did not report a successful test completion marker" >&2
    return 1
  fi
}

trap 'status=$?; if [ "${status}" -ne 0 ]; then dump_logcat; fi; exit "${status}"' EXIT

./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --parallel -Dorg.gradle.parallel=true

adb wait-for-device
adb install -r "${ankidroid_apk}"
adb shell monkey -p com.ichi2.anki 1
sleep 5

adb root || true
adb wait-for-device
retry "External storage fixture directory readiness" 12 5 wait_for_external_storage
adb push "${collection_path}" "${ankidroid_dir}/collection.anki2"
adb shell "owner=\$(stat -c '%u' /storage/emulated/0/Android/data/com.ichi2.anki 2>/dev/null || true); if [ -n \"\$owner\" ]; then chown -R \"\$owner\":ext_data_rw ${ankidroid_dir} || true; fi"
adb shell "chmod -R u+rwX,g+rwX ${ankidroid_dir} || true"

adb shell "prefs=/data/user/0/com.ichi2.anki/shared_prefs/com.ichi2.anki_preferences.xml; if [ -f \"\$prefs\" ]; then sed -i 's#/storage/emulated/0/AnkiDroid#${ankidroid_dir}#g' \"\$prefs\"; fi"
adb shell am force-stop com.ichi2.anki
adb shell monkey -p com.ichi2.anki 1
sleep 5

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant dev.bee.kanjianki com.ichi2.anki.permission.READ_WRITE_DATABASE || true
retry "AnkiDroid provider model readiness" 12 5 probe_ankidroid_provider

adb logcat -c
run_instrumentation_gate
