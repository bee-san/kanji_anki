import os
import subprocess
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
SCRIPT = REPO_ROOT / "ci" / "scripts" / "run_ankidroid_fixture.sh"


def write_executable(path: Path, content: str) -> None:
    path.write_text(content)
    path.chmod(0o755)


def run_fixture(tmp_path: Path, fake_adb_body: str) -> subprocess.CompletedProcess[str]:
    bin_dir = tmp_path / "bin"
    bin_dir.mkdir()
    write_executable(tmp_path / "gradlew", "#!/usr/bin/env bash\nexit 0\n")
    write_executable(bin_dir / "sleep", "#!/usr/bin/env bash\nexit 0\n")
    write_executable(bin_dir / "adb", fake_adb_body)

    ankidroid = tmp_path / "ankidroid.apk"
    collection = tmp_path / "collection.anki2"
    ankidroid.write_text("apk")
    collection.write_text("collection")

    env = os.environ.copy()
    env["PATH"] = f"{bin_dir}:{env['PATH']}"
    env["RUNNER_TEMP"] = str(tmp_path)

    return subprocess.run(
        ["bash", str(SCRIPT), str(ankidroid), str(collection)],
        cwd=tmp_path,
        env=env,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        timeout=30,
    )


def base_fake_adb(extra_cases: str = "") -> str:
    return f"""#!/usr/bin/env bash
set -euo pipefail
printf '%s\\n' "$*" >> "$RUNNER_TEMP/adb-calls.log"
case "$*" in
  logcat\\ -d*) exit 0 ;;
  wait-for-device*) exit 0 ;;
  install*) exit 0 ;;
  shell\\ monkey*) exit 0 ;;
  root*) exit 0 ;;
  push*) exit 0 ;;
  shell\\ *stat*) exit 0 ;;
  shell\\ *chmod*) exit 0 ;;
  shell\\ *prefs=*) exit 0 ;;
  shell\\ am\\ force-stop*) exit 0 ;;
  shell\\ pm\\ grant*) exit 0 ;;
  logcat\\ -c*) exit 0 ;;
{extra_cases}
  shell\\ content\\ query*) echo 'Row: 0 _id=123, name=Kiku'; exit 0 ;;
  shell\\ mkdir*) exit 0 ;;
  shell\\ am\\ instrument*) echo 'OK (45 tests)'; exit 0 ;;
  *) exit 0 ;;
esac
"""


class RunAnkiDroidFixtureTest(unittest.TestCase):
    def run_fixture_in_tmp(self, fake_adb_body: str) -> tuple[subprocess.CompletedProcess[str], Path]:
        tmp_context = tempfile.TemporaryDirectory()
        self.addCleanup(tmp_context.cleanup)
        tmp_path = Path(tmp_context.name)
        return run_fixture(tmp_path, fake_adb_body), tmp_path

    def test_fixture_retries_transient_external_storage_mount_failure(self):
        fake_adb = base_fake_adb(
            """  shell\\ mkdir*)
    count_file="$RUNNER_TEMP/mkdir-count"
    count=$(cat "$count_file" 2>/dev/null || echo 0)
    count=$((count + 1))
    echo "$count" > "$count_file"
    if [ "$count" -lt 2 ]; then
      echo "mkdir: '/storage/emulated/0': Transport endpoint is not connected" >&2
      exit 1
    fi
    exit 0 ;;
"""
        )

        result, tmp_path = self.run_fixture_in_tmp(fake_adb)

        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertEqual((tmp_path / "mkdir-count").read_text().strip(), "2")

    def test_fixture_fails_when_instrumentation_output_contains_failures(self):
        fake_adb = base_fake_adb(
            """  shell\\ am\\ instrument*)
    cat <<'OUT'
INSTRUMENTATION_STATUS: numtests=45
FAILURES!!!
Tests run: 45,  Failures: 2
OUT
    exit 0 ;;
"""
        )

        result, _ = self.run_fixture_in_tmp(fake_adb)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("FAILURES!!!", result.stdout)
        self.assertIn("Instrumentation reported a failure", result.stdout)

    def test_fixture_fails_when_instrumentation_process_crashes(self):
        fake_adb = base_fake_adb(
            """  shell\\ am\\ instrument*)
    echo 'INSTRUMENTATION_RESULT: shortMsg=Process crashed.'
    exit 0 ;;
"""
        )

        result, _ = self.run_fixture_in_tmp(fake_adb)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Process crashed", result.stdout)
        self.assertIn("Instrumentation reported a failure", result.stdout)


if __name__ == "__main__":
    unittest.main()
