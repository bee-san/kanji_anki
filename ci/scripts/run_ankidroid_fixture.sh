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
  adb shell "mkdir -p ${ankidroid_dir}/collection.media && test -d ${ankidroid_dir}"
}

probe_ankidroid_provider() {
  # AnkiDroid can recreate or tighten its app-specific external-storage
  # directory during first-run/provider startup. Re-apply the CI fixture
  # permissions immediately before each provider probe so readiness retries fix
  # the storage state instead of repeatedly querying a provider that cannot
  # write to its collection directory.
  repair_ankidroid_dir_permissions || return 1
  adb shell content query --uri content://com.ichi2.anki.flashcards/models \
    | tee "${provider_probe_path}"
  grep -Eq 'Row:|Kiku' "${provider_probe_path}"
}

repair_ankidroid_dir_permissions() {
  adb shell "mkdir -p ${ankidroid_dir}/collection.media && owner_uid=\$(stat -c '%u' /storage/emulated/0/Android/data/com.ichi2.anki 2>/dev/null || true); if [ -n \"\$owner_uid\" ]; then chown -R \"\$owner_uid\":ext_data_rw ${ankidroid_dir}; fi; chmod -R u+rwX,g+rwX ${ankidroid_dir}; test -w ${ankidroid_dir}"
}

launch_ankidroid() {
  local monkey_output
  set +e
  monkey_output="$(adb shell monkey -p com.ichi2.anki 1 2>&1)"
  local monkey_status=$?
  set -e
  printf '%s\n' "${monkey_output}"

  if [ "${monkey_status}" -eq 0 ] && ! grep -Fq 'no activities found' <<< "${monkey_output}"; then
    return 0
  fi

  # Some ATD images / AnkiDroid APK variants return success from monkey while
  # only printing "no activities found" for the MONKEY category. Start
  # AnkiDroid explicitly so first-run preferences and provider state are
  # initialized before probing the flashcards provider.
  adb shell am start -W -n com.ichi2.anki/.IntentHandler
}

run_instrumentation_gate_once() {
  local minimum_notes="${KANJI_LIVE_MINIMUM_NOTES-1}"
  local test_classes="${KANJI_LIVE_TEST_CLASSES:-dev.bee.kanjianki.MainActivityInstrumentedTest#testManualSyncButtonWorksAgainstLiveAnkiDroid,dev.bee.kanjianki.anki.AnkiDroidGatewayProviderInstrumentedTest,dev.bee.kanjianki.anki.RealAnkiDroidLiveProviderInstrumentedTest}"
  local instrumentation_args=(
    -e kanjiLiveAnkiDroid true
  )
  if [ -n "${minimum_notes}" ]; then
    instrumentation_args+=(
      -e kanjiLiveMinimumNotes "${minimum_notes}"
    )
  fi
  instrumentation_args+=(
    -e class "${test_classes}"
    dev.bee.kanjianki.test/androidx.test.runner.AndroidJUnitRunner
  )

  set +e
  adb shell am instrument -w "${instrumentation_args[@]}" \
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

instrumentation_failure_is_transient() {
  grep -Eqi 'Process crashed|INSTRUMENTATION_RESULT: shortMsg=Process crashed|INSTRUMENTATION_CODE: 0|No test results|Test run failed to complete' "${instrumentation_output_path}"
}

instrumentation_failure_is_known_fake_provider_classpath_crash() {
  [ -f "${logcat_path}" ] || return 1
  grep -Fq 'FakeAnkiDroidProvider' "${logcat_path}" && \
    grep -Eqi 'Unable to instantiate provider|NoClassDefFoundError|kotlin/jvm/internal/Intrinsics' "${logcat_path}"
}

reset_apps_after_transient_instrumentation_failure() {
  adb shell am force-stop dev.bee.kanjianki || true
  adb shell am force-stop com.ichi2.anki || true
  launch_ankidroid || true
  sleep 5
  adb shell pm grant dev.bee.kanjianki com.ichi2.anki.permission.READ_WRITE_DATABASE || true
  retry "AnkiDroid provider model readiness after instrumentation retry reset" 6 5 probe_ankidroid_provider
  adb shell am force-stop com.ichi2.anki || true
}

run_instrumentation_gate() {
  local attempts="${KANJI_LIVE_INSTRUMENTATION_ATTEMPTS:-2}"
  local attempt=1

  while true; do
    echo "Running live-provider instrumentation attempt ${attempt}/${attempts}"
    if run_instrumentation_gate_once; then
      return 0
    fi

    if [ "${attempt}" -ge "${attempts}" ] || ! instrumentation_failure_is_transient; then
      echo "Instrumentation reported a non-retriable failure; failing the live-provider gate" >&2
      return 1
    fi

    dump_logcat
    if instrumentation_failure_is_known_fake_provider_classpath_crash; then
      echo "Instrumentation hit a known Fake AnkiDroid provider classpath crash; not retrying" >&2
      return 1
    fi

    echo "Instrumentation appears to have hit a transient runner/process failure; resetting apps and retrying" >&2
    reset_apps_after_transient_instrumentation_failure
    adb logcat -c
    attempt=$((attempt + 1))
  done
}

trap 'status=$?; if [ "${status}" -ne 0 ]; then dump_logcat; fi; exit "${status}"' EXIT

# The debug and androidTest APKs are built before the emulator step (CI) or by
# the operator (local runs) so emulator wall time is spent only on device work.
app_apk="app/build/outputs/apk/debug/app-debug.apk"
test_apk="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
if [ ! -f "${app_apk}" ] || [ ! -f "${test_apk}" ]; then
  ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --parallel -Dorg.gradle.parallel=true
fi

adb wait-for-device
adb install -r "${ankidroid_apk}"
launch_ankidroid
sleep 5

adb root || true
adb wait-for-device
retry "External storage fixture directory readiness" 12 5 wait_for_external_storage
adb push "${collection_path}" "${ankidroid_dir}/collection.anki2"
retry "AnkiDroid fixture directory permissions" 6 2 repair_ankidroid_dir_permissions

adb shell "prefs=/data/user/0/com.ichi2.anki/shared_prefs/com.ichi2.anki_preferences.xml; if [ -f \"\$prefs\" ]; then sed -i 's#/storage/emulated/0/AnkiDroid#${ankidroid_dir}#g' \"\$prefs\"; fi"
adb shell am force-stop com.ichi2.anki
launch_ankidroid
sleep 5

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant dev.bee.kanjianki com.ichi2.anki.permission.READ_WRITE_DATABASE || true
retry "AnkiDroid provider model readiness" 12 5 probe_ankidroid_provider
adb shell am force-stop com.ichi2.anki || true

adb logcat -c
run_instrumentation_gate
