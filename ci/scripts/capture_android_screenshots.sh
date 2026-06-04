#!/usr/bin/env bash
set -euo pipefail

requested_route="${1:-all}"
screenshots_dir="${SCREENSHOTS_DIR:-screenshots}"
mkdir -p "${screenshots_dir}"

log() {
  printf '[android-screenshots] %s\n' "$*"
}

find_debug_apk() {
  find app/build/outputs/apk -type f -name '*debug*.apk' ! -name '*androidTest*.apk' 2>/dev/null | sort | head -n 1
}

find_aapt() {
  if command -v aapt >/dev/null 2>&1; then
    command -v aapt
    return
  fi

  find "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" -path '*/build-tools/*/aapt' -type f 2>/dev/null | sort -V | tail -n 1
}

require_tool() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf 'Required tool not found on PATH: %s\n' "$1" >&2
    exit 1
  fi
}

require_tool adb
require_tool python3

aapt_path="$(find_aapt)"
if [ -z "${aapt_path}" ]; then
  echo 'Required tool not found: aapt. Ensure Android build-tools are installed.' >&2
  exit 1
fi

apk_path="$(find_debug_apk)"
if [ -z "${apk_path}" ]; then
  echo 'No debug APK found under app/build/outputs/apk. Did the configured Gradle task build one?' >&2
  exit 1
fi

package_name="$("${aapt_path}" dump badging "${apk_path}" | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -n 1)"
if [ -z "${package_name}" ]; then
  echo "Could not derive package name from ${apk_path}" >&2
  exit 1
fi

screen_route_extra="dev.bee.kanjianki.extra.SCREENSHOT_ROUTE"

captured_routes=()
captured_files=()

original_accelerometer_rotation="$(adb shell settings get system accelerometer_rotation 2>/dev/null | tr -d '\r' || true)"
original_user_rotation="$(adb shell settings get system user_rotation 2>/dev/null | tr -d '\r' || true)"

restore_setting() {
  local namespace="$1"
  local key="$2"
  local value="$3"
  if [ -n "${value}" ] && [ "${value}" != "null" ]; then
    adb shell settings put "${namespace}" "${key}" "${value}" >/dev/null 2>&1 || true
  fi
}

cleanup() {
  restore_setting system accelerometer_rotation "${original_accelerometer_rotation}"
  restore_setting system user_rotation "${original_user_rotation}"
  adb shell am force-stop "${package_name}" >/dev/null 2>&1 || true
}

trap cleanup EXIT

capture_png() {
  local route_name="$1"
  local output_path="${screenshots_dir}/${route_name}.png"
  local remote_path="/sdcard/${route_name}.png"

  adb shell screencap -p "${remote_path}"
  adb pull "${remote_path}" "${output_path}" >/dev/null
  adb shell rm -f "${remote_path}" >/dev/null 2>&1 || true
  captured_routes+=("${route_name}")
  captured_files+=("${output_path}")
  printf '%s\n' "${output_path}"
}

dump_ui_xml() {
  local local_xml
  local_xml="$(mktemp "${TMPDIR:-/tmp}/kani-ui.XXXXXX")"
  if ! adb shell uiautomator dump /sdcard/kani-ui.xml >/dev/null 2>&1; then
    rm -f "${local_xml}"
    printf '\n'
    return 0
  fi
  if ! adb pull /sdcard/kani-ui.xml "${local_xml}" >/dev/null 2>&1; then
    rm -f "${local_xml}"
    printf '\n'
    return 0
  fi
  adb shell rm -f /sdcard/kani-ui.xml >/dev/null 2>&1 || true
  printf '%s\n' "${local_xml}"
}

ui_dump_matches() {
  local xml_path="$1"
  shift
  python3 - "$xml_path" "$@" <<'PY'
import pathlib
import sys

xml_path = pathlib.Path(sys.argv[1])
xml = xml_path.read_text(encoding='utf-8', errors='ignore')
lower = xml.lower()
if "isn't responding" in lower or "is not responding" in lower or "aerr_" in lower:
    print("Detected Android ANR dialog in UI dump.", file=sys.stderr)
    for line in xml.splitlines():
        lowered = line.lower()
        if "responding" in lowered or "aerr_" in lowered:
            print(line.strip(), file=sys.stderr)
    sys.exit(2)

expected_terms = [term for term in sys.argv[2:] if term]
if expected_terms and not all(term in xml for term in expected_terms):
    sys.exit(1)

sys.exit(0)
PY
}

wait_for_route() {
  local capture_name="$1"
  shift
  local -a expected_terms=("$@")
  local attempt
  for attempt in $(seq 1 90); do
    local xml_path
    xml_path="$(dump_ui_xml)"
    if [ -n "${xml_path}" ] && [ -f "${xml_path}" ]; then
      local status=0
      if ui_dump_matches "${xml_path}" "${expected_terms[@]}"; then
        rm -f "${xml_path}"
        return 0
      else
        status=$?
      fi
      rm -f "${xml_path}"
      if [ "${status}" -eq 2 ]; then
        echo "Detected an Android ANR/dialog while waiting for ${capture_name}." >&2
        exit 1
      fi
    fi
    sleep 1
  done
  echo "Timed out waiting for ${capture_name} screenshot state." >&2
  exit 1
}

set_orientation() {
  local orientation="$1"
  case "${orientation}" in
    portrait)
      adb shell settings put system accelerometer_rotation 0 >/dev/null 2>&1 || true
      adb shell settings put system user_rotation 0 >/dev/null 2>&1 || true
      ;;
    landscape)
      adb shell settings put system accelerometer_rotation 0 >/dev/null 2>&1 || true
      adb shell settings put system user_rotation 1 >/dev/null 2>&1 || true
      ;;
    *)
      echo "Unsupported orientation '${orientation}'" >&2
      exit 1
      ;;
  esac
}

launch_screenshot_route() {
  local launch_target="$1"
  adb shell am force-stop "${package_name}" >/dev/null 2>&1 || true
  adb shell am start -W -n "${package_name}/.MainActivity" --es "${screen_route_extra}" "${launch_target}" >/dev/null
}

capture_route() {
  local capture_name="$1"
  local launch_target="$2"
  local orientation="$3"
  shift 3
  local -a expected_terms=("$@")

  log "Capturing ${capture_name} (${launch_target}, ${orientation})"
  set_orientation "${orientation}"
  launch_screenshot_route "${launch_target}"
  wait_for_route "${capture_name}" "${expected_terms[@]}"
  capture_png "${capture_name}" >/dev/null
}

log "Installing ${apk_path} (${package_name})"
adb wait-for-device
adb install -r "${apk_path}"

log 'Disabling device animations'
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true

case "${requested_route}" in
  all)
    capture_route home home portrait "Kani route home"
    capture_route study study portrait "Kani route study" "Study"
    capture_route stats stats portrait "Kani route stats" "Stats"
    capture_route settings settings portrait "Kani route settings" "Settings"
    capture_route games games portrait "Games"
    capture_route narrow home portrait "Kani route home"
    capture_route wide home landscape "Kani route home"
    ;;
  launcher-home|home)
    capture_route home home portrait "Kani route home"
    ;;
  study)
    capture_route study study portrait "Kani route study" "Study"
    ;;
  stats)
    capture_route stats stats portrait "Kani route stats" "Stats"
    ;;
  settings)
    capture_route settings settings portrait "Kani route settings" "Settings"
    ;;
  games)
    capture_route games games portrait "Games"
    ;;
  narrow)
    capture_route narrow home portrait "Kani route home"
    ;;
  wide)
    capture_route wide home landscape "Kani route home"
    ;;
  update)
    capture_route update update portrait "Kani route settings" "GitHub updater"
    ;;
  *)
    echo "Unsupported screenshot route '${requested_route}'. Expected one of: all, home, launcher-home, study, stats, settings, games, narrow, wide, update." >&2
    exit 1
    ;;
esac

export APK_PATH="${apk_path}"
export PACKAGE_NAME="${package_name}"
export REQUESTED_ROUTE="${requested_route}"
export CAPTURED_ROUTES_RAW="$(printf '%s\n' "${captured_routes[@]}")"
export CAPTURED_FILES_RAW="$(printf '%s\n' "${captured_files[@]}")"

python3 - <<'PY'
import json
import os
import subprocess
from pathlib import Path

screenshots_dir = Path(os.environ.get("SCREENSHOTS_DIR", "screenshots"))
apk_path = os.environ["APK_PATH"]
package_name = os.environ["PACKAGE_NAME"]
requested_route = os.environ["REQUESTED_ROUTE"]


def run(command):
    try:
        return subprocess.check_output(command, text=True, stderr=subprocess.DEVNULL).strip()
    except Exception:
        return ""

captured_routes = [line for line in os.environ.get("CAPTURED_ROUTES_RAW", "").splitlines() if line]
captured_files = [line for line in os.environ.get("CAPTURED_FILES_RAW", "").splitlines() if line]
if not captured_routes:
    captured_routes = [path.stem for path in sorted(screenshots_dir.glob("*.png"))]
if not captured_files:
    captured_files = [str(path) for path in sorted(screenshots_dir.glob("*.png"))]

manifest = {
    "git_sha": os.environ.get("GITHUB_SHA") or run(["git", "rev-parse", "HEAD"]),
    "git_ref": os.environ.get("GITHUB_REF_NAME") or os.environ.get("GITHUB_REF") or run(["git", "branch", "--show-current"]),
    "workflow_run_id": os.environ.get("GITHUB_RUN_ID", ""),
    "workflow_run_attempt": os.environ.get("GITHUB_RUN_ATTEMPT", ""),
    "package": package_name,
    "apk_path": apk_path,
    "device": {
        "profile": os.environ.get("ANDROID_SCREENSHOT_PROFILE", ""),
        "api_level": os.environ.get("ANDROID_SCREENSHOT_API_LEVEL", ""),
        "manufacturer": run(["adb", "shell", "getprop", "ro.product.manufacturer"]),
        "model": run(["adb", "shell", "getprop", "ro.product.model"]),
        "sdk": run(["adb", "shell", "getprop", "ro.build.version.sdk"]),
    },
    "requested_route": requested_route,
    "routes": captured_routes,
    "files": captured_files,
    "notes": [
        "Route capture waits for the requested screen and fails fast on Android ANR dialogs.",
    ],
}
(screenshots_dir / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY

log "Wrote ${screenshots_dir}/manifest.json"
