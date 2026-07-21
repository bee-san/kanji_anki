#!/usr/bin/env bash
set -euo pipefail

# Boots a local emulator, prepares a sanitized or caller-provided AnkiDroid
# collection, and delegates the app/provider instrumentation gate to
# ci/scripts/run_ankidroid_fixture.sh.
#
# Safe defaults use the deterministic sanitized Kiku fixture. For release-risk
# provider/sync validation with a real user collection, pass the local-only copy
# path as the first argument and set KANJI_LIVE_MINIMUM_NOTES only if you are
# intentionally lowering the threshold for a smoke run.

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
collection_path="${1:-}"
collection_arg_provided=0
if [ -n "${collection_path}" ]; then
  collection_arg_provided=1
fi

android_home="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "${android_home}" ]; then
  for candidate in \
    "${HOME}/android-sdk" \
    "/tmp/android-sdk" \
    "/opt/homebrew/share/android-commandlinetools" \
    "${HOME}/Library/Android/sdk" \
    "${HOME}/Android/Sdk"; do
    if [ -d "${candidate}" ]; then
      android_home="${candidate}"
      break
    fi
  done
  android_home="${android_home:-${HOME}/Android/Sdk}"
fi
export ANDROID_HOME="${android_home}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME}}"
export PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/emulator:${PATH}"

java_home_default="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
if [ -z "${JAVA_HOME:-}" ] && [ -d "${java_home_default}" ]; then
  export JAVA_HOME="${java_home_default}"
fi

avd_name="${KANJI_ANKIDROID_AVD:-kanji_anki_api35_local}"
api_level="${KANJI_ANKIDROID_API_LEVEL:-35}"
release_tag="${KANJI_ANKIDROID_RELEASE:-v2.24.0}"
work_dir="${KANJI_ANKIDROID_WORK_DIR:-${TMPDIR:-/tmp}/kanji-ankidroid-fixture}"
mkdir -p "${work_dir}"

host_arch="$(uname -m)"
case "${KANJI_ANKIDROID_ARCH:-${host_arch}}" in
  arm64|aarch64|arm64-v8a) image_arch="arm64-v8a" ;;
  x86_64|amd64) image_arch="x86_64" ;;
  *) echo "Unsupported emulator architecture: ${KANJI_ANKIDROID_ARCH:-${host_arch}}" >&2; exit 2 ;;
esac

system_image="${KANJI_ANKIDROID_SYSTEM_IMAGE:-system-images;android-${api_level};google_atd;${image_arch}}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 127
  fi
}

install_sdk_packages() {
  require_command sdkmanager
  yes | sdkmanager --licenses >/dev/null || true
  sdkmanager \
    "platform-tools" \
    "emulator" \
    "platforms;android-36" \
    "build-tools;36.0.0" \
    "${system_image}"
}

create_avd_if_needed() {
  require_command avdmanager
  require_command emulator
  if emulator -list-avds | grep -Fxq "${avd_name}"; then
    return 0
  fi
  echo "Creating AVD ${avd_name} from ${system_image}"
  avdmanager create avd \
    --force \
    --name "${avd_name}" \
    --package "${system_image}" \
    --device "pixel_2"
}

pick_ankidroid_asset() {
  local metadata_path="$1"
  local desired_arch="$2"
  python3 - "$metadata_path" "$desired_arch" <<'PY'
import json
import sys
from pathlib import Path

metadata = json.loads(Path(sys.argv[1]).read_text())
desired_arch = sys.argv[2]
assets = metadata.get("assets", [])

names = [(asset.get("name", ""), asset.get("browser_download_url", "")) for asset in assets]
priorities = []
if desired_arch == "arm64-v8a":
    priorities = ["arm64", "arm64-v8a", "universal", "full"]
else:
    priorities = ["x86_64", "x86-64", "universal", "full"]

for needle in priorities:
    for name, url in names:
        lowered = name.lower()
        if lowered.endswith(".apk") and needle in lowered and url:
            print(url)
            raise SystemExit(0)

for name, url in names:
    if name.lower().endswith(".apk") and url:
        print(url)
        raise SystemExit(0)

raise SystemExit("No APK asset found in release metadata")
PY
}

expected_ankidroid_sha256() {
  local configured_sha256="${KANJI_ANKIDROID_APK_SHA256:-}"
  if [ -n "${configured_sha256}" ]; then
    if [[ ! "${configured_sha256}" =~ ^[0-9a-fA-F]{64}$ ]]; then
      echo "KANJI_ANKIDROID_APK_SHA256 must contain exactly 64 hexadecimal characters." >&2
      return 2
    fi
    printf '%s\n' "${configured_sha256}" | tr '[:upper:]' '[:lower:]'
    return 0
  fi

  if [ "${release_tag}" != "v2.24.0" ]; then
    echo "KANJI_ANKIDROID_APK_SHA256 is required for unpinned release ${release_tag}." >&2
    return 2
  fi

  local asset_name
  case "${image_arch}" in
    arm64-v8a) asset_name="AnkiDroid-2.24.0-arm64-v8a.apk" ;;
    x86_64) asset_name="variant-abi-AnkiDroid-2.24.0-x86_64.apk" ;;
  esac
  local checksum_file="${repo_root}/ci/fixtures/ankidroid/ankidroid-2.24.0.sha256"
  local expected
  expected="$(awk -v name="${asset_name}" '$2 == name { print $1 }' "${checksum_file}")"
  if [[ ! "${expected}" =~ ^[0-9a-f]{64}$ ]]; then
    echo "Pinned SHA-256 is missing for ${asset_name}." >&2
    return 2
  fi
  printf '%s\n' "${expected}"
}

verify_apk_sha256() {
  local apk_path="$1"
  local expected_sha256="$2"
  python3 - "${apk_path}" "${expected_sha256}" <<'PY'
import hashlib
import sys
from pathlib import Path

path = Path(sys.argv[1])
expected = sys.argv[2].lower()
digest = hashlib.sha256()
with path.open("rb") as handle:
    for chunk in iter(lambda: handle.read(1024 * 1024), b""):
        digest.update(chunk)
actual = digest.hexdigest()
if actual != expected:
    raise SystemExit(f"SHA-256 mismatch for {path}: expected {expected}, got {actual}")
PY
}

download_ankidroid_apk() {
  if [ -n "${ANKIDROID_APK:-}" ]; then
    if [ ! -s "${ANKIDROID_APK}" ]; then
      echo "ANKIDROID_APK does not exist or is empty: ${ANKIDROID_APK}" >&2
      return 2
    fi
    if [ -n "${KANJI_ANKIDROID_APK_SHA256:-}" ]; then
      verify_apk_sha256 "${ANKIDROID_APK}" "$(expected_ankidroid_sha256)"
    fi
    printf '%s\n' "${ANKIDROID_APK}"
    return 0
  fi

  require_command python3
  local expected_sha256
  expected_sha256="$(expected_ankidroid_sha256)"
  local apk_dir="${work_dir}/ankidroid-${release_tag}-${image_arch}"
  local apk_path="${apk_dir}/AnkiDroid-${release_tag}-${image_arch}.apk"
  mkdir -p "${apk_dir}"
  if [ -s "${apk_path}" ]; then
    if verify_apk_sha256 "${apk_path}" "${expected_sha256}"; then
      printf '%s\n' "${apk_path}"
      return 0
    fi
    echo "Discarding cached AnkiDroid APK with an invalid SHA-256: ${apk_path}" >&2
    rm -f "${apk_path}"
  fi

  require_command curl
  local metadata_path="${apk_dir}/release.json"
  curl --fail --location --silent --show-error \
    "https://api.github.com/repos/ankidroid/Anki-Android/releases/tags/${release_tag}" \
    --output "${metadata_path}"
  local asset_url
  asset_url="$(pick_ankidroid_asset "${metadata_path}" "${image_arch}")"
  echo "Downloading AnkiDroid ${release_tag} asset for ${image_arch}: ${asset_url}" >&2
  local partial_path="${apk_path}.partial"
  rm -f "${partial_path}"
  curl --fail --location --show-error "${asset_url}" --output "${partial_path}"
  if ! verify_apk_sha256 "${partial_path}" "${expected_sha256}"; then
    rm -f "${partial_path}"
    return 1
  fi
  mv "${partial_path}" "${apk_path}"
  printf '%s\n' "${apk_path}"
}

require_emulator_target() {
  local qemu
  qemu="$(adb shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r' || true)"
  if [ "${qemu}" != "1" ]; then
    local serial
    serial="$(adb get-serialno 2>/dev/null || printf 'unknown')"
    echo "Refusing to overwrite AnkiDroid data on non-emulator ADB target ${serial}." >&2
    return 2
  fi
}

boot_emulator() {
  require_command adb
  require_command emulator
  adb start-server >/dev/null
  local connected_count
  connected_count="$(adb devices | awk '$2 == "device" { count++ } END { print count + 0 }')"
  if [ "${connected_count}" -gt 0 ]; then
    if ! adb get-state >/dev/null 2>&1; then
      echo "Unable to select one connected ADB target; set ANDROID_SERIAL explicitly." >&2
      return 2
    fi
    require_emulator_target
    echo "Using already-connected Android emulator: $(adb get-serialno)" >&2
    return 0
  fi

  local log_path="${work_dir}/${avd_name}-emulator.log"
  echo "Starting emulator ${avd_name}; log: ${log_path}" >&2
  emulator -avd "${avd_name}" \
    -no-window \
    -no-audio \
    -no-snapshot \
    -no-boot-anim \
    -gpu swiftshader_indirect \
    >"${log_path}" 2>&1 &
  local emulator_pid=$!
  echo "${emulator_pid}" > "${work_dir}/${avd_name}.pid"

  adb wait-for-device
  local boot_completed=""
  for _ in $(seq 1 120); do
    boot_completed="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    if [ "${boot_completed}" = "1" ]; then
      require_emulator_target
      adb shell settings put global window_animation_scale 0 || true
      adb shell settings put global transition_animation_scale 0 || true
      adb shell settings put global animator_duration_scale 0 || true
      return 0
    fi
    sleep 2
  done

  echo "Emulator did not finish booting; leaving it running for inspection (pid ${emulator_pid})." >&2
  return 1
}

main() {
  if [ "${KANJI_INSTALL_ANDROID_SDK_PACKAGES:-0}" = "1" ]; then
    install_sdk_packages
  fi

  create_avd_if_needed

  if [ -z "${collection_path}" ]; then
    collection_path="${work_dir}/kiku-provider-fixture.anki2"
    python3 "${repo_root}/ci/scripts/create_ankidroid_kiku_fixture.py" "${collection_path}"
    if [ -z "${KANJI_LIVE_MINIMUM_NOTES+x}" ]; then
      export KANJI_LIVE_MINIMUM_NOTES=1
    fi
  elif [ "${collection_arg_provided}" -eq 1 ] && [ -z "${KANJI_LIVE_MINIMUM_NOTES+x}" ]; then
    # A caller-provided collection is assumed to be a local real-collection gate.
    # Leave the instrumentation argument unset so the test default remains the
    # stricter 7,000-note threshold.
    export KANJI_LIVE_MINIMUM_NOTES=""
  fi

  if [ ! -s "${collection_path}" ]; then
    echo "Collection path does not exist or is empty: ${collection_path}" >&2
    exit 2
  fi

  local ankidroid_apk
  ankidroid_apk="$(download_ankidroid_apk)"

  boot_emulator

  echo "Running live AnkiDroid fixture gate"
  echo "  AVD: ${avd_name}"
  echo "  APK: ${ankidroid_apk}"
  echo "  Collection: ${collection_path}"
  echo "  Diagnostics: ${RUNNER_TEMP:-/tmp}/ankidroid-fixture-*.txt and ${work_dir}/${avd_name}-emulator.log"
  (cd "${repo_root}" && bash ci/scripts/run_ankidroid_fixture.sh "${ankidroid_apk}" "${collection_path}")
}

main "$@"
