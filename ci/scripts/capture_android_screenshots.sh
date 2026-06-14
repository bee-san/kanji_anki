#!/usr/bin/env bash
set -euo pipefail

requested_route="${1:-all}"
requested_theme="${2:-}"
requested_locale="${SCREENSHOT_LOCALE:-}"
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

  local sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [ -z "${sdk_root}" ]; then
    return 0
  fi

  find "${sdk_root}" -path '*/build-tools/*/aapt' -type f 2>/dev/null | sort -V | tail -n 1
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
screen_theme_extra="dev.bee.kanjianki.extra.SCREENSHOT_THEME"
screen_locale_extra="dev.bee.kanjianki.extra.SCREENSHOT_LOCALE"
screen_scroll_position_extra="dev.bee.kanjianki.extra.SCREENSHOT_SCROLL_POSITION"
screen_scroll_y_extra="dev.bee.kanjianki.extra.SCREENSHOT_SCROLL_Y"

captured_routes=()
captured_files=()
captured_orientations=()
captured_launch_targets=()
captured_scroll_positions=()
captured_scroll_ys=()
captured_themes=()
captured_theme_choices=()
captured_uiautomator_dumps=()
captured_system_modes=()

original_accelerometer_rotation="$(adb shell settings get system accelerometer_rotation 2>/dev/null | tr -d '\r' || true)"
original_user_rotation="$(adb shell settings get system user_rotation 2>/dev/null | tr -d '\r' || true)"
original_ui_night_mode="$(adb shell settings get secure ui_night_mode 2>/dev/null | tr -d '\r' || true)"

restore_setting() {
  local namespace="$1"
  local key="$2"
  local value="$3"
  if [ -n "${value}" ] && [ "${value}" != "null" ]; then
    adb shell settings put "${namespace}" "${key}" "${value}" >/dev/null 2>&1 || true
  fi
}

resolve_theme_choice() {
  local theme_label
  theme_label="$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')"
  case "${theme_label}" in
    "")
      printf '\n'
      ;;
    girlypop|light|dark|system|autumn)
      printf '%s\n' "${theme_label}"
      ;;
    system-light|system-dark)
      printf 'system\n'
      ;;
    *)
      echo "Unsupported screenshot theme '${1}'. Expected girlypop, light, dark, system, system-light, system-dark, or autumn." >&2
      return 1
      ;;
  esac
}

resolve_theme_system_mode() {
  local theme_label
  theme_label="$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')"
  case "${theme_label}" in
    system-light)
      printf 'light\n'
      ;;
    system-dark)
      printf 'dark\n'
      ;;
    *)
      printf '\n'
      ;;
  esac
}

set_system_night_mode() {
  local mode="$1"
  case "${mode}" in
    light)
      adb shell cmd uimode night no >/dev/null 2>&1 || adb shell settings put secure ui_night_mode 1 >/dev/null 2>&1 || true
      ;;
    dark)
      adb shell cmd uimode night yes >/dev/null 2>&1 || adb shell settings put secure ui_night_mode 2 >/dev/null 2>&1 || true
      ;;
    *)
      ;;
  esac
}

cleanup() {
  restore_setting system accelerometer_rotation "${original_accelerometer_rotation}"
  restore_setting system user_rotation "${original_user_rotation}"
  restore_setting secure ui_night_mode "${original_ui_night_mode}"
  adb shell am force-stop "${package_name}" >/dev/null 2>&1 || true
}

trap cleanup EXIT

capture_theme_label=""
capture_theme_choice=""
capture_theme_system_mode=""
if [ -n "${requested_theme}" ]; then
  capture_theme_label="${requested_theme}"
  capture_theme_choice="$(resolve_theme_choice "${requested_theme}")"
  capture_theme_system_mode="$(resolve_theme_system_mode "${requested_theme}")"
fi

capture_png() {
  local capture_name="$1"
  local output_path="${screenshots_dir}/${capture_name}.png"
  local remote_path="/sdcard/${capture_name}.png"

  adb shell screencap -p "${remote_path}"
  adb pull "${remote_path}" "${output_path}" >/dev/null
  adb shell rm -f "${remote_path}" >/dev/null 2>&1 || true
  printf '%s\n' "${capture_name}.png"
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

capture_ui_xml() {
  local capture_name="$1"
  local output_path="${screenshots_dir}/${capture_name}.uiautomator.xml"
  local local_xml
  local_xml="$(dump_ui_xml)"
  if [ -z "${local_xml}" ] || [ ! -f "${local_xml}" ]; then
    printf '\n'
    return 0
  fi
  if mv "${local_xml}" "${output_path}" >/dev/null 2>&1; then
    printf '%s\n' "${capture_name}.uiautomator.xml"
    return 0
  fi
  rm -f "${local_xml}"
  printf '\n'
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

logical_screen_height() {
  local orientation="$1"
  local wm_size
  wm_size="$(adb shell wm size 2>/dev/null | tr -d '\r' | awk '/Physical size:/ {print $3; exit}')"
  if [ -z "${wm_size}" ]; then
    echo "Unable to determine physical screen size." >&2
    exit 1
  fi
  local width="${wm_size%x*}"
  local height="${wm_size#*x}"
  case "${orientation}" in
    portrait)
      if [ "${height}" -ge "${width}" ]; then
        printf '%s\n' "${height}"
      else
        printf '%s\n' "${width}"
      fi
      ;;
    landscape)
      if [ "${height}" -le "${width}" ]; then
        printf '%s\n' "${height}"
      else
        printf '%s\n' "${width}"
      fi
      ;;
    *)
      echo "Unsupported orientation '${orientation}'" >&2
      exit 1
      ;;
  esac
}

scroll_y_for_position() {
  local orientation="$1"
  local position="$2"
  local screen_height
  screen_height="$(logical_screen_height "${orientation}")"
  case "${position}" in
    top)
      printf '0\n'
      ;;
    middle)
      printf '%s\n' "${screen_height}"
      ;;
    bottom)
      printf '%s\n' "$((screen_height * 8))"
      ;;
    *)
      if [[ "${position}" =~ ^-?[0-9]+$ ]]; then
        printf '%s\n' "${position}"
      else
        echo "Unsupported scroll position '${position}'" >&2
        exit 1
      fi
      ;;
  esac
}

settings_bottom_scroll_extra=288

launch_screenshot_route() {
  local launch_target="$1"
  local theme_choice="$2"
  local scroll_position="$3"
  local scroll_y="$4"
  adb shell am force-stop "${package_name}" >/dev/null 2>&1 || true
  local -a start_args=(am start -W -n "${package_name}/.MainActivity" --es "${screen_route_extra}" "${launch_target}")
  if [ -n "${theme_choice}" ]; then
    start_args+=(--es "${screen_theme_extra}" "${theme_choice}")
  fi
  if [ -n "${requested_locale}" ]; then
    start_args+=(--es "${screen_locale_extra}" "${requested_locale}")
  fi
  if [ -n "${scroll_position}" ]; then
    start_args+=(--es "${screen_scroll_position_extra}" "${scroll_position}")
  fi
  if [ -n "${scroll_y}" ]; then
    start_args+=(--ei "${screen_scroll_y_extra}" "${scroll_y}")
  fi
  adb shell "${start_args[@]}" >/dev/null
}

capture_route_variant() {
  local route_name="$1"
  local capture_name="$2"
  local launch_target="$3"
  local orientation="$4"
  local scroll_position="$5"
  shift 5
  local -a expected_terms=("$@")
  local scroll_y
  local output_path
  scroll_y="$(scroll_y_for_position "${orientation}" "${scroll_position}")"
  if [[ "${route_name}" == "settings" && "${scroll_position}" == "bottom" ]]; then
    scroll_y="$((scroll_y + settings_bottom_scroll_extra))"
  fi
  log "Capturing ${capture_name} (${launch_target}, ${orientation}, ${scroll_position} @ ${scroll_y})"
  set_orientation "${orientation}"
  launch_screenshot_route "${launch_target}" "${capture_theme_choice}" "${scroll_position}" "${scroll_y}"
  wait_for_route "${capture_name}" "${expected_terms[@]}"
  output_path="$(capture_png "${capture_name}")"
  ui_dump_path="$(capture_ui_xml "${capture_name}")"
  captured_routes+=("${route_name}")
  captured_files+=("${output_path}")
  captured_orientations+=("${orientation}")
  captured_launch_targets+=("${launch_target}")
  captured_scroll_positions+=("${scroll_position}")
  captured_scroll_ys+=("${scroll_y}")
  captured_themes+=("${capture_theme_label}")
  captured_theme_choices+=("${capture_theme_choice}")
  captured_uiautomator_dumps+=("${ui_dump_path}")
  captured_system_modes+=("${capture_theme_system_mode}")
}

capture_route_triplet() {
  local route_name="$1"
  local launch_target="$2"
  local orientation="$3"
  shift 3
  local -a expected_terms=("$@")

  capture_route_variant "${route_name}" "${route_name}-top" "${launch_target}" "${orientation}" "top" "${expected_terms[@]}"
  capture_route_variant "${route_name}" "${route_name}-middle" "${launch_target}" "${orientation}" "middle" "${expected_terms[@]}"
  capture_route_variant "${route_name}" "${route_name}-bottom" "${launch_target}" "${orientation}" "bottom" "${expected_terms[@]}"
}

log "Installing ${apk_path} (${package_name})"
adb wait-for-device
adb install -r "${apk_path}"

log 'Disabling device animations'
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true

if [ -n "${capture_theme_system_mode}" ]; then
  log "Setting device night mode to ${capture_theme_system_mode} for screenshot theme ${capture_theme_label}"
  set_system_night_mode "${capture_theme_system_mode}"
fi

stats_label="Stats"
requested_locale_lower="$(printf '%s' "${requested_locale}" | tr '[:upper:]' '[:lower:]')"
if [[ "${requested_locale_lower}" == ja* ]]; then
  stats_label="統計"
fi
if [ -n "${requested_locale}" ]; then
  log "Using screenshot locale ${requested_locale}"
fi

case "${requested_route}" in
  all)
    capture_route_triplet home home portrait "Kani route home"
    capture_route_triplet study study portrait "Kani route study" "Study"
    capture_route_triplet stats stats portrait "Kani route stats" "${stats_label}"
    capture_route_triplet settings settings portrait "Kani route settings" "Settings"
    capture_route_triplet games games portrait "Games"
    capture_route_triplet narrow home portrait "Kani route home"
    capture_route_triplet wide home landscape "Kani route home"
    ;;
  launcher-home|home)
    capture_route_triplet home home portrait "Kani route home"
    ;;
  study)
    capture_route_triplet study study portrait "Kani route study" "Study"
    ;;
  stats)
    capture_route_triplet stats stats portrait "Kani route stats" "${stats_label}"
    ;;
  settings)
    capture_route_triplet settings settings portrait "Kani route settings" "Settings"
    ;;
  games)
    capture_route_triplet games games portrait "Games"
    ;;
  narrow)
    capture_route_triplet narrow home portrait "Kani route home"
    ;;
  wide)
    capture_route_triplet wide home landscape "Kani route home"
    ;;
  update)
    capture_route_triplet update update portrait "Kani route settings" "GitHub updater"
    ;;
  *)
    echo "Unsupported screenshot route '${requested_route}'. Expected one of: all, home, launcher-home, study, stats, settings, games, narrow, wide, update." >&2
    exit 1
    ;;
esac

export APK_PATH="${apk_path}"
export PACKAGE_NAME="${package_name}"
export REQUESTED_ROUTE="${requested_route}"
export REQUESTED_THEME="${requested_theme}"
export REQUESTED_LOCALE="${requested_locale}"
export REQUESTED_THEME_CHOICE="${capture_theme_choice}"
export REQUESTED_SYSTEM_MODE="${capture_theme_system_mode}"
export CAPTURED_ROUTES_RAW="$(printf '%s\n' "${captured_routes[@]}")"
export CAPTURED_FILES_RAW="$(printf '%s\n' "${captured_files[@]}")"
export CAPTURED_ORIENTATIONS_RAW="$(printf '%s\n' "${captured_orientations[@]}")"
export CAPTURED_LAUNCH_TARGETS_RAW="$(printf '%s\n' "${captured_launch_targets[@]}")"
export CAPTURED_SCROLL_POSITIONS_RAW="$(printf '%s\n' "${captured_scroll_positions[@]}")"
export CAPTURED_SCROLL_YS_RAW="$(printf '%s\n' "${captured_scroll_ys[@]}")"
export CAPTURED_THEMES_RAW="$(printf '%s\n' "${captured_themes[@]}")"
export CAPTURED_THEME_CHOICES_RAW="$(printf '%s\n' "${captured_theme_choices[@]}")"
export CAPTURED_UIAUTOMATOR_DUMPS_RAW="$(printf '%s\n' "${captured_uiautomator_dumps[@]}")"
export CAPTURED_SYSTEM_MODES_RAW="$(printf '%s\n' "${captured_system_modes[@]}")"
export CAPTURE_SCRIPT_PATH="${0}"

python3 - <<'PY'
import hashlib
import json
import os
import subprocess
from pathlib import Path
from datetime import datetime, timezone

screenshots_dir = Path(os.environ.get("SCREENSHOTS_DIR", "screenshots"))
apk_path = os.environ["APK_PATH"]
package_name = os.environ["PACKAGE_NAME"]
requested_route = os.environ["REQUESTED_ROUTE"]
requested_theme = os.environ.get("REQUESTED_THEME", "")
requested_locale = os.environ.get("REQUESTED_LOCALE", "")
capture_script_path = os.environ.get("CAPTURE_SCRIPT_PATH", "ci/scripts/capture_android_screenshots.sh")


def run(command):
    try:
        return subprocess.check_output(command, text=True, stderr=subprocess.DEVNULL).strip()
    except Exception:
        return ""

captured_routes = [line for line in os.environ.get("CAPTURED_ROUTES_RAW", "").splitlines() if line]
captured_files = [line for line in os.environ.get("CAPTURED_FILES_RAW", "").splitlines() if line]
captured_orientations = [line for line in os.environ.get("CAPTURED_ORIENTATIONS_RAW", "").splitlines() if line]
captured_launch_targets = [line for line in os.environ.get("CAPTURED_LAUNCH_TARGETS_RAW", "").splitlines() if line]
captured_scroll_positions = [line for line in os.environ.get("CAPTURED_SCROLL_POSITIONS_RAW", "").splitlines() if line]
captured_scroll_ys = [line for line in os.environ.get("CAPTURED_SCROLL_YS_RAW", "").splitlines() if line]
captured_themes = [line for line in os.environ.get("CAPTURED_THEMES_RAW", "").splitlines() if line]
captured_theme_choices = [line for line in os.environ.get("CAPTURED_THEME_CHOICES_RAW", "").splitlines() if line]
captured_uiautomator_dumps = [line for line in os.environ.get("CAPTURED_UIAUTOMATOR_DUMPS_RAW", "").splitlines() if line]
captured_system_modes = [line for line in os.environ.get("CAPTURED_SYSTEM_MODES_RAW", "").splitlines() if line]
if not captured_routes:
    captured_routes = [path.stem for path in sorted(screenshots_dir.glob("*.png"))]
if not captured_files:
    captured_files = [str(path) for path in sorted(screenshots_dir.glob("*.png"))]
if not captured_orientations:
    captured_orientations = [""] * len(captured_routes)
if not captured_launch_targets:
    captured_launch_targets = [""] * len(captured_routes)
if not captured_scroll_positions:
    captured_scroll_positions = [""] * len(captured_routes)
if not captured_scroll_ys:
    captured_scroll_ys = [""] * len(captured_routes)
if not captured_themes:
    captured_themes = [os.environ.get("REQUESTED_THEME", "")] * len(captured_routes)
if not captured_theme_choices:
    captured_theme_choices = [os.environ.get("REQUESTED_THEME_CHOICE", "")] * len(captured_routes)
if not captured_uiautomator_dumps:
    captured_uiautomator_dumps = [""] * len(captured_routes)
if not captured_system_modes:
    captured_system_modes = [os.environ.get("REQUESTED_SYSTEM_MODE", "")] * len(captured_routes)


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def unique_routes(values):
    routes = []
    seen = set()
    for value in values:
        if value and value not in seen:
            seen.add(value)
            routes.append(value)
    return routes


if len(captured_routes) != len(captured_files):
    raise SystemExit("Captured screenshot routes and files are out of sync.")

capture_count = len(captured_routes)
captures = []
for index in range(capture_count):
    route = captured_routes[index]
    file_name = captured_files[index]
    orientation = captured_orientations[index] if index < len(captured_orientations) else ""
    launch_target = captured_launch_targets[index] if index < len(captured_launch_targets) else ""
    scroll_position = captured_scroll_positions[index] if index < len(captured_scroll_positions) else ""
    raw_scroll_y = captured_scroll_ys[index] if index < len(captured_scroll_ys) else ""
    theme_label = captured_themes[index] if index < len(captured_themes) else ""
    theme_choice = captured_theme_choices[index] if index < len(captured_theme_choices) else ""
    system_mode = captured_system_modes[index] if index < len(captured_system_modes) else ""
    ui_dump_file = captured_uiautomator_dumps[index] if index < len(captured_uiautomator_dumps) else ""
    file_path = Path(file_name) if file_name else None
    ui_dump_path = Path(ui_dump_file) if ui_dump_file else None
    if raw_scroll_y and str(raw_scroll_y).strip():
        try:
            scroll_y = int(str(raw_scroll_y).strip())
        except ValueError:
            scroll_y = raw_scroll_y
    else:
        scroll_y = None
    captures.append(
        {
            "route": route,
            "launch_target": launch_target,
            "orientation": orientation,
            "theme": theme_label,
            "theme_choice": theme_choice,
            "system_mode": system_mode,
            "scroll_position": scroll_position,
            "scroll_y": scroll_y,
            "scrollable": True,
            "path": file_name,
            "sha256": sha256(file_path) if file_path is not None and file_path.exists() else "",
            "uiautomator_dump_path": ui_dump_file,
            "uiautomator_dump_sha256": sha256(ui_dump_path) if ui_dump_path is not None and ui_dump_path.exists() else "",
        }
    )
captured_at_utc = datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")

manifest = {
    "captured_at_utc": captured_at_utc,
    "command_argv": [capture_script_path, requested_route] + ([requested_theme] if requested_theme else []),
    "git_sha": os.environ.get("GITHUB_SHA") or run(["git", "rev-parse", "HEAD"]),
    "git_ref": os.environ.get("GITHUB_REF_NAME") or os.environ.get("GITHUB_REF") or run(["git", "branch", "--show-current"]),
    "workflow_run_id": os.environ.get("GITHUB_RUN_ID", ""),
    "workflow_run_attempt": os.environ.get("GITHUB_RUN_ATTEMPT", ""),
    "package": package_name,
    "apk_path": apk_path,
    "device": {
        "profile": os.environ.get("ANDROID_SCREENSHOT_PROFILE", ""),
        "api_level": os.environ.get("ANDROID_SCREENSHOT_API_LEVEL", "") or run(["adb", "shell", "getprop", "ro.build.version.sdk"]),
        "manufacturer": run(["adb", "shell", "getprop", "ro.product.manufacturer"]),
        "model": run(["adb", "shell", "getprop", "ro.product.model"]),
        "sdk": run(["adb", "shell", "getprop", "ro.build.version.sdk"]),
    },
    "requested_route": requested_route,
    "requested_theme": requested_theme,
    "requested_locale": requested_locale,
    "requested_theme_choice": os.environ.get("REQUESTED_THEME_CHOICE", ""),
    "requested_system_mode": os.environ.get("REQUESTED_SYSTEM_MODE", ""),
    "routes": unique_routes(captured_routes),
    "files": captured_files,
    "captures": captures,
    "notes": [
        "Route capture waits for the requested screen and fails fast on Android ANR dialogs.",
        "Each capture may include a UIAutomator XML dump alongside the PNG for route assertions.",
    ],
}
(screenshots_dir / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY

log "Wrote ${screenshots_dir}/manifest.json"
