#!/usr/bin/env bash
# Regenerates the README screenshots in docs/screenshots.
#
# Renders every Kani screen with sample data through ReadmeScreenshotsTest on the device named
# by ANDROID_SERIAL (use an emulator: the test installs a debug build), then frames each shot
# as a phone and builds the hero image. Needs adb and ImageMagick 7 (`magick`).
#
#   ANDROID_SERIAL=emulator-5554 tools/readme_screenshots.sh
set -euo pipefail

: "${ANDROID_SERIAL:?Set ANDROID_SERIAL to the emulator to capture on}"
export ANDROID_SERIAL

root="$(cd "$(dirname "$0")/.." && pwd)"
out="$root/docs/screenshots"
raw="$(mktemp -d)"
trap 'rm -rf "$raw"' EXIT

cd "$root"
if [[ -z "${SKIP_BUILD:-}" ]]; then
  ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
fi
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e kaniScreenshots true \
  -e class dev.bee.kanjianki.kani.ui.ReadmeScreenshotsTest \
  dev.bee.kanjianki.test/androidx.test.runner.AndroidJUnitRunner | tee "$raw/run.log"
grep -q '^OK (1 test)' "$raw/run.log"
adb pull /sdcard/Android/data/dev.bee.kanjianki/files/readme-screens "$raw/shots"

# The screens the README uses; each also has a "-dark" twin.
shots=(welcome scan words today blind-spots check check-reading reveal contrast
  hook early sprint-flash sprint-pick sprint-feedback write-empty write done settings)

# frame IN OUT: scale to 540 px wide, round the screen corners, add a bezel and a hairline that
# keeps the phone visible on both light and dark pages.
frame() {
  magick "$1" -resize 540x -alpha set \
    \( +clone -alpha transparent -fill white -draw 'roundrectangle 0,0 %[fx:w-1],%[fx:h-1] 44,44' \) \
    -compose DstIn -composite \
    \( +clone -alpha transparent -bordercolor none -border 14 \
       -fill '#15171f' -draw 'roundrectangle 1,1 %[fx:w-2],%[fx:h-2] 56,56' \
       -fill none -stroke '#5b6070' -strokewidth 2 -draw 'roundrectangle 1,1 %[fx:w-2],%[fx:h-2] 56,56' \) \
    +swap -gravity center -compose Over -composite -strip "$2"
}

mkdir -p "$out"
for shot in "${shots[@]}"; do
  for suffix in "" "-dark"; do
    frame "$raw/shots/$shot$suffix.png" "$out/$shot$suffix.png"
  done
done

# Settings only appears in the AMOLED row.
rm "$out/settings.png"

# Hero: three phones, the middle one in front.
hero() {
  local suffix="$1"
  magick -size 1500x1290 xc:none \
    \( "$out/contrast$suffix.png" -resize 86% \) -gravity West -geometry +40+40 -composite \
    \( "$out/write$suffix.png" -resize 86% \) -gravity East -geometry +40+40 -composite \
    "$out/today$suffix.png" -gravity Center -composite \
    -strip "$out/hero$suffix.png"
}
hero ""
hero "-dark"

if command -v optipng >/dev/null; then optipng -quiet -o2 "$out"/*.png; fi
echo "Wrote $(ls "$out" | wc -l) images to $out"
