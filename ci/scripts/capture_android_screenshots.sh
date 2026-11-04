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
original_fixed_to_user_rotation="$(
  adb shell wm fixed-to-user-rotation 2>/dev/null \
    | tr -d '\r' \
    | awk '{ value = $NF } END { if (value ~ /^(enabled|disabled|default)$/) print value }' \
    || true
)"
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
    girlypop|light|dark|system|autumn|matcha_milk|ocean_study|midnight_arcade|grape_soda|forest_moss)
      printf '%s\n' "${theme_label}"
      ;;
    system-light|system-dark)
      printf 'system\n'
      ;;
    *)
      echo "Unsupported screenshot theme '${1}'. Expected girlypop, light, dark, system, system-light, system-dark, autumn, matcha_milk, ocean_study, midnight_arcade, grape_soda, or forest_moss." >&2
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
  if [ "${original_accelerometer_rotation}" = "1" ]; then
    adb shell wm user-rotation free >/dev/null 2>&1 || true
  elif [ -n "${original_user_rotation}" ] && [ "${original_user_rotation}" != "null" ]; then
    adb shell wm user-rotation lock "${original_user_rotation}" >/dev/null 2>&1 || true
  fi
  restore_setting system accelerometer_rotation "${original_accelerometer_rotation}"
  restore_setting system user_rotation "${original_user_rotation}"
  if [ -n "${original_fixed_to_user_rotation}" ]; then
    adb shell wm fixed-to-user-rotation "${original_fixed_to_user_rotation}" >/dev/null 2>&1 || true
  fi
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

expected_terms = [term.lower() for term in sys.argv[2:] if term]
if expected_terms and not all(term in lower for term in expected_terms):
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

current_display_rotation() {
  adb shell dumpsys window displays 2>/dev/null \
    | tr -d '\r' \
    | sed -nE 's/.*mDisplayRotation=ROTATION_(0|90|180|270).*/\1/p' \
    | head -n 1
}

wait_for_orientation() {
  local orientation="$1"
  local expected_rotation
  case "${orientation}" in
    portrait) expected_rotation=0 ;;
    landscape) expected_rotation=90 ;;
    *)
      echo "Unsupported orientation '${orientation}'" >&2
      exit 1
      ;;
  esac
  local attempt
  for ((attempt = 1; attempt <= 30; attempt++)); do
    if [ "$(current_display_rotation)" = "${expected_rotation}" ]; then
      return 0
    fi
    sleep 0.2
  done
  echo "Timed out waiting for ${orientation} display rotation." >&2
  exit 1
}

set_orientation() {
  local orientation="$1"
  # Emulator/device activity orientation requests can race the user-rotation
  # setting. During deterministic capture, keep the display fixed to the
  # requested user rotation and restore the original policy on exit.
  adb shell wm fixed-to-user-rotation enabled >/dev/null 2>&1 || true
  case "${orientation}" in
    portrait)
      adb shell wm user-rotation lock 0 >/dev/null 2>&1 || {
        adb shell settings put system accelerometer_rotation 0 >/dev/null 2>&1 || true
        adb shell settings put system user_rotation 0 >/dev/null 2>&1 || true
      }
      ;;
    landscape)
      adb shell wm user-rotation lock 1 >/dev/null 2>&1 || {
        adb shell settings put system accelerometer_rotation 0 >/dev/null 2>&1 || true
        adb shell settings put system user_rotation 1 >/dev/null 2>&1 || true
      }
      ;;
    *)
      echo "Unsupported orientation '${orientation}'" >&2
      exit 1
      ;;
  esac
  wait_for_orientation "${orientation}"
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
  local -a start_args=(am start -W -n "${package_name}/.host.KaniHostActivity" --es "${screen_route_extra}" "${launch_target}")
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
  local ui_dump_path
  scroll_y="$(scroll_y_for_position "${orientation}" "${scroll_position}")"
  if [[ "${route_name}" == "settings" && "${scroll_position}" == "bottom" ]]; then
    scroll_y="$((scroll_y + settings_bottom_scroll_extra))"
  fi
  log "Capturing ${capture_name} (${launch_target}, ${orientation}, ${scroll_position} @ ${scroll_y})"
  set_orientation "${orientation}"
  launch_screenshot_route "${launch_target}" "${capture_theme_choice}" "${scroll_position}" "${scroll_y}"
  wait_for_route "${capture_name}" "${expected_terms[@]}"
  wait_for_orientation "${orientation}"
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
study_label="Study"
missing_kanji_label="Missing Kanji"
route_label_prefix="Kani route"
requested_locale_lower="$(printf '%s' "${requested_locale}" | tr '[:upper:]' '[:lower:]')"
if [[ "${requested_locale_lower}" == ja* ]]; then
  stats_label="統計"
  study_label="学習"
  missing_kanji_label="未登録漢字"
  route_label_prefix="Kaniルート"
fi
if [ -n "${requested_locale}" ]; then
  log "Using screenshot locale ${requested_locale}"
fi

case "${requested_route}" in
  all)
    capture_route_triplet home home portrait "${route_label_prefix} home"
    capture_route_triplet study study portrait "${route_label_prefix} study" "${study_label}"
    capture_route_triplet stats stats portrait "${route_label_prefix} stats" "${stats_label}"
    capture_route_triplet settings settings portrait "${route_label_prefix} settings" "Settings"
    capture_route_triplet games games portrait "Games"
    capture_route_variant missing-kanji missing-kanji-top missing-kanji portrait top "${missing_kanji_label}"
    capture_route_triplet narrow home portrait "${route_label_prefix} home"
    capture_route_triplet wide home landscape "${route_label_prefix} home"
    ;;
  launcher-home|home)
    capture_route_triplet home home portrait "${route_label_prefix} home"
    ;;
  study)
    capture_route_triplet study study portrait "${route_label_prefix} study" "${study_label}"
    ;;
  stats)
    capture_route_triplet stats stats portrait "${route_label_prefix} stats" "${stats_label}"
    ;;
  stats-heatmap)
    capture_route_variant stats stats-heatmap stats portrait 1280 "${route_label_prefix} stats" "${stats_label}"
    ;;
  settings)
    capture_route_triplet settings settings portrait "${route_label_prefix} settings" "Settings"
    ;;
  games)
    capture_route_triplet games games portrait "Games"
    ;;
  missing-kanji)
    capture_route_variant missing-kanji missing-kanji-top missing-kanji portrait top "${missing_kanji_label}"
    ;;
  narrow)
    capture_route_triplet narrow home portrait "${route_label_prefix} home"
    ;;
  wide)
    capture_route_triplet wide home landscape "${route_label_prefix} home"
    ;;
  update)
    capture_route_triplet update update portrait "${route_label_prefix} settings" "GitHub updater"
    ;;
  *)
    echo "Unsupported screenshot route '${requested_route}'. Expected one of: all, home, launcher-home, study, stats, stats-heatmap, settings, games, missing-kanji, narrow, wide, update." >&2
    exit 1
    ;;
esac

APK_PATH="${apk_path}"
PACKAGE_NAME="${package_name}"
REQUESTED_ROUTE="${requested_route}"
REQUESTED_THEME="${requested_theme}"
REQUESTED_LOCALE="${requested_locale}"
REQUESTED_THEME_CHOICE="${capture_theme_choice}"
REQUESTED_SYSTEM_MODE="${capture_theme_system_mode}"
CAPTURE_SCRIPT_PATH="${0}"
export APK_PATH PACKAGE_NAME REQUESTED_ROUTE REQUESTED_THEME REQUESTED_LOCALE
export REQUESTED_THEME_CHOICE REQUESTED_SYSTEM_MODE CAPTURE_SCRIPT_PATH

python3 - "${#captured_routes[@]}" \
  "${captured_routes[@]}" \
  "${captured_files[@]}" \
  "${captured_orientations[@]}" \
  "${captured_launch_targets[@]}" \
  "${captured_scroll_positions[@]}" \
  "${captured_scroll_ys[@]}" \
  "${captured_themes[@]}" \
  "${captured_theme_choices[@]}" \
  "${captured_uiautomator_dumps[@]}" \
  "${captured_system_modes[@]}" <<'PY'
import hashlib
import json
import os
import subprocess
import sys
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

capture_count = int(sys.argv[1])
field_names = (
    "routes",
    "files",
    "orientations",
    "launch_targets",
    "scroll_positions",
    "scroll_ys",
    "themes",
    "theme_choices",
    "uiautomator_dumps",
    "system_modes",
)
serialized_fields = sys.argv[2:]
expected_field_count = capture_count * len(field_names)
if capture_count < 1 or len(serialized_fields) != expected_field_count:
    raise SystemExit(
        f"Captured screenshot metadata is out of sync: expected {expected_field_count} "
        f"fields for {capture_count} captures, got {len(serialized_fields)}."
    )
fields = {
    name: serialized_fields[index * capture_count : (index + 1) * capture_count]
    for index, name in enumerate(field_names)
}
captured_routes = fields["routes"]
captured_files = fields["files"]
captured_orientations = fields["orientations"]
captured_launch_targets = fields["launch_targets"]
captured_scroll_positions = fields["scroll_positions"]
captured_scroll_ys = fields["scroll_ys"]
captured_themes = fields["themes"]
captured_theme_choices = fields["theme_choices"]
captured_uiautomator_dumps = fields["uiautomator_dumps"]
captured_system_modes = fields["system_modes"]


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
    if file_path is not None and not file_path.is_absolute():
        file_path = screenshots_dir / file_path
    ui_dump_path = Path(ui_dump_file) if ui_dump_file else None
    if ui_dump_path is not None and not ui_dump_path.is_absolute():
        ui_dump_path = screenshots_dir / ui_dump_path
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
