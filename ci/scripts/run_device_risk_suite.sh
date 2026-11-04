#!/usr/bin/env bash

set -euo pipefail

readonly risk_annotation='dev.bee.kanjianki.testing.DeviceRisk'
readonly provider_test_package='dev.bee.kanjianki.provider.ankidroid.test'

./gradlew :provider-ankidroid:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.annotation="${risk_annotation}" \
  --no-daemon

# Both debug hosts expose the same fake AnkiDroid authority. Remove the
# standalone provider host before installing the app test host.
adb uninstall "${provider_test_package}" >/dev/null 2>&1 || true

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.annotation="${risk_annotation}" \
  --no-daemon

./gradlew :app:assembleMinifiedSmoke --no-daemon

readonly smoke_apk='app/build/outputs/apk/minifiedSmoke/app-minifiedSmoke.apk'
readonly smoke_package='dev.bee.kanjianki.smoke'
readonly smoke_activity='dev.bee.kanjianki.host.KaniHostActivity'
readonly smoke_report_dir='app/build/reports/minifiedSmoke'

capture_minified_smoke_diagnostics() {
  mkdir -p "${smoke_report_dir}"
  adb shell ps -A >"${smoke_report_dir}/processes.txt" 2>&1 || true
  adb shell dumpsys activity activities >"${smoke_report_dir}/activities.txt" 2>&1 || true
  adb shell dumpsys activity processes >"${smoke_report_dir}/activity-processes.txt" 2>&1 || true
  adb shell dumpsys activity exit-info "${smoke_package}" >"${smoke_report_dir}/exit-info.txt" 2>&1 || true
  adb shell dumpsys package "${smoke_package}" >"${smoke_report_dir}/package.txt" 2>&1 || true
  adb logcat -b all -d -v threadtime >"${smoke_report_dir}/logcat.txt" 2>&1 || true
}

minified_smoke_activity_is_resumed() {
  grep -E 'topResumedActivity|mResumedActivity|ResumedActivity' "${smoke_report_dir}/activities.txt" \
    | grep -Fq "${smoke_package}/${smoke_activity}"
}

test -s "${smoke_apk}"
adb install -r "${smoke_apk}"
mkdir -p "${smoke_report_dir}"
adb shell am force-stop "${smoke_package}"
adb logcat -b all -c
if ! adb shell am start -W \
  -n "${smoke_package}/${smoke_activity}" \
  | tee "${smoke_report_dir}/activity-start.txt"; then
  capture_minified_smoke_diagnostics
  exit 1
fi
sleep 2
if ! adb shell dumpsys activity activities >"${smoke_report_dir}/activities.txt"; then
  capture_minified_smoke_diagnostics
  exit 1
fi
if adb shell pidof -s "${smoke_package}" \
  | tr -d '\r' >"${smoke_report_dir}/pids.txt" \
  && test -s "${smoke_report_dir}/pids.txt" \
  && minified_smoke_activity_is_resumed; then
  exit 0
fi

capture_minified_smoke_diagnostics
echo "::error::Minified smoke did not remain alive with the host activity resumed"
exit 1
