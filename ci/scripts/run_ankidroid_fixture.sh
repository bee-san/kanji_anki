#!/usr/bin/env bash
set -euo pipefail

ankidroid_apk="${1:?AnkiDroid APK path is required}"
collection_path="${2:?Fixture collection path is required}"
logcat_path="${RUNNER_TEMP:-/tmp}/ankidroid-fixture-logcat.txt"
instrumentation_output_path="${RUNNER_TEMP:-/tmp}/ankidroid-fixture-instrumentation.txt"
provider_instrumentation_output_path="${RUNNER_TEMP:-/tmp}/ankidroid-fixture-provider-instrumentation.txt"
app_instrumentation_output_path="${RUNNER_TEMP:-/tmp}/ankidroid-fixture-app-instrumentation.txt"
provider_probe_path="${RUNNER_TEMP:-/tmp}/ankidroid-fixture-provider-probe.txt"
ankidroid_dir="/storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid"
legacy_ankidroid_dir="/storage/emulated/0/AnkiDroid"
provider_test_package="dev.bee.kanjianki.provider.ankidroid.test"
app_package="dev.bee.kanjianki"
provider_runner="${provider_test_package}/androidx.test.runner.AndroidJUnitRunner"
app_runner="${app_package}.test/androidx.test.runner.AndroidJUnitRunner"

if [ "${KANJI_LIVE_TEST_CLASSES+x}" = "x" ]; then
  # Backward-compatible provider-only override used by local operators.
  provider_test_classes="${KANJI_LIVE_TEST_CLASSES}"
  app_test_classes=""
else
  provider_test_classes="${KANJI_LIVE_PROVIDER_TEST_CLASSES-dev.bee.kanjianki.anki.AnkiDroidGatewayProviderInstrumentedTest,dev.bee.kanjianki.anki.RealAnkiDroidLiveProviderInstrumentedTest}"
  app_test_classes="${KANJI_LIVE_APP_TEST_CLASSES-dev.bee.kanjianki.host.KaniHostLiveSyncInstrumentedTest#theSyncButtonOnTheThinHostImportsFromLiveAnkiDroid}"
fi

current_instrumentation_name=""
current_instrumentation_output_path=""
current_test_classes=""
current_test_package=""
current_test_runner=""

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

clear_logcat_best_effort() {
  local clear_output
  if ! clear_output="$(adb logcat -c 2>&1)"; then
    echo "Unable to clear logcat; continuing: ${clear_output}" >&2
  fi
}

reconnect_adb() {
  # `adb root` restarts adbd, and GitHub-hosted emulators can drop the client
  # connection mid-command with "Software caused connection abort" or "device
  # offline" for a short window afterwards. Re-establish a live device
  # connection before retrying the affected command so a single transient abort
  # does not fail the whole release.
  adb reconnect >/dev/null 2>&1 || true
  adb wait-for-device
  # Bounded wait for the device to leave the "offline"/"unauthorized" transport
  # state that follows an adbd restart.
  local attempt=1
  while [ "${attempt}" -le 10 ]; do
    if [ "$(adb get-state 2>/dev/null)" = "device" ]; then
      return 0
    fi
    sleep 2
    attempt=$((attempt + 1))
  done
  return 0
}

adb_push_with_reconnect() {
  local src="$1"
  local dest="$2"
  # Ensure the transport is live before pushing; adbd may still be restarting
  # from a preceding `adb root`.
  reconnect_adb
  adb push "${src}" "${dest}"
}

wait_for_external_storage() {
  adb shell "mkdir -p ${ankidroid_dir}/collection.media ${legacy_ankidroid_dir}/collection.media && test -d ${ankidroid_dir} && test -d ${legacy_ankidroid_dir}"
}

wait_for_package_service() {
  # `adb install` can reach Settings.Global and storage allocation after the
  # package binder starts responding. Probe the settings provider too so we do
  # not start an install while system providers are still coming online.
  adb shell cmd package list packages >/dev/null && \
    adb shell settings get global package_verifier_enable >/dev/null
}

install_apk_once() {
  local apk_path="$1"
  adb wait-for-device
  # `sys.boot_completed=1` is not enough on cold GitHub-hosted AVD boots: the
  # package manager service can still be unavailable for a short window, which
  # makes `adb install` fail with Broken pipe / Can't find service: package.
  retry \
    "Android package service readiness" \
    "${KANJI_LIVE_PACKAGE_SERVICE_ATTEMPTS:-12}" \
    "${KANJI_LIVE_PACKAGE_SERVICE_RETRY_DELAY_SECONDS:-5}" \
    wait_for_package_service
  adb install -r "${apk_path}"
}

install_apk() {
  local description="$1"
  local apk_path="$2"
  retry \
    "${description}" \
    "${KANJI_LIVE_APK_INSTALL_ATTEMPTS:-6}" \
    "${KANJI_LIVE_APK_INSTALL_RETRY_DELAY_SECONDS:-10}" \
    install_apk_once "${apk_path}"
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
  grep -Fq 'name=Kiku' "${provider_probe_path}"
}

repair_ankidroid_dir_permissions() {
  # A root push can create the external package directory as root, so it is
  # not a trustworthy source for AnkiDroid's uid. The internal data directory
  # is package-manager-owned even on a brand-new AVD.
  adb shell "mkdir -p ${ankidroid_dir}/collection.media ${legacy_ankidroid_dir}/collection.media && owner_uid=\$(stat -c '%u' /data/user/0/com.ichi2.anki 2>/dev/null || true); if [ -n \"\$owner_uid\" ]; then chown -R \"\$owner_uid\":ext_data_rw ${ankidroid_dir}; chown -R \"\$owner_uid\":media_rw ${legacy_ankidroid_dir} 2>/dev/null || true; fi; chmod -R u+rwX,g+rwX ${ankidroid_dir}; chmod -R u+rwX,g+rwX ${legacy_ankidroid_dir} 2>/dev/null || true; test -w ${ankidroid_dir}"
}

configure_ankidroid_collection_path() {
  # The release fixture is a fresh AnkiDroid install. Make the collection path
  # explicit instead of depending on first-launch preferences already existing:
  # recent API-35 images reject the legacy /storage/emulated/0/AnkiDroid path,
  # so provider probes never see Kiku unless deckPath points at app-private
  # external storage.
  adb shell "prefs_dir=/data/user/0/com.ichi2.anki/shared_prefs; prefs=\${prefs_dir}/com.ichi2.anki_preferences.xml; mkdir -p ${ankidroid_dir}/collection.media \"\${prefs_dir}\"; printf '%s\n' '<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\" ?>' '<map>' '    <string name=\"deckPath\">${ankidroid_dir}</string>' '</map>' > \"\${prefs}\"; owner_uid=\$(stat -c '%u' /data/user/0/com.ichi2.anki 2>/dev/null || true); owner_gid=\$(stat -c '%g' /data/user/0/com.ichi2.anki 2>/dev/null || true); if [ -n \"\${owner_uid}\" ] && [ -n \"\${owner_gid}\" ]; then chown \"\${owner_uid}\":\"\${owner_gid}\" \"\${prefs}\"; fi; chmod 660 \"\${prefs}\""
}

remove_collection_sidecars() {
  adb shell "rm -f ${ankidroid_dir}/collection.anki2-wal ${ankidroid_dir}/collection.anki2-shm ${ankidroid_dir}/collection.anki2-journal ${legacy_ankidroid_dir}/collection.anki2-wal ${legacy_ankidroid_dir}/collection.anki2-shm ${legacy_ankidroid_dir}/collection.anki2-journal"
}

seed_collection_fixture() {
  # AnkiDroid may create a default Basic collection during first launch. If a
  # SQLite WAL/SHM sidecar from that collection is left beside our pushed Kiku
  # fixture, SQLite can replay the stale Basic collection over the replacement
  # database. Force-stop AnkiDroid and delete sidecars before and after pushing.
  adb shell am force-stop com.ichi2.anki || true
  remove_collection_sidecars
  # `adb push` can fail with a transient "Software caused connection abort" when
  # adbd is still settling after `adb root`. Retry with a reconnect between
  # attempts so a single dropped transport does not fail the whole release.
  retry "Kiku fixture push (app-private)" \
    "${KANJI_LIVE_PUSH_ATTEMPTS:-5}" \
    "${KANJI_LIVE_PUSH_RETRY_DELAY_SECONDS:-5}" \
    adb_push_with_reconnect "${collection_path}" "${ankidroid_dir}/collection.anki2"
  # Some AnkiDroid APK variants can still default to the legacy public folder
  # when all-files/legacy storage is available. Seed both plausible locations so
  # the provider reads Kiku regardless of the first-run deckPath decision.
  retry "Kiku fixture push (legacy)" \
    "${KANJI_LIVE_PUSH_ATTEMPTS:-5}" \
    "${KANJI_LIVE_PUSH_RETRY_DELAY_SECONDS:-5}" \
    adb_push_with_reconnect "${collection_path}" "${legacy_ankidroid_dir}/collection.anki2"
  remove_collection_sidecars
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

grant_ankidroid_permission() {
  local package_name="$1"
  adb shell pm grant \
    "${package_name}" \
    com.ichi2.anki.permission.READ_WRITE_DATABASE || true
}

wait_for_ankidroid_provider() {
  # Poll the flashcards provider until it serves the Kiku model. A plain
  # permission-only retry (probe_ankidroid_provider) cannot recover when the
  # first seed/launch did not take - AnkiDroid may have rewritten deckPath,
  # replayed a stale default collection, or not finished first-run provider
  # startup. Escalate every few attempts by re-seeding the fixture and
  # relaunching AnkiDroid so readiness retries can actually fix the state.
  local attempts="${KANJI_LIVE_PROVIDER_READY_ATTEMPTS:-12}"
  local delay_seconds="${KANJI_LIVE_PROVIDER_READY_RETRY_DELAY_SECONDS:-5}"
  local reseed_every="${KANJI_LIVE_PROVIDER_READY_RESEED_EVERY:-4}"
  local attempt=1
  while true; do
    if probe_ankidroid_provider; then
      return 0
    fi

    if [ "${attempt}" -ge "${attempts}" ]; then
      echo "AnkiDroid provider model readiness failed after ${attempts} attempts" >&2
      return 1
    fi

    # Every reseed_every attempts, do a heavier recovery: re-seed the fixture
    # collection, re-point deckPath, and relaunch AnkiDroid before probing again.
    if [ $((attempt % reseed_every)) -eq 0 ]; then
      echo "AnkiDroid provider still not ready after ${attempt} attempts; re-seeding fixture and relaunching" >&2
      seed_collection_fixture || true
      repair_ankidroid_dir_permissions || true
      configure_ankidroid_collection_path || true
      adb shell am force-stop com.ichi2.anki || true
      launch_ankidroid || true
      grant_ankidroid_permission "${provider_test_package}"
      grant_ankidroid_permission "${app_package}"
    fi

    echo "AnkiDroid provider model readiness failed on attempt ${attempt}/${attempts}; retrying in ${delay_seconds}s" >&2
    sleep "${delay_seconds}"
    attempt=$((attempt + 1))
  done
}

run_instrumentation_gate_once() {
  local minimum_notes="${KANJI_LIVE_MINIMUM_NOTES-1}"
  local instrumentation_args=(
    -e kanjiLiveAnkiDroid true
  )
  if [ -n "${minimum_notes}" ]; then
    instrumentation_args+=(
      -e kanjiLiveMinimumNotes "${minimum_notes}"
    )
  fi
  # Cap the import wait below the job's own timeout-minutes. Without this the app gate can
  # wait past the point the harness kills it, and a cancelled job reports the test class name
  # and nothing else -- no assertion, no last-seen sync status, nothing to diagnose.
  instrumentation_args+=(
    -e kanjiLiveImportBudgetSeconds "${KANJI_LIVE_IMPORT_BUDGET_SECONDS-1500}"
  )
  instrumentation_args+=(
    -e class "${current_test_classes}"
    "${current_test_runner}"
  )

  set +e
  adb shell am instrument -w "${instrumentation_args[@]}" \
    | tee "${current_instrumentation_output_path}"
  local adb_status=${PIPESTATUS[0]}
  set -e
  {
    printf '\n=== %s ===\n' "${current_instrumentation_name}"
    cat "${current_instrumentation_output_path}"
  } >> "${instrumentation_output_path}"

  if [ "${adb_status}" -ne 0 ]; then
    echo "${current_instrumentation_name} command failed with exit status ${adb_status}" >&2
    return "${adb_status}"
  fi

  if grep -Eq 'FAILURES!!!|INSTRUMENTATION_RESULT: shortMsg=.|Process crashed' "${current_instrumentation_output_path}"; then
    echo "${current_instrumentation_name} reported a failure; failing the live-provider gate" >&2
    return 1
  fi

  if ! grep -Eq '^OK \([0-9]+ tests?\)' "${current_instrumentation_output_path}"; then
    echo "${current_instrumentation_name} did not report a successful test completion marker" >&2
    return 1
  fi
}

instrumentation_failure_is_transient() {
  grep -Eqi 'Process crashed|INSTRUMENTATION_RESULT: shortMsg=Process crashed|INSTRUMENTATION_CODE: 0|No test results|Test run failed to complete' "${current_instrumentation_output_path}"
}

instrumentation_failure_is_known_fake_provider_classpath_crash() {
  [ -f "${logcat_path}" ] || return 1
  grep -Fq 'FakeAnkiDroidProvider' "${logcat_path}" && \
    grep -Eqi 'Unable to instantiate provider|NoClassDefFoundError|kotlin/jvm/internal/Intrinsics' "${logcat_path}"
}

settle_before_instrumentation() {
  # GitHub-hosted ATD images can keep package, settings, bluetooth, and storage
  # services busy for several minutes after `sys.boot_completed=1`. Starting
  # instrumentation while system_server is saturated can make the target app ANR
  # during process startup. Re-probe install prerequisites and give the device a
  # bounded quiet window before each instrumentation attempt.
  retry "Android install services readiness before instrumentation" 6 5 wait_for_package_service
  sleep "${KANJI_LIVE_INSTRUMENTATION_SETTLE_SECONDS:-20}"
}

reset_apps_after_transient_instrumentation_failure() {
  adb shell am force-stop "${current_test_package}" || true
  adb shell am force-stop com.ichi2.anki || true
  launch_ankidroid || true
  sleep 5
  grant_ankidroid_permission "${current_test_package}"
  retry "AnkiDroid provider model readiness after instrumentation retry reset" 6 5 probe_ankidroid_provider
  adb shell am force-stop com.ichi2.anki || true
}

run_instrumentation_gate() {
  current_instrumentation_name="$1"
  current_test_package="$2"
  current_test_runner="$3"
  current_test_classes="$4"
  current_instrumentation_output_path="$5"
  if [ -z "${current_test_classes}" ]; then
    return 0
  fi

  local attempts="${KANJI_LIVE_INSTRUMENTATION_ATTEMPTS:-4}"
  local attempt=1

  while true; do
    settle_before_instrumentation
    echo "Running ${current_instrumentation_name} attempt ${attempt}/${attempts}"
    if run_instrumentation_gate_once; then
      return 0
    fi

    if [ "${attempt}" -ge "${attempts}" ] || ! instrumentation_failure_is_transient; then
      echo "${current_instrumentation_name} reported a non-retriable failure; failing the live-provider gate" >&2
      return 1
    fi

    dump_logcat
    if instrumentation_failure_is_known_fake_provider_classpath_crash; then
      echo "Instrumentation hit a known Fake AnkiDroid provider classpath crash; not retrying" >&2
      return 1
    fi

    echo "${current_instrumentation_name} hit a transient runner/process failure; resetting apps and retrying" >&2
    reset_apps_after_transient_instrumentation_failure
    clear_logcat_best_effort
    attempt=$((attempt + 1))
  done
}

on_exit() {
  local exit_status=$?
  trap - EXIT
  if [ "${exit_status}" -ne 0 ]; then
    dump_logcat
  fi
  exit "${exit_status}"
}

trap on_exit EXIT

# The test APKs are built before the emulator step (CI) or by the operator
# (local runs) so emulator wall time is spent only on device work.
provider_test_apk="provider-ankidroid/build/outputs/apk/androidTest/debug/provider-ankidroid-debug-androidTest.apk"
app_apk="app/build/outputs/apk/debug/app-debug.apk"
app_test_apk="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
build_tasks=()
if [ -n "${provider_test_classes}" ] && [ ! -f "${provider_test_apk}" ]; then
  build_tasks+=(":provider-ankidroid:assembleDebugAndroidTest")
fi
if [ -n "${app_test_classes}" ] && { [ ! -f "${app_apk}" ] || [ ! -f "${app_test_apk}" ]; }; then
  build_tasks+=(":app:assembleDebug" ":app:assembleDebugAndroidTest")
fi
if [ "${#build_tasks[@]}" -gt 0 ]; then
  ./gradlew "${build_tasks[@]}" --parallel -Dorg.gradle.parallel=true
fi

adb wait-for-device
install_apk "AnkiDroid APK install" "${ankidroid_apk}"
launch_ankidroid
sleep 5

adb root || true
adb wait-for-device
retry "External storage fixture directory readiness" 12 5 wait_for_external_storage
seed_collection_fixture
retry "AnkiDroid fixture directory permissions" 6 2 repair_ankidroid_dir_permissions

configure_ankidroid_collection_path
adb shell am force-stop com.ichi2.anki
launch_ankidroid
sleep 5

: > "${instrumentation_output_path}"

if [ -n "${provider_test_classes}" ]; then
  install_apk "Provider androidTest APK install" "${provider_test_apk}"
  grant_ankidroid_permission "${provider_test_package}"
  wait_for_ankidroid_provider
  adb shell am force-stop com.ichi2.anki || true
  clear_logcat_best_effort
  run_instrumentation_gate \
    "provider instrumentation" \
    "${provider_test_package}" \
    "${provider_runner}" \
    "${provider_test_classes}" \
    "${provider_instrumentation_output_path}"
  adb uninstall "${provider_test_package}" >/dev/null 2>&1 || true
fi

if [ -n "${app_test_classes}" ]; then
  install_apk "Debug app APK install" "${app_apk}"
  install_apk "Debug androidTest APK install" "${app_test_apk}"
  grant_ankidroid_permission "${app_package}"
  wait_for_ankidroid_provider
  adb shell am force-stop com.ichi2.anki || true
  clear_logcat_best_effort
  run_instrumentation_gate \
    "app instrumentation" \
    "${app_package}" \
    "${app_runner}" \
    "${app_test_classes}" \
    "${app_instrumentation_output_path}"
fi
