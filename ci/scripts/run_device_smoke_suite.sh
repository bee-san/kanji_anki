#!/usr/bin/env bash

set -euo pipefail

readonly annotation='dev.bee.kanjianki.testing.DeviceSmoke'
readonly provider_test_package='dev.bee.kanjianki.provider.ankidroid.test'

./gradlew :provider-ankidroid:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.annotation="${annotation}" \
  --no-daemon

# Both debug hosts expose the same fake AnkiDroid authority. Remove the
# standalone provider host before installing the app test host.
adb uninstall "${provider_test_package}" >/dev/null 2>&1 || true

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.annotation="${annotation}" \
  --no-daemon
