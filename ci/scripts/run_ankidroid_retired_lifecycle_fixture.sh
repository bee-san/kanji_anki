#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: run_ankidroid_retired_lifecycle_fixture.sh FIXTURE_DIR" >&2
  exit 2
}

if [ "$#" -ne 1 ]; then
  usage
fi

fixture_dir="$1"
manifest_path="${fixture_dir}/manifest.json"
if [ ! -f "${manifest_path}" ]; then
  echo "fixture manifest not found: ${manifest_path}" >&2
  exit 2
fi

stage_file() {
  local stage="$1"
  python3 - "${manifest_path}" "${stage}" <<'PY'
import json
import sys
from pathlib import Path

manifest = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
stage = manifest.get("stages", {}).get(sys.argv[2])
if not isinstance(stage, dict) or not isinstance(stage.get("file"), str):
    print(f"manifest missing stage {sys.argv[2]}", file=sys.stderr)
    raise SystemExit(2)
print(stage["file"])
PY
}

weak_file="$(stage_file weak_below_threshold)" || exit $?
mature_file="$(stage_file mature_at_threshold)" || exit $?
missing_file="$(stage_file missing_route)" || exit $?
invalid_file="$(stage_file invalid_ord1)" || exit $?

for file in "${weak_file}" "${mature_file}" "${missing_file}" "${invalid_file}"; do
  if [ ! -f "${fixture_dir}/${file}" ]; then
    echo "fixture stage file not found: ${fixture_dir}/${file}" >&2
    exit 2
  fi
done

phases=(
  weak_initial
  mature_retire
  mature_repeat
  weak_reopen
  missing_route_retire
  weak_reopen_after_missing
  invalid_ord1_fail_closed
)

if [ "${KANJI_RETIRED_LIFECYCLE_DRY_RUN:-0}" = "1" ]; then
  for phase in "${phases[@]}"; do
    echo "phase=${phase}"
  done
  exit 0
fi

command -v adb >/dev/null 2>&1 || {
  echo "adb is required" >&2
  exit 2
}

app_private_dir="/storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid"
legacy_dir="/storage/emulated/0/AnkiDroid"
app_private_collection="/storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid/collection.anki2"
legacy_collection="/storage/emulated/0/AnkiDroid/collection.anki2"
probe_class="dev.bee.kanjianki.RetiredLifecycleRealProviderInstrumentedTest#lifecycleStage"
output_dir="${RUNNER_TEMP:-/tmp}"

fixture_for_phase() {
  case "$1" in
    weak_initial|weak_reopen|weak_reopen_after_missing) printf '%s\n' "${weak_file}" ;;
    mature_retire|mature_repeat) printf '%s\n' "${mature_file}" ;;
    missing_route_retire) printf '%s\n' "${missing_file}" ;;
    invalid_ord1_fail_closed) printf '%s\n' "${invalid_file}" ;;
    *) echo "unknown lifecycle phase: $1" >&2; return 2 ;;
  esac
}

seed_stage() {
  local local_path="$1"
  adb shell am force-stop com.ichi2.anki || true
  adb root >/dev/null 2>&1 || true
  adb wait-for-device
  adb shell "mkdir -p '${app_private_dir}/collection.media' '${legacy_dir}/collection.media'; rm -f '${app_private_collection}' '${app_private_collection}-wal' '${app_private_collection}-shm' '${app_private_collection}-journal' '${legacy_collection}' '${legacy_collection}-wal' '${legacy_collection}-shm' '${legacy_collection}-journal'"
  adb push "${local_path}" "${app_private_collection}" >/dev/null
  adb push "${local_path}" "${legacy_collection}" >/dev/null
  adb shell "prefs_dir=/data/user/0/com.ichi2.anki/shared_prefs; prefs=\${prefs_dir}/com.ichi2.anki_preferences.xml; mkdir -p \"\${prefs_dir}\"; printf '%s\n' '<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\" ?>' '<map>' '    <string name=\"deckPath\">${app_private_dir}</string>' '</map>' > \"\${prefs}\"; owner_uid=\$(stat -c '%u' /data/user/0/com.ichi2.anki); owner_gid=\$(stat -c '%g' /data/user/0/com.ichi2.anki); chown \"\${owner_uid}\":\"\${owner_gid}\" \"\${prefs}\"; chmod 660 \"\${prefs}\"; chmod -R u+rwX,g+rwX '${app_private_dir}' '${legacy_dir}'"
  adb shell monkey -p com.ichi2.anki -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
  sleep "${KANJI_RETIRED_LIFECYCLE_SETTLE_SECONDS:-5}"
  adb shell pm grant dev.bee.kanjianki com.ichi2.anki.permission.READ_WRITE_DATABASE || true
}

run_phase() {
  local phase="$1"
  local fixture_file
  fixture_file="$(fixture_for_phase "${phase}")"
  local output_path="${output_dir}/ankidroid-retired-lifecycle-${phase}.txt"

  echo "phase=${phase}"
  seed_stage "${fixture_dir}/${fixture_file}"

  set +e
  adb shell am instrument -w \
    -e kanjiRetiredLifecycle true \
    -e lifecycleStage "${phase}" \
    -e class "${probe_class}" \
    dev.bee.kanjianki.test/androidx.test.runner.AndroidJUnitRunner \
    | tee "${output_path}"
  local adb_status=${PIPESTATUS[0]}
  set -e

  if [ "${adb_status}" -ne 0 ]; then
    echo "lifecycle instrumentation failed for ${phase} with exit ${adb_status}" >&2
    return "${adb_status}"
  fi
  if grep -Eq 'FAILURES!!!|INSTRUMENTATION_RESULT: shortMsg=.|Process crashed' "${output_path}"; then
    echo "lifecycle instrumentation reported failure for ${phase}" >&2
    return 1
  fi
  if ! grep -Eq '^OK \([0-9]+ tests?\)' "${output_path}"; then
    echo "lifecycle instrumentation did not report success for ${phase}" >&2
    return 1
  fi
}

for phase in "${phases[@]}"; do
  run_phase "${phase}"
done
