#!/usr/bin/env bash

set -euo pipefail

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.annotation=dev.bee.kanjianki.testing.DeviceRisk \
  --no-daemon

./gradlew :app:assembleMinifiedSmoke --no-daemon

readonly smoke_apk='app/build/outputs/apk/minifiedSmoke/app-minifiedSmoke.apk'
test -s "${smoke_apk}"
adb install -r "${smoke_apk}"
adb shell am start -W \
  -n dev.bee.kanjianki.smoke/dev.bee.kanjianki.MainActivity
sleep 2
adb shell pidof dev.bee.kanjianki.smoke >/dev/null
