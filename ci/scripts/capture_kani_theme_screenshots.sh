#!/usr/bin/env bash
set -euo pipefail

requested_route="${1:-all}"
shots_root="${SCREENSHOTS_DIR:-screenshots/kani-theme-matrix}"
theme_matrix_raw="${SCREENSHOT_THEME_MATRIX:-girlypop,light,dark,system-light,system-dark,autumn}"

repo_root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "${repo_root}"

log() {
  printf '[kani-theme-screenshots] %s\n' "$*"
}

trim() {
  local value="$1"
  value="$(printf '%s' "$value" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
  printf '%s' "$value"
}

declare -a theme_labels=()
IFS=',' read -r -a raw_theme_labels <<<"${theme_matrix_raw}"
for theme in "${raw_theme_labels[@]}"; do
  theme="$(trim "${theme}")"
  if [ -n "${theme}" ]; then
    theme_labels+=("${theme}")
  fi
done

if [ "${#theme_labels[@]}" -eq 0 ]; then
  echo "No screenshot themes were requested." >&2
  exit 1
fi

mkdir -p "${shots_root}"

has_device() {
  if ! command -v adb >/dev/null 2>&1; then
    return 1
  fi
  adb devices 2>/dev/null | awk '$2 == "device" { found = 1 } END { exit(found ? 0 : 1) }'
}

write_visual_pending_manifest() {
  python3 - "$shots_root" "$requested_route" "${theme_labels[@]}" <<'PY'
import json
import os
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

root = Path(sys.argv[1])
requested_route = sys.argv[2]
themes = sys.argv[3:]

def run(command):
    try:
        return subprocess.check_output(command, text=True, stderr=subprocess.DEVNULL).strip()
    except Exception:
        return ""

manifest = {
    "captured_at_utc": datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
    "capture_status": "visual_pending",
    "command_argv": ["ci/scripts/capture_kani_theme_screenshots.sh", requested_route],
    "git_sha": os.environ.get("GITHUB_SHA") or run(["git", "rev-parse", "HEAD"]),
    "git_ref": os.environ.get("GITHUB_REF_NAME") or os.environ.get("GITHUB_REF") or run(["git", "branch", "--show-current"]),
    "requested_route": requested_route,
    "requested_theme_matrix": themes,
    "theme_count": len(themes),
    "theme_runs": [],
    "files": [],
    "manifest_files": [],
    "notes": [
        "No adb device was available, so no PNGs were captured.",
        "This manifest is intentionally labeled visual_pending until the emulator/device path succeeds.",
    ],
}
(root / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
}

if ! has_device; then
  log "No connected adb device found; writing visual_pending manifest."
  write_visual_pending_manifest
  log "Wrote ${shots_root}/manifest.json"
  exit 0
fi

theme_manifest_paths=()
for theme in "${theme_labels[@]}"; do
  theme_dir="${shots_root}/${theme}"
  mkdir -p "${theme_dir}"
  log "Capturing ${requested_route} for theme ${theme}"
  SCREENSHOTS_DIR="${theme_dir}" bash "ci/scripts/capture_android_screenshots.sh" "${requested_route}" "${theme}"
  theme_manifest_paths+=("${theme_dir}/manifest.json")
done

python3 - "$shots_root" "$requested_route" "${theme_labels[@]}" <<'PY'
import json
import os
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

root = Path(sys.argv[1])
requested_route = sys.argv[2]
themes = sys.argv[3:]

def run(command):
    try:
        return subprocess.check_output(command, text=True, stderr=subprocess.DEVNULL).strip()
    except Exception:
        return ""

files = []
manifest_files = []
theme_runs = []
for theme in themes:
    manifest_path = root / theme / "manifest.json"
    if not manifest_path.exists():
        raise SystemExit(f"Missing theme manifest: {manifest_path}")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    relative_manifest_path = str(manifest_path.relative_to(root))
    manifest_files.append(relative_manifest_path)
    files.extend(manifest.get("files", []))
    theme_runs.append(
        {
            "theme": theme,
            "manifest_path": relative_manifest_path,
            "capture_status": manifest.get("capture_status", "complete"),
            "requested_route": manifest.get("requested_route", requested_route),
            "requested_theme": manifest.get("requested_theme", ""),
            "requested_theme_choice": manifest.get("requested_theme_choice", ""),
            "requested_system_mode": manifest.get("requested_system_mode", ""),
            "device": manifest.get("device", {}),
            "command_argv": manifest.get("command_argv", []),
            "routes": manifest.get("routes", []),
            "files": manifest.get("files", []),
            "captures": manifest.get("captures", []),
            "notes": manifest.get("notes", []),
        }
    )

manifest = {
    "captured_at_utc": datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
    "capture_status": "complete",
    "command_argv": ["ci/scripts/capture_kani_theme_screenshots.sh", requested_route],
    "git_sha": os.environ.get("GITHUB_SHA") or run(["git", "rev-parse", "HEAD"]),
    "git_ref": os.environ.get("GITHUB_REF_NAME") or os.environ.get("GITHUB_REF") or run(["git", "branch", "--show-current"]),
    "requested_route": requested_route,
    "requested_theme_matrix": themes,
    "theme_count": len(themes),
    "theme_runs": theme_runs,
    "files": files,
    "manifest_files": manifest_files,
    "notes": [
        "Each theme run writes its own manifest in a per-theme subdirectory.",
        "The root manifest aggregates the requested theme matrix and all per-theme capture manifests.",
    ],
}
(root / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY

log "Wrote ${shots_root}/manifest.json"
