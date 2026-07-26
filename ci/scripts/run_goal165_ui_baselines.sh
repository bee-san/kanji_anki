#!/usr/bin/env bash

set -euo pipefail

readonly repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${repo_root}"

record=false
if [[ "${1:-}" == "--record" ]]; then
  record=true
  shift
fi
if [[ "$#" -ne 0 ]]; then
  echo "usage: $0 [--record]" >&2
  exit 2
fi

adb_command=(adb)
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  adb_command+=(-s "${ANDROID_SERIAL}")
fi

readonly target_package='dev.bee.kanjianki'
readonly test_package='dev.bee.kanjianki.test'
readonly runner='androidx.test.runner.AndroidJUnitRunner'
readonly test_class='dev.bee.kanjianki.baseline.Goal165AndroidRouteBaselineInstrumentedTest'
readonly test_method="${test_class}#capturesOrComparesCatalogShard"
readonly remote_root="/sdcard/Android/data/${target_package}/files/goal165-ui-baselines/goal165/ui"
readonly local_root='app/src/androidTest/assets/goal165/ui'
readonly contract="${local_root}/route-state-catalog.snapshot.txt"
readonly validator='ci/scripts/validate_goal165_ui_baselines.py'
run_tmp=''
record_install_started=false
record_install_complete=false

read_setting() {
  local namespace="$1"
  local name="$2"
  local fallback="$3"
  local value
  value="$("${adb_command[@]}" shell settings get "${namespace}" "${name}" | tr -d '\r')"
  if [[ -z "${value}" || "${value}" == 'null' ]]; then
    value="${fallback}"
  fi
  printf '%s' "${value}"
}

readonly original_font_scale="$(read_setting system font_scale 1.0)"
readonly original_transition_scale="$(read_setting global transition_animation_scale 1)"
readonly original_window_scale="$(read_setting global window_animation_scale 1)"
readonly original_animator_scale="$(read_setting global animator_duration_scale 1)"
readonly original_size="$("${adb_command[@]}" shell wm size | tr -d '\r' | sed -n 's/^Override size: //p')"
readonly original_density="$("${adb_command[@]}" shell wm density | tr -d '\r' | sed -n 's/^Override density: //p')"

restore_device() {
  set +e
  if [[ -n "${run_tmp}" && "${record_install_started}" == true && "${record_install_complete}" != true ]]; then
    mkdir -p "${run_tmp}/failed-record"
    for asset_kind in images semantics; do
      if [[ -d "${local_root}/${asset_kind}" ]]; then
        mv "${local_root}/${asset_kind}" "${run_tmp}/failed-record/${asset_kind}"
      fi
      if [[ -d "${run_tmp}/previous-assets/${asset_kind}" ]]; then
        mv "${run_tmp}/previous-assets/${asset_kind}" "${local_root}/${asset_kind}"
      fi
    done
  fi
  "${adb_command[@]}" shell am force-stop "${target_package}" >/dev/null 2>&1
  "${adb_command[@]}" shell settings put system font_scale "${original_font_scale}" >/dev/null 2>&1
  "${adb_command[@]}" shell settings put global transition_animation_scale "${original_transition_scale}" >/dev/null 2>&1
  "${adb_command[@]}" shell settings put global window_animation_scale "${original_window_scale}" >/dev/null 2>&1
  "${adb_command[@]}" shell settings put global animator_duration_scale "${original_animator_scale}" >/dev/null 2>&1
  if [[ -n "${original_size}" ]]; then
    "${adb_command[@]}" shell wm size "${original_size}" >/dev/null 2>&1
  else
    "${adb_command[@]}" shell wm size reset >/dev/null 2>&1
  fi
  if [[ -n "${original_density}" ]]; then
    "${adb_command[@]}" shell wm density "${original_density}" >/dev/null 2>&1
  else
    "${adb_command[@]}" shell wm density reset >/dev/null 2>&1
  fi
  if [[ -n "${run_tmp}" ]]; then
    rm -rf "${run_tmp}"
  fi
}

wait_for_font_scale() {
  local expected="$1"
  local actual=''
  local attempt
  for attempt in $(seq 1 50); do
    actual="$(read_setting system font_scale '')"
    if [[ "${actual}" == "${expected}" ]]; then
      return 0
    fi
    sleep 0.1
  done
  echo "font scale did not settle: expected=${expected} actual=${actual}" >&2
  return 1
}

assert_clean_instrumentation() {
  local label="$1"
  local output_file="$2"
  local logcat_file="$3"

  if ! grep -Eq '^OK \([0-9]+ tests?\)$' "${output_file}"; then
    echo "${label}: instrumentation did not report a passing JUnit result" >&2
    return 1
  fi
  if ! grep -Fq 'INSTRUMENTATION_CODE: -1' "${output_file}"; then
    echo "${label}: instrumentation did not report its terminal code" >&2
    return 1
  fi
  if grep -Eq 'FAILURES!!!|INSTRUMENTATION_STATUS_CODE: -2|Process crashed' "${output_file}"; then
    echo "${label}: instrumentation reported a failure" >&2
    return 1
  fi
  if grep -Eqi \
    'FATAL EXCEPTION|DeadObjectException|UiAutomation.*(error|exception)|SIGKILL' \
    "${logcat_file}"; then
    echo "${label}: fatal instrumentation or UiAutomation error found in logcat" >&2
    grep -Ein \
      'FATAL EXCEPTION|DeadObjectException|UiAutomation.*(error|exception)|SIGKILL' \
      "${logcat_file}" >&2
    return 1
  fi
}

run_instrumentation() {
  local label="$1"
  shift
  if [[ -z "${run_tmp}" || ! -d "${run_tmp}" ]]; then
    echo "${label}: host diagnostics directory is unavailable: ${run_tmp:-<unset>}" >&2
    return 1
  fi
  local output_file="${run_tmp}/${label}.instrumentation.txt"
  local logcat_file="${run_tmp}/${label}.logcat.txt"

  "${adb_command[@]}" shell am force-stop "${target_package}"
  "${adb_command[@]}" logcat -b all -c
  if ! "${adb_command[@]}" shell am instrument -w -r "$@" \
    "${test_package}/${runner}" | tee "${output_file}"; then
    echo "${label}: adb instrumentation command failed" >&2
    return 1
  fi
  "${adb_command[@]}" logcat -b all -d -v threadtime >"${logcat_file}"
  assert_clean_instrumentation "${label}" "${output_file}" "${logcat_file}"
}

run_shard() {
  local scale="$1"
  local shard_index="$2"
  local shard_count="$3"
  local label="scale-${scale}-shard-${shard_index}-of-${shard_count}"
  local arguments=(
    -e goal165RunBaselines true
    -e goal165FontScale "${scale}"
    -e goal165ShardIndex "${shard_index}"
    -e goal165ShardCount "${shard_count}"
    -e class "${test_method}"
  )
  if [[ "${record}" == true ]]; then
    arguments=(-e goal165RecordBaselines true "${arguments[@]}")
  fi

  "${adb_command[@]}" shell settings put system font_scale "${scale}"
  wait_for_font_scale "${scale}"
  run_instrumentation "${label}" "${arguments[@]}"
}

run_lightweight_contracts() {
  run_instrumentation \
    lightweight-contracts \
    -e class "${test_class}"
}

./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
"${adb_command[@]}" install -r app/build/outputs/apk/debug/app-debug.apk
"${adb_command[@]}" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

run_tmp="$(mktemp -d "${TMPDIR:-/tmp}/kani-goal165-ui.XXXXXX")"
trap restore_device EXIT
trap 'exit 130' INT TERM

"${adb_command[@]}" shell wm size 360x640
"${adb_command[@]}" shell wm density 160
"${adb_command[@]}" shell settings put global transition_animation_scale 0
"${adb_command[@]}" shell settings put global window_animation_scale 0
"${adb_command[@]}" shell settings put global animator_duration_scale 0

if [[ "${record}" == true ]]; then
  "${adb_command[@]}" shell rm -rf \
    "/sdcard/Android/data/${target_package}/files/goal165-ui-baselines"
fi

for shard_index in 0 1 2 3; do
  run_shard 1.0 "${shard_index}" 4 || exit 1
done
for shard_index in 0 1; do
  run_shard 2.0 "${shard_index}" 2 || exit 1
done

if [[ "${record}" == true ]]; then
  readonly staged_root="${run_tmp}/recorded-assets"
  mkdir -p "${staged_root}"
  "${adb_command[@]}" pull "${remote_root}/images" "${staged_root}/"
  "${adb_command[@]}" pull "${remote_root}/semantics" "${staged_root}/"
  python3 "${validator}" --root "${staged_root}" --contract "${contract}" || exit 1

  mkdir -p "${run_tmp}/previous-assets" "${local_root}"
  record_install_started=true
  for asset_kind in images semantics; do
    if [[ -d "${local_root}/${asset_kind}" ]]; then
      mv "${local_root}/${asset_kind}" "${run_tmp}/previous-assets/${asset_kind}"
    fi
    mv "${staged_root}/${asset_kind}" "${local_root}/${asset_kind}"
  done
  python3 "${validator}" --root "${local_root}" --contract "${contract}" || exit 1

  # Repackage the just-recorded assets before running the executable contract
  # and alias checks against the installed instrumentation APK.
  ./gradlew :app:assembleDebugAndroidTest
  "${adb_command[@]}" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  run_lightweight_contracts || exit 1
  record_install_complete=true
else
  run_lightweight_contracts || exit 1
fi

mode='comparison'
if [[ "${record}" == true ]]; then
  mode='record'
fi
echo "Goal 165 UI baseline ${mode} gate passed: 63 cases in six shards."
