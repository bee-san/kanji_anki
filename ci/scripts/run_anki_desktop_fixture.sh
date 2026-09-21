#!/usr/bin/env bash
set -euo pipefail

# Boots pinned Anki Desktop with pinned AnkiConnect against a throwaway profile,
# on an isolated loopback port, and waits until AnkiConnect answers.
#
# This exists because Kani's desktop provider was written against a mock HTTP
# server, and a mock agrees with whatever the client believes. The first probe
# against this fixture found that `getActiveProfile` -- which Kani required and
# would refuse to connect without -- is not an AnkiConnect action at all. That
# class of defect is only findable against a real host.
#
# The operator's own Anki is never touched: separate base directory, separate
# add-on copy, separate port. See ci/fixtures/anki-desktop/README.md.
#
# Usage:
#   ci/scripts/run_anki_desktop_fixture.sh                  # boot and wait
#   ci/scripts/run_anki_desktop_fixture.sh --stop           # shut down
#   ci/scripts/run_anki_desktop_fixture.sh --probe version  # one read-only action
#
# Environment:
#   KANI_ANKI_DESKTOP_WORK_DIR  fixture root (default /tmp/kani-anki-desktop-fixture)
#   KANI_ANKI_DESKTOP_PORT      AnkiConnect port (default 18765)
#   KANI_ANKI_DESKTOP_PROFILE   profile to open (default KaniFixture)
#   KANI_ANKI_DESKTOP_VERSION   Anki release (default 26.05)
#   KANI_ANKICONNECT_COMMIT     AnkiConnect commit (default the pinned one)

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

work_dir="${KANI_ANKI_DESKTOP_WORK_DIR:-${TMPDIR:-/tmp}/kani-anki-desktop-fixture}"
port="${KANI_ANKI_DESKTOP_PORT:-18765}"
profile="${KANI_ANKI_DESKTOP_PROFILE:-KaniFixture}"
anki_version="${KANI_ANKI_DESKTOP_VERSION:-26.05}"
ankiconnect_commit="${KANI_ANKICONNECT_COMMIT:-4064fa142785975255457abd6a496015f5b71f38}"
checksums="${repo_root}/ci/fixtures/anki-desktop/anki-desktop-${anki_version}.sha256"

# The standard AnkiConnect port. Binding it would put fixture traffic on the
# operator's live Anki, which is the one thing this fixture must never do.
readonly RESERVED_LIVE_PORT=8765

base_dir="${work_dir}/base"
anki_dir="${work_dir}/anki-linux"
addon_dir="${base_dir}/addons21/kani_ankiconnect_fixture"
endpoint="http://127.0.0.1:${port}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 127
  fi
}

expected_sha256() {
  local filename="$1"
  local digest
  digest="$(awk -v name="${filename}" '$2 == name { print $1 }' "${checksums}")"
  if [[ ! "${digest}" =~ ^[0-9a-f]{64}$ ]]; then
    echo "No pinned SHA-256 for ${filename} in ${checksums}." >&2
    return 2
  fi
  printf '%s\n' "${digest}"
}

verify_sha256() {
  local path="$1"
  local expected="$2"
  local actual
  actual="$(sha256sum "${path}" | awk '{ print $1 }')"
  if [ "${actual}" != "${expected}" ]; then
    echo "SHA-256 mismatch for ${path}: expected ${expected}, got ${actual}" >&2
    return 1
  fi
}

# Downloads to `.partial` and only then renames, so an interrupted run cannot
# leave a truncated archive that a later run trusts.
download_pinned() {
  local url="$1"
  local target="$2"
  local expected
  expected="$(expected_sha256 "$(basename "${target}")")"
  if [ -s "${target}" ] && verify_sha256 "${target}" "${expected}"; then
    return 0
  fi
  require_command curl
  echo "Downloading ${url}" >&2
  rm -f "${target}.partial"
  curl --fail --location --show-error "${url}" --output "${target}.partial"
  verify_sha256 "${target}.partial" "${expected}"
  mv "${target}.partial" "${target}"
}

fetch_anki() {
  local archive="${work_dir}/anki-${anki_version}-linux-x86_64.tar.zst"
  download_pinned \
    "https://github.com/ankitects/anki/releases/download/${anki_version}/anki-${anki_version}-linux-x86_64.tar.zst" \
    "${archive}"
  if [ ! -x "${anki_dir}/anki" ]; then
    require_command tar
    echo "Extracting ${archive}" >&2
    # The upstream archive's top-level directory is `anki-linux`, not the
    # versioned archive name, so this lands directly at ${anki_dir}.
    tar --use-compress-program=unzstd -xf "${archive}" -C "${work_dir}"
  fi
  if [ ! -x "${anki_dir}/anki" ]; then
    echo "Extracted archive did not contain anki-linux/anki: ${archive}" >&2
    return 1
  fi
}

# Unpacks AnkiConnect into the *fixture* add-on directory and rewrites its
# config to the isolated port. Both config.json and meta.json are written:
# Anki reads the operator-visible value from meta.json, so writing only
# config.json leaves the add-on listening on its default port.
fetch_ankiconnect() {
  local archive="${work_dir}/anki-connect-${ankiconnect_commit}.tar.gz"
  download_pinned \
    "https://github.com/FooSoft/anki-connect/archive/${ankiconnect_commit}.tar.gz" \
    "${archive}"
  if [ ! -f "${addon_dir}/__init__.py" ]; then
    require_command tar
    mkdir -p "${addon_dir}"
    tar -xzf "${archive}" -C "${work_dir}"
    cp "${work_dir}/anki-connect-${ankiconnect_commit}/plugin/"* "${addon_dir}/"
  fi
  python3 - "${addon_dir}" "${port}" <<'PY'
import json
import sys
from pathlib import Path

addon = Path(sys.argv[1])
port = int(sys.argv[2])
config = json.loads((addon / "config.json").read_text())
config["webBindPort"] = port
config["webBindAddress"] = "127.0.0.1"
config["apiKey"] = None
(addon / "config.json").write_text(json.dumps(config, indent=4, sort_keys=True) + "\n")
(addon / "meta.json").write_text(
    json.dumps({"name": "Kani AnkiConnect fixture", "config": config}, sort_keys=True) + "\n"
)
PY
}

# Seeds prefs21.db under the bundled interpreter so the defaults come from
# Anki's own aqt.profiles rather than a copy that drifts.
seed_profile() {
  if [ -s "${base_dir}/prefs21.db" ]; then
    return 0
  fi
  echo "Seeding ${base_dir}/prefs21.db for profile ${profile}" >&2
  (
    cd "${anki_dir}"
    PYTHONHOME="${anki_dir}/python" "${anki_dir}/python/bin/python3" \
      "${repo_root}/ci/scripts/seed_anki_desktop_profile.py" \
      --base "${base_dir}" \
      --profile "${profile}" \
      --defaults-from-anki
  )
}

write_launcher() {
  cat > "${work_dir}/start.sh" <<EOF
#!/bin/sh
set -eu
exec xvfb-run -a --server-args="-screen 0 1280x1024x24" \\
  "${anki_dir}/anki" -b "${base_dir}" -p "${profile}"
EOF
  chmod +x "${work_dir}/start.sh"

  # The patterns stay inside this file on purpose. \`pgrep -f\` matches the
  # caller's own command line too, so inlining them into an interactive shell
  # makes that shell kill itself.
  cat > "${work_dir}/stop.sh" <<'EOF'
#!/bin/sh
for p in $(pgrep -f 'anki-linux/anki' || true); do
  kill "$p" 2>/dev/null || true
done
sleep 3
for p in $(pgrep -f 'Xvfb' || true); do
  kill "$p" 2>/dev/null || true
done
sleep 1
echo stopped
EOF
  chmod +x "${work_dir}/stop.sh"
}

probe() {
  local action="$1"
  curl --silent --max-time 5 -X POST "${endpoint}" \
    -d "{\"action\":\"${action}\",\"version\":6}"
}

wait_for_ankiconnect() {
  local answer=""
  for _ in $(seq 1 60); do
    answer="$(probe version || true)"
    case "${answer}" in
      *'"result": 6'*) echo "AnkiConnect ready on ${endpoint}" >&2; return 0 ;;
    esac
    sleep 2
  done
  echo "AnkiConnect did not answer on ${endpoint} within 120s." >&2
  echo "Log: ${work_dir}/anki.log" >&2
  return 1
}

stop_fixture() {
  if [ -x "${work_dir}/stop.sh" ]; then
    setsid "${work_dir}/stop.sh" >"${work_dir}/stop.log" 2>&1 </dev/null &
    sleep 6
  fi
}

start_fixture() {
  require_command xvfb-run
  require_command python3
  mkdir -p "${work_dir}"

  if [ "${port}" = "${RESERVED_LIVE_PORT}" ]; then
    echo "Refusing to bind ${RESERVED_LIVE_PORT}: that is the operator's live AnkiConnect port." >&2
    exit 2
  fi

  fetch_anki
  fetch_ankiconnect
  seed_profile
  write_launcher

  if [ -n "$(probe version 2>/dev/null || true)" ]; then
    echo "Fixture already answering on ${endpoint}" >&2
    return 0
  fi

  echo "Starting Anki ${anki_version} (AnkiConnect ${ankiconnect_commit:0:8}) on ${endpoint}" >&2
  setsid "${work_dir}/start.sh" >"${work_dir}/anki.log" 2>&1 </dev/null &
  wait_for_ankiconnect
}

main() {
  case "${1:-}" in
    --stop)
      stop_fixture
      ;;
    --probe)
      if [ -z "${2:-}" ]; then
        echo "--probe needs an action name" >&2
        exit 2
      fi
      probe "$2"
      echo
      ;;
    "")
      start_fixture
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
}

main "$@"
