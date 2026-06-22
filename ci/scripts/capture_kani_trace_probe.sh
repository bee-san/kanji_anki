#!/usr/bin/env bash
set -euo pipefail

requested_route="${1:-all}"
probe_root="${KANI_TRACE_PROBE_DIR:-reports/kani-trace-probe}"
apk_path="${APK_PATH:-app/build/outputs/apk/debug/app-debug.apk}"
package_name="${PACKAGE_NAME:-dev.bee.kanjianki}"
screen_route_extra="${SCREENSHOT_ROUTE_EXTRA:-dev.bee.kanjianki.extra.SCREENSHOT_ROUTE}"
data_state_assumption="${KANI_TRACE_PROBE_DATA_STATE:-pm clear dev.bee.kanjianki, then ButtonLatencyFixtureInstrumentedTest#seedRepresentativeLocalStoreForButtonLatencyBenchmark}"
pid_attempts="${KANI_TRACE_PROBE_PID_ATTEMPTS:-60}"
settle_seconds="${KANI_TRACE_PROBE_SETTLE_SECONDS:-2}"

repo_root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "${repo_root}"
mkdir -p "${probe_root}"

log() {
  printf '[kani-trace-probe] %s\n' "$*"
}

write_pending_manifest() {
  local reason="$1"
  python3 - "${probe_root}" "${requested_route}" "${apk_path}" "${package_name}" "${data_state_assumption}" "${reason}" <<'PY'
import json
import os
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

root = Path(sys.argv[1])
requested_route = sys.argv[2]
apk_path = sys.argv[3]
package_name = sys.argv[4]
data_state_assumption = sys.argv[5]
reason = sys.argv[6]


def run(command):
    try:
        return subprocess.check_output(command, text=True, stderr=subprocess.DEVNULL).strip()
    except Exception:
        return ""


manifest = {
    "captured_at_utc": datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
    "capture_status": "device_pending",
    "command_argv": ["ci/scripts/capture_kani_trace_probe.sh", requested_route],
    "git_sha": os.environ.get("GITHUB_SHA") or run(["git", "rev-parse", "HEAD"]),
    "git_ref": os.environ.get("GITHUB_REF_NAME") or os.environ.get("GITHUB_REF") or run(["git", "branch", "--show-current"]),
    "requested_route": requested_route,
    "package": package_name,
    "apk_path": apk_path,
    "data_state_assumption": data_state_assumption,
    "routes": [],
    "files": [],
    "manifest_files": [],
    "device": {},
    "notes": [
        "No adb device was available, so no app-pid trace artifacts were captured.",
        "This probe uses app-pid-filtered logcat and dumpsys gfxinfo only; it does not use UiAutomator polling.",
        "Expected data state: " + data_state_assumption,
        "Device availability reason: " + reason,
    ],
}
(root / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
}

has_device() {
  adb devices 2>/dev/null | awk '$2 == "device" { found = 1 } END { exit(found ? 0 : 1) }'
}

if ! command -v adb >/dev/null 2>&1; then
  write_pending_manifest "adb executable not found on PATH"
  log "No adb executable found; wrote device_pending manifest."
  exit 0
fi

if ! has_device; then
  write_pending_manifest "no connected adb device"
  log "No connected adb device found; wrote device_pending manifest."
  exit 0
fi

wait_for_pid() {
  local attempt
  for attempt in $(seq 1 "${pid_attempts}"); do
    local pid
    pid="$(adb shell pidof -s "${package_name}" 2>/dev/null | tr -d '\r\n')"
    if [ -n "${pid}" ]; then
      printf '%s\n' "${pid}"
      return 0
    fi
    sleep 1
  done

  echo "Timed out waiting for app pid for ${package_name}" >&2
  exit 1
}

launch_route() {
  local launch_target="$1"
  local start_output_path="$2"

  adb shell am force-stop "${package_name}" >/dev/null 2>&1 || true
  adb logcat -c >/dev/null 2>&1 || true
  adb shell am start -W -n "${package_name}/.MainActivity" --es "${screen_route_extra}" "${launch_target}" | tee "${start_output_path}"
}

write_route_manifest() {
  local route_dir="$1"
  local capture_name="$2"
  local launch_target="$3"
  local pid="$4"
  python3 - "${probe_root}" "${route_dir}" "${capture_name}" "${launch_target}" "${pid}" "${package_name}" "${apk_path}" "${data_state_assumption}" <<'PY'
import json
import os
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

probe_root = Path(sys.argv[1])
route_dir = Path(sys.argv[2])
capture_name = sys.argv[3]
launch_target = sys.argv[4]
pid = sys.argv[5]
package_name = sys.argv[6]
apk_path = sys.argv[7]
data_state_assumption = sys.argv[8]


def run(command):
    try:
        return subprocess.check_output(command, text=True, stderr=subprocess.DEVNULL).strip()
    except Exception:
        return ""


manifest = {
    "captured_at_utc": datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
    "capture_status": "complete",
    "command_argv": ["ci/scripts/capture_kani_trace_probe.sh", capture_name],
    "git_sha": os.environ.get("GITHUB_SHA") or run(["git", "rev-parse", "HEAD"]),
    "git_ref": os.environ.get("GITHUB_REF_NAME") or os.environ.get("GITHUB_REF") or run(["git", "branch", "--show-current"]),
    "requested_route": capture_name,
    "launch_target": launch_target,
    "pid": pid,
    "package": package_name,
    "apk_path": apk_path,
    "data_state_assumption": data_state_assumption,
    "files": [
        "am-start.txt",
        "pid.txt",
        "logcat.txt",
        "gfxinfo.txt",
    ],
    "notes": [
        "App-pid-only trace probe: launch with am start -W, then collect pid-filtered KaniPerf/Choreographer/HWUI logcat plus dumpsys gfxinfo.",
        "No UiAutomator polling is used.",
        "Expected data state: " + data_state_assumption,
    ],
}
(route_dir / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
}

captured_routes=()
captured_files=()
captured_manifest_files=()

capture_route() {
  local capture_name="$1"
  local launch_target="$2"
  local route_dir="${probe_root}/${capture_name}"
  local start_output_path="${route_dir}/am-start.txt"
  local pid

  mkdir -p "${route_dir}"
  log "Capturing ${capture_name} (${launch_target})"
  launch_route "${launch_target}" "${start_output_path}"
  pid="$(wait_for_pid)"
  printf '%s\n' "${pid}" > "${route_dir}/pid.txt"
  sleep "${settle_seconds}"
  adb logcat -d --pid "${pid}" -s KaniPerf Choreographer HWUI > "${route_dir}/logcat.txt" 2>&1 || true
  adb shell dumpsys gfxinfo "${package_name}" > "${route_dir}/gfxinfo.txt" 2>&1 || true
  write_route_manifest "${route_dir}" "${capture_name}" "${launch_target}" "${pid}"
  captured_routes+=("${capture_name}")
  captured_files+=(
    "${capture_name}/am-start.txt"
    "${capture_name}/pid.txt"
    "${capture_name}/logcat.txt"
    "${capture_name}/gfxinfo.txt"
  )
  captured_manifest_files+=("${capture_name}/manifest.json")
}

case "${requested_route}" in
  all)
    capture_route home home
    capture_route study study
    capture_route stats stats
    capture_route settings settings
    capture_route games games
    capture_route update update
    ;;
  launcher-home|home)
    capture_route home home
    ;;
  study)
    capture_route study study
    ;;
  stats)
    capture_route stats stats
    ;;
  settings)
    capture_route settings settings
    ;;
  games)
    capture_route games games
    ;;
  update)
    capture_route update update
    ;;
  *)
    echo "Unsupported trace probe route '${requested_route}'. Expected one of: all, home, launcher-home, study, stats, settings, games, update." >&2
    exit 1
    ;;
esac

routes_blob="$(printf '%s\n' "${captured_routes[@]}")"
files_blob="$(printf '%s\n' "${captured_files[@]}")"
manifest_files_blob="$(printf '%s\n' "${captured_manifest_files[@]}")"

python3 - "${probe_root}" "${requested_route}" "${apk_path}" "${package_name}" "${data_state_assumption}" "${routes_blob}" "${files_blob}" "${manifest_files_blob}" <<'PY'
import json
import os
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

root = Path(sys.argv[1])
requested_route = sys.argv[2]
apk_path = sys.argv[3]
package_name = sys.argv[4]
data_state_assumption = sys.argv[5]
routes = [item for item in sys.argv[6].splitlines() if item]
files = [item for item in sys.argv[7].splitlines() if item]
manifest_files = [item for item in sys.argv[8].splitlines() if item]


def run(command):
    try:
        return subprocess.check_output(command, text=True, stderr=subprocess.DEVNULL).strip()
    except Exception:
        return ""


manifest = {
    "captured_at_utc": datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
    "capture_status": "complete",
    "command_argv": ["ci/scripts/capture_kani_trace_probe.sh", requested_route],
    "git_sha": os.environ.get("GITHUB_SHA") or run(["git", "rev-parse", "HEAD"]),
    "git_ref": os.environ.get("GITHUB_REF_NAME") or os.environ.get("GITHUB_REF") or run(["git", "branch", "--show-current"]),
    "requested_route": requested_route,
    "package": package_name,
    "apk_path": apk_path,
    "data_state_assumption": data_state_assumption,
    "device": {
        "manufacturer": run(["adb", "shell", "getprop", "ro.product.manufacturer"]),
        "model": run(["adb", "shell", "getprop", "ro.product.model"]),
        "sdk": run(["adb", "shell", "getprop", "ro.build.version.sdk"]),
    },
    "routes": routes,
    "files": files,
    "manifest_files": manifest_files,
    "notes": [
        "App-pid-only trace probe: launch each route with am start -W, then collect pid-filtered KaniPerf/Choreographer/HWUI logcat plus dumpsys gfxinfo.",
        "No UiAutomator polling is used.",
        "Expected device state: adb-connected device or emulator with the debug APK installed, booted, unlocked, and ready for shell commands.",
        "Expected data state: " + data_state_assumption,
    ],
}
(root / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY

log "Wrote ${probe_root}/manifest.json"
