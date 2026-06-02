import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from typing import Dict, Optional, Tuple


REPO_ROOT = Path(__file__).resolve().parents[2]
SCRIPT = REPO_ROOT / "ci" / "scripts" / "run_ankidroid_fixture.sh"


def write_executable(path: Path, content: str) -> None:
    path.write_text(content)
    path.chmod(0o755)


def run_fixture(
    tmp_path: Path,
    fake_adb_body: str,
    extra_env: Optional[Dict[str, str]] = None,
) -> subprocess.CompletedProcess[str]:
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
    if extra_env:
        env.update(extra_env)

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
{extra_cases}
  logcat\\ -d*) exit 0 ;;
  wait-for-device*) exit 0 ;;
  install*) exit 0 ;;
  shell\\ monkey*) exit 0 ;;
  shell\\ am\\ start*) exit 0 ;;
  root*) exit 0 ;;
  push*) exit 0 ;;
  shell\\ *stat*) exit 0 ;;
  shell\\ *chmod*) exit 0 ;;
  shell\\ *prefs=*) exit 0 ;;
  shell\\ am\\ force-stop*) exit 0 ;;
  shell\\ pm\\ grant*) exit 0 ;;
  logcat\\ -c*) exit 0 ;;
  shell\\ content\\ query*) echo 'Row: 0 _id=123, name=Kiku'; exit 0 ;;
  shell\\ mkdir*) exit 0 ;;
  shell\\ am\\ instrument*) echo 'OK (45 tests)'; exit 0 ;;
  *) exit 0 ;;
esac
"""


class RunAnkiDroidFixtureTest(unittest.TestCase):
    def run_fixture_in_tmp(
        self,
        fake_adb_body: str,
        extra_env: Optional[Dict[str, str]] = None,
    ) -> Tuple[subprocess.CompletedProcess[str], Path]:
        tmp_context = tempfile.TemporaryDirectory()
        self.addCleanup(tmp_context.cleanup)
        tmp_path = Path(tmp_context.name)
        return run_fixture(tmp_path, fake_adb_body, extra_env=extra_env), tmp_path

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
        self.assertEqual((tmp_path / "mkdir-count").read_text().strip(), "4")

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

    def test_fixture_retries_transient_instrumentation_process_crash(self):
        fake_adb = base_fake_adb(
            """  shell\\ am\\ instrument*)
    count_file="$RUNNER_TEMP/instrument-count"
    count=$(cat "$count_file" 2>/dev/null || echo 0)
    count=$((count + 1))
    echo "$count" > "$count_file"
    if [ "$count" -lt 2 ]; then
      echo 'INSTRUMENTATION_RESULT: shortMsg=Process crashed.'
      exit 0
    fi
    echo 'OK (45 tests)'
    exit 0 ;;
"""
        )

        result, tmp_path = self.run_fixture_in_tmp(fake_adb)

        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertEqual((tmp_path / "instrument-count").read_text().strip(), "2")
        self.assertIn("transient runner/process failure", result.stdout)

    def test_fixture_fails_after_repeated_instrumentation_process_crashes(self):
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
        self.assertIn("Instrumentation reported a non-retriable failure", result.stdout)

    def test_fixture_does_not_retry_known_fake_provider_classpath_crash(self):
        fake_adb = base_fake_adb(
            """  logcat\\ -d*)
    cat <<'OUT'
06-02 08:02:10.733  3186  3186 E AndroidRuntime: FATAL EXCEPTION: main
06-02 08:02:10.733  3186  3186 E AndroidRuntime: Unable to instantiate provider dev.bee.kanjianki.anki.FakeAnkiDroidProvider
06-02 08:02:10.733  3186  3186 E AndroidRuntime: java.lang.NoClassDefFoundError: Failed resolution of: Lkotlin/jvm/internal/Intrinsics;
OUT
    exit 0 ;;
  shell\\ am\\ instrument*)
    count_file="$RUNNER_TEMP/instrument-count"
    count=$(cat "$count_file" 2>/dev/null || echo 0)
    count=$((count + 1))
    echo "$count" > "$count_file"
    echo 'INSTRUMENTATION_RESULT: shortMsg=Process crashed.'
    exit 0 ;;
"""
        )

        result, tmp_path = self.run_fixture_in_tmp(fake_adb)

        self.assertNotEqual(result.returncode, 0)
        self.assertEqual((tmp_path / "instrument-count").read_text().strip(), "1")
        self.assertIn("Fake AnkiDroid provider classpath crash", result.stdout)
        self.assertIn("not retrying", result.stdout)

    def test_fixture_does_not_retry_assertion_failures(self):
        fake_adb = base_fake_adb(
            """  shell\\ am\\ instrument*)
    count_file="$RUNNER_TEMP/instrument-count"
    count=$(cat "$count_file" 2>/dev/null || echo 0)
    count=$((count + 1))
    echo "$count" > "$count_file"
    cat <<'OUT'
INSTRUMENTATION_STATUS: numtests=45
FAILURES!!!
Tests run: 45,  Failures: 2
OUT
    exit 0 ;;
"""
        )

        result, tmp_path = self.run_fixture_in_tmp(fake_adb)

        self.assertNotEqual(result.returncode, 0)
        self.assertEqual((tmp_path / "instrument-count").read_text().strip(), "1")
        self.assertIn("Instrumentation reported a failure", result.stdout)

    def test_fixture_falls_back_to_explicit_ankidroid_activity_start(self):
        fake_adb = base_fake_adb(
            """  shell\\ monkey*) exit 1 ;;
  shell\\ am\\ start*) exit 0 ;;
"""
        )

        result, tmp_path = self.run_fixture_in_tmp(fake_adb)

        self.assertEqual(result.returncode, 0, result.stdout)
        adb_calls = (tmp_path / "adb-calls.log").read_text()
        self.assertIn("shell monkey -p com.ichi2.anki 1", adb_calls)
        self.assertIn("shell am start -W -n com.ichi2.anki/.IntentHandler", adb_calls)

    def test_fixture_recreates_ankidroid_dir_before_permission_repair(self):
        fake_adb = base_fake_adb(
            """  shell\\ *chmod*)
    case "$*" in
      *"mkdir -p /storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid/collection.media"*) exit 0 ;;
      *) echo 'chmod: /storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid: No such file or directory' >&2; exit 1 ;;
    esac ;;
"""
        )

        result, tmp_path = self.run_fixture_in_tmp(fake_adb)

        self.assertEqual(result.returncode, 0, result.stdout)
        adb_calls = (tmp_path / "adb-calls.log").read_text()
        self.assertIn(
            "mkdir -p /storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid/collection.media",
            adb_calls,
        )

    def test_fixture_repairs_ankidroid_dir_after_app_installs_before_provider_probe(self):
        fake_adb = base_fake_adb(
            """  shell\\ pm\\ grant*)
    touch "$RUNNER_TEMP/app-installed-and-granted"
    exit 0 ;;
  shell\\ *chmod*)
    if [ -f "$RUNNER_TEMP/app-installed-and-granted" ]; then
      touch "$RUNNER_TEMP/repaired-after-app-installs"
    fi
    exit 0 ;;
  shell\\ content\\ query*)
    if [ -f "$RUNNER_TEMP/repaired-after-app-installs" ]; then
      echo 'Row: 0 _id=123, name=Kiku'
    else
      echo 'No result found.'
    fi
    exit 0 ;;
"""
        )

        result, tmp_path = self.run_fixture_in_tmp(fake_adb)

        self.assertEqual(result.returncode, 0, result.stdout)
        adb_calls = (tmp_path / "adb-calls.log").read_text().splitlines()
        last_repair = max(i for i, call in enumerate(adb_calls) if "chmod -R" in call)
        first_probe = next(i for i, call in enumerate(adb_calls) if "content query" in call)
        self.assertLess(last_repair, first_probe)

    def test_fixture_retries_provider_probe_when_permission_repair_fails(self):
        fake_adb = base_fake_adb(
            """  shell\\ pm\\ grant*)
    touch "$RUNNER_TEMP/app-installed-and-granted"
    exit 0 ;;
  shell\\ *chmod*)
    if [ -f "$RUNNER_TEMP/app-installed-and-granted" ]; then
      count_file="$RUNNER_TEMP/post-grant-repair-count"
      count=$(cat "$count_file" 2>/dev/null || echo 0)
      count=$((count + 1))
      echo "$count" > "$count_file"
      if [ "$count" -lt 2 ]; then
        echo 'chmod: /storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid: Permission denied' >&2
        exit 1
      fi
    fi
    exit 0 ;;
  shell\\ content\\ query*)
    echo 'Row: 0 _id=123, name=Kiku'
    exit 0 ;;
"""
        )

        result, tmp_path = self.run_fixture_in_tmp(fake_adb)

        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertEqual((tmp_path / "post-grant-repair-count").read_text().strip(), "2")
        self.assertIn("AnkiDroid provider model readiness failed on attempt 1/12", result.stdout)

    def test_fixture_retries_provider_probe_until_models_are_visible(self):
        fake_adb = base_fake_adb(
            """  shell\\ content\\ query*)
    count_file="$RUNNER_TEMP/provider-probe-count"
    count=$(cat "$count_file" 2>/dev/null || echo 0)
    count=$((count + 1))
    echo "$count" > "$count_file"
    if [ "$count" -lt 2 ]; then
      echo 'No result found.'
    else
      echo 'Row: 0 _id=123, name=Kiku'
    fi
    exit 0 ;;
"""
        )

        result, tmp_path = self.run_fixture_in_tmp(fake_adb)

        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertEqual((tmp_path / "provider-probe-count").read_text().strip(), "2")
        self.assertIn("AnkiDroid provider model readiness failed on attempt 1/12", result.stdout)

    def test_fixture_defaults_to_one_note_for_sanitized_fixture(self):
        result, tmp_path = self.run_fixture_in_tmp(base_fake_adb())

        self.assertEqual(result.returncode, 0, result.stdout)
        adb_calls = (tmp_path / "adb-calls.log").read_text()
        self.assertIn("kanjiLiveAnkiDroid true", adb_calls)
        self.assertIn("kanjiLiveMinimumNotes 1", adb_calls)

    def test_fixture_can_omit_lowered_minimum_notes_for_real_collection_gate(self):
        result, tmp_path = self.run_fixture_in_tmp(
            base_fake_adb(),
            extra_env={"KANJI_LIVE_MINIMUM_NOTES": ""},
        )

        self.assertEqual(result.returncode, 0, result.stdout)
        adb_calls = (tmp_path / "adb-calls.log").read_text()
        self.assertIn("kanjiLiveAnkiDroid true", adb_calls)
        self.assertNotIn("kanjiLiveMinimumNotes", adb_calls)

    def test_fixture_allows_overriding_instrumentation_classes(self):
        result, tmp_path = self.run_fixture_in_tmp(
            base_fake_adb(),
            extra_env={"KANJI_LIVE_TEST_CLASSES": "dev.bee.kanjianki.anki.RealAnkiDroidLiveProviderInstrumentedTest"},
        )

        self.assertEqual(result.returncode, 0, result.stdout)
        adb_calls = (tmp_path / "adb-calls.log").read_text()
        self.assertIn("dev.bee.kanjianki.anki.RealAnkiDroidLiveProviderInstrumentedTest", adb_calls)
        self.assertNotIn("MainActivityInstrumentedTest#testManualSyncButtonWorksAgainstLiveAnkiDroid", adb_calls)


if __name__ == "__main__":
    unittest.main()
