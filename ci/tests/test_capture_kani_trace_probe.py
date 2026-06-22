from __future__ import annotations

import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
SCRIPT = REPO_ROOT / "ci" / "scripts" / "capture_kani_trace_probe.sh"


def write_executable(path: Path, content: str) -> None:
    path.write_text(content)
    path.chmod(0o755)


def base_fake_adb(extra_cases: str = "", device_present: bool = True) -> str:
    device_listing = "List of devices attached\nemulator-5554\tdevice\n" if device_present else "List of devices attached\n\n"
    return rf"""#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$RUNNER_TEMP/adb-calls.log"
case "$*" in
{extra_cases}
  devices*)
    cat <<'OUT'
{device_listing}OUT
    exit 0 ;;
  wait-for-device*) exit 0 ;;
  install*) exit 0 ;;
  shell\ settings\ put*) exit 0 ;;
  shell\ am\ force-stop*) exit 0 ;;
  logcat\ -c*) exit 0 ;;
  shell\ getprop\ ro.product.manufacturer*) echo Google; exit 0 ;;
  shell\ getprop\ ro.product.model*) echo sdk_gphone64_arm64; exit 0 ;;
  shell\ getprop\ ro.build.version.sdk*) echo 35; exit 0 ;;
  *) exit 0 ;;
esac
"""


class CaptureKaniTraceProbeTest(unittest.TestCase):
    def run_probe(self, fake_adb_body: str, requested_route: str = "stats") -> tuple[subprocess.CompletedProcess[str], Path]:
        tmp_context = tempfile.TemporaryDirectory()
        self.addCleanup(tmp_context.cleanup)
        tmp_path = Path(tmp_context.name)

        bin_dir = tmp_path / "bin"
        bin_dir.mkdir()
        write_executable(bin_dir / "adb", fake_adb_body)
        write_executable(bin_dir / "sleep", "#!/usr/bin/env bash\nexit 0\n")

        env = os.environ.copy()
        env["PATH"] = f"{bin_dir}:{env['PATH']}"
        env["RUNNER_TEMP"] = str(tmp_path)
        env["KANI_TRACE_PROBE_DIR"] = str(tmp_path / "probe")

        result = subprocess.run(
            ["bash", str(SCRIPT), requested_route],
            cwd=REPO_ROOT,
            env=env,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=30,
        )
        return result, tmp_path

    def test_probe_writes_device_pending_manifest_without_device(self) -> None:
        result, tmp_path = self.run_probe(base_fake_adb(device_present=False), requested_route="stats")

        self.assertEqual(0, result.returncode, result.stdout)
        manifest = json.loads((tmp_path / "probe" / "manifest.json").read_text())
        self.assertEqual("device_pending", manifest["capture_status"])
        self.assertEqual("stats", manifest["requested_route"])
        self.assertEqual([], manifest["routes"])
        self.assertEqual([], manifest["files"])
        self.assertTrue(any("adb device" in note.lower() for note in manifest["notes"]))

    def test_probe_records_pid_filtered_logcat_and_gfxinfo_for_stats_route(self) -> None:
        fake_adb = base_fake_adb(
            extra_cases="""  shell\\ am\\ start\\ -W\\ -n\\ dev.bee.kanjianki/.MainActivity\\ --es\\ dev.bee.kanjianki.extra.SCREENSHOT_ROUTE\\ stats*)
    cat <<'OUT'
Starting: Intent { cmp=dev.bee.kanjianki/.MainActivity }
Status: ok
Activity: dev.bee.kanjianki/.MainActivity
ThisTime: 321
TotalTime: 456
WaitTime: 500
Complete
OUT
    exit 0 ;;
  shell\\ pidof\\ -s\\ dev.bee.kanjianki*)
    count_file="$RUNNER_TEMP/pidof-count"
    count=$(cat "$count_file" 2>/dev/null || echo 0)
    count=$((count + 1))
    echo "$count" > "$count_file"
    if [ "$count" -lt 2 ]; then
      exit 0
    fi
    echo 4321
    exit 0 ;;
  logcat\\ -d\\ --pid\\ 4321\\ -s\\ KaniPerf\\ Choreographer\\ HWUI*)
    cat <<'OUT'
06-21 12:00:00.123  4321  4321 D KaniPerf: perf section=kani.load.stats-route duration_ms=42.00
06-21 12:00:00.456  4321  4321 D Choreographer: Skipped 71 frames!
06-21 12:00:00.789  4321  4321 D HWUI: Janky frame 1
OUT
    exit 0 ;;
  shell\\ dumpsys\\ gfxinfo\\ dev.bee.kanjianki*)
    cat <<'OUT'
Applications Graphics Acceleration Info:
Janky frames: 71 (83.52%)
OUT
    exit 0 ;;
""",
        )

        result, tmp_path = self.run_probe(fake_adb, requested_route="stats")

        self.assertEqual(0, result.returncode, result.stdout)
        adb_calls = (tmp_path / "adb-calls.log").read_text()
        self.assertIn(
            "shell am start -W -n dev.bee.kanjianki/.MainActivity --es dev.bee.kanjianki.extra.SCREENSHOT_ROUTE stats",
            adb_calls,
        )
        self.assertIn("shell pidof -s dev.bee.kanjianki", adb_calls)
        self.assertIn("logcat -d --pid 4321 -s KaniPerf Choreographer HWUI", adb_calls)
        self.assertIn("shell dumpsys gfxinfo dev.bee.kanjianki", adb_calls)
        self.assertNotIn("uiautomator", adb_calls.lower())

        root_manifest = json.loads((tmp_path / "probe" / "manifest.json").read_text())
        self.assertEqual("complete", root_manifest["capture_status"])
        self.assertEqual(["stats"], root_manifest["routes"])
        self.assertIn("stats/am-start.txt", root_manifest["files"])
        self.assertIn("stats/manifest.json", root_manifest["manifest_files"])

        stats_dir = tmp_path / "probe" / "stats"
        manifest = json.loads((stats_dir / "manifest.json").read_text())
        self.assertEqual("complete", manifest["capture_status"])
        self.assertEqual("stats", manifest["requested_route"])
        self.assertEqual("dev.bee.kanjianki", manifest["package"])
        self.assertIn("am-start.txt", manifest["files"])
        self.assertIn("logcat.txt", manifest["files"])
        self.assertIn("gfxinfo.txt", manifest["files"])
        self.assertTrue(any("app-pid" in note.lower() for note in manifest["notes"]))
        self.assertIn("ThisTime: 321", (stats_dir / "am-start.txt").read_text())
        self.assertEqual("4321", (stats_dir / "pid.txt").read_text().strip())
        self.assertIn("KaniPerf", (stats_dir / "logcat.txt").read_text())
        self.assertIn("Janky frames: 71", (stats_dir / "gfxinfo.txt").read_text())
        self.assertEqual("2", (tmp_path / "pidof-count").read_text().strip())


if __name__ == "__main__":
    unittest.main()
