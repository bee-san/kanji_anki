#!/usr/bin/env bash
set -euo pipefail

readonly TEST_RUNNER="dev.bee.kanjianki.test/androidx.test.runner.AndroidJUnitRunner"
readonly FIXTURE_CLASS="dev.bee.kanjianki.KaniWidgetScreenshotFixtureInstrumentedTest"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly REPO_ROOT
readonly DEFAULT_OUTPUT_DIR="$REPO_ROOT/screenshots/kani-widget-family"
cd "$REPO_ROOT"

usage() {
  printf '%s\n' \
    "Seed: $0 --seed-fixture" \
    "Capture: $0 <capture-id> <provider-component> <app-widget-id>" \
    "" \
    "Capture requires these environment variables:" \
    "  KANI_WIDGET_LAUNCHER_PACKAGE  KANI_WIDGET_LAUNCHER_GRID" \
    "  KANI_WIDGET_APP_THEME" \
    "  KANI_WIDGET_MIN_WIDTH         KANI_WIDGET_MIN_HEIGHT" \
    "  KANI_WIDGET_MAX_WIDTH         KANI_WIDGET_MAX_HEIGHT" \
    "Optional: ANDROID_SERIAL, KANI_WIDGET_OUTPUT_DIR, KANI_WIDGET_OPERATOR"
}

adb_cmd() {
  if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    adb -s "$ANDROID_SERIAL" "$@"
  else
    adb "$@"
  fi
}

if [[ "${1:-}" == "--seed-fixture" ]]; then
  adb_cmd shell am instrument -w \
    -e class "$FIXTURE_CLASS" \
    "$TEST_RUNNER"
  exit 0
fi

if [[ "$#" -ne 3 ]]; then
  usage >&2
  exit 2
fi

readonly capture_id="$1"
readonly provider_component="$2"
readonly app_widget_id="$3"
case "$capture_id" in
  *[!A-Za-z0-9._-]*|'') printf 'Invalid capture id: %s\n' "$capture_id" >&2; exit 2 ;;
esac
case "$app_widget_id" in
  *[!0-9]*|'') printf 'Invalid app widget id: %s\n' "$app_widget_id" >&2; exit 2 ;;
esac

required=(
  KANI_WIDGET_LAUNCHER_PACKAGE KANI_WIDGET_LAUNCHER_GRID
  KANI_WIDGET_APP_THEME
  KANI_WIDGET_MIN_WIDTH KANI_WIDGET_MIN_HEIGHT
  KANI_WIDGET_MAX_WIDTH KANI_WIDGET_MAX_HEIGHT
)
export KANI_WIDGET_FIXTURE_ID="${KANI_WIDGET_FIXTURE_ID:-sanitized-focus-due-history-v1}"
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    printf 'Missing required environment variable: %s\n' "$name" >&2
    exit 2
  fi
done
export "${required[@]}"

readonly output_dir="${KANI_WIDGET_OUTPUT_DIR:-$DEFAULT_OUTPUT_DIR}"
mkdir -p "$output_dir"
readonly png_path="$output_dir/$capture_id.png"
readonly xml_path="$output_dir/$capture_id.uiautomator.xml"
readonly appwidget_path="$output_dir/$capture_id.appwidget.txt"
readonly json_path="$output_dir/$capture_id.json"
readonly remote_xml="/sdcard/${capture_id}.uiautomator.xml"

adb_cmd exec-out screencap -p > "$png_path"
adb_cmd shell uiautomator dump "$remote_xml" >/dev/null
adb_cmd pull "$remote_xml" "$xml_path" >/dev/null
adb_cmd shell rm -f "$remote_xml"
adb_cmd shell dumpsys appwidget > "$appwidget_path"

KANI_CAPTURE_ID="$capture_id"
KANI_CAPTURE_PROVIDER="$provider_component"
KANI_CAPTURE_WIDGET_ID="$app_widget_id"
KANI_CAPTURE_PNG="$(basename "$png_path")"
KANI_CAPTURE_XML="$(basename "$xml_path")"
KANI_CAPTURE_APPWIDGET="$(basename "$appwidget_path")"
KANI_CAPTURE_COMMIT="$(git rev-parse HEAD)"
KANI_CAPTURE_API="$(adb_cmd shell getprop ro.build.version.sdk | tr -d '\r')"
KANI_CAPTURE_DEVICE="$(adb_cmd shell getprop ro.product.model | tr -d '\r')"
KANI_CAPTURE_LOCALE="$(adb_cmd shell getprop persist.sys.locale | tr -d '\r')"
KANI_CAPTURE_FONT_SCALE="$(adb_cmd shell settings get system font_scale | tr -d '\r')"
KANI_CAPTURE_UI_MODE="$(adb_cmd shell cmd uimode night 2>/dev/null | tr -d '\r' || true)"
KANI_CAPTURE_LAUNCHER_VERSION="$(adb_cmd shell dumpsys package "$KANI_WIDGET_LAUNCHER_PACKAGE" | grep -m 1 'versionName=' | cut -d= -f2- | tr -d '\r')"
KANI_CAPTURE_OPERATOR="${KANI_WIDGET_OPERATOR:-hermes-agent}"
KANI_CAPTURE_COMMAND="ci/scripts/capture_kani_widget_screenshots.sh $capture_id $provider_component $app_widget_id"
export KANI_CAPTURE_ID KANI_CAPTURE_PROVIDER KANI_CAPTURE_WIDGET_ID
export KANI_CAPTURE_PNG KANI_CAPTURE_XML KANI_CAPTURE_APPWIDGET KANI_CAPTURE_COMMIT
export KANI_CAPTURE_API KANI_CAPTURE_DEVICE KANI_CAPTURE_LOCALE KANI_CAPTURE_FONT_SCALE
export KANI_CAPTURE_UI_MODE KANI_CAPTURE_LAUNCHER_VERSION KANI_CAPTURE_OPERATOR
export KANI_CAPTURE_COMMAND

python3 - "$json_path" <<'PY'
import datetime
import json
import os
import sys

integer_names = (
    "KANI_WIDGET_MIN_WIDTH",
    "KANI_WIDGET_MIN_HEIGHT",
    "KANI_WIDGET_MAX_WIDTH",
    "KANI_WIDGET_MAX_HEIGHT",
)
values = {name: int(os.environ[name]) for name in integer_names}
manifest = {
    "schema_version": 1,
    "capture_id": os.environ["KANI_CAPTURE_ID"],
    "captured_at_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
    "commit": os.environ["KANI_CAPTURE_COMMIT"],
    "api": int(os.environ["KANI_CAPTURE_API"]),
    "device": os.environ["KANI_CAPTURE_DEVICE"],
    "launcher": {
        "package": os.environ["KANI_WIDGET_LAUNCHER_PACKAGE"],
        "version": os.environ["KANI_CAPTURE_LAUNCHER_VERSION"],
        "grid": os.environ["KANI_WIDGET_LAUNCHER_GRID"],
    },
    "locale": os.environ["KANI_CAPTURE_LOCALE"],
    "font_scale": float(os.environ["KANI_CAPTURE_FONT_SCALE"]),
    "ui_mode": os.environ["KANI_CAPTURE_UI_MODE"],
    "app_theme": os.environ["KANI_WIDGET_APP_THEME"],
    "app_widget_options_dp": {
        "min_width": values["KANI_WIDGET_MIN_WIDTH"],
        "min_height": values["KANI_WIDGET_MIN_HEIGHT"],
        "max_width": values["KANI_WIDGET_MAX_WIDTH"],
        "max_height": values["KANI_WIDGET_MAX_HEIGHT"],
    },
    "fixture_id": os.environ["KANI_WIDGET_FIXTURE_ID"],
    "provider_component": os.environ["KANI_CAPTURE_PROVIDER"],
    "app_widget_id": int(os.environ["KANI_CAPTURE_WIDGET_ID"]),
    "png": os.environ["KANI_CAPTURE_PNG"],
    "uiautomator_xml": os.environ["KANI_CAPTURE_XML"],
    "appwidget_dump": os.environ["KANI_CAPTURE_APPWIDGET"],
    "capture_command": os.environ["KANI_CAPTURE_COMMAND"],
    "operator": os.environ["KANI_CAPTURE_OPERATOR"],
}
with open(sys.argv[1], "w", encoding="utf-8") as handle:
    json.dump(manifest, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
PY

printf 'Captured %s with sidecar %s\n' "$png_path" "$json_path"
