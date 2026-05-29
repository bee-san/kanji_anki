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

capture_png() {
  local route_name="$1"
  local output_path="${screenshots_dir}/${route_name}.png"
  local remote_path="/sdcard/${route_name}.png"

  adb shell screencap -p "${remote_path}"
  adb pull "${remote_path}" "${output_path}" >/dev/null
  adb shell rm -f "${remote_path}" >/dev/null 2>&1 || true
  printf '%s\n' "${output_path}"
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

log "Installing ${apk_path} (${package_name})"
adb wait-for-device
adb install -r "${apk_path}"

log 'Disabling device animations'
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true

log "Launching ${package_name}"
adb shell am force-stop "${package_name}" || true
adb shell monkey -p "${package_name}" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 5

if [ "${requested_route}" != "all" ] && [ "${requested_route}" != "launcher-home" ]; then
  log "Route '${requested_route}' is not wired to a deterministic deep link yet; capturing launcher-home only."
fi

log 'Capturing launcher-home screenshot'
capture_png launcher-home >/dev/null

export APK_PATH="${apk_path}"
export PACKAGE_NAME="${package_name}"
export REQUESTED_ROUTE="${requested_route}"

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

files = sorted(str(path) for path in screenshots_dir.glob("*.png"))
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
    "routes": ["launcher-home"],
    "files": files,
    "notes": [
        "Initial renderer captures launcher/home only until deterministic screenshot routes are wired."
    ],
}
(screenshots_dir / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY

log "Wrote ${screenshots_dir}/manifest.json"
