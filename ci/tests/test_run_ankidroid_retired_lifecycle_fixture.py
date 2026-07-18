from __future__ import annotations

import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
SCRIPT = REPO_ROOT / "ci" / "scripts" / "run_ankidroid_retired_lifecycle_fixture.sh"
STAGES = (
    "weak_initial",
    "mature_retire",
    "mature_repeat",
    "weak_reopen",
    "missing_route_retire",
    "weak_reopen_after_missing",
    "invalid_ord1_fail_closed",
)


class RunAnkiDroidRetiredLifecycleFixtureTest(unittest.TestCase):
    def run_script(self, *args: str, env: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
        merged_env = os.environ.copy()
        if env:
            merged_env.update(env)
        return subprocess.run(
            ["bash", str(SCRIPT), *args],
            cwd=REPO_ROOT,
            env=merged_env,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=30,
        )

    def test_requires_fixture_directory(self) -> None:
        result = self.run_script()

        self.assertEqual(2, result.returncode)
        self.assertIn("usage: run_ankidroid_retired_lifecycle_fixture.sh FIXTURE_DIR", result.stderr)

    def test_dry_run_validates_manifest_and_reports_ordered_stateful_phases(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            fixture_dir = Path(tmp)
            stage_files = {
                "weak_below_threshold": "weak.anki2",
                "mature_at_threshold": "mature.anki2",
                "missing_route": "missing.anki2",
                "invalid_ord1": "invalid.anki2",
            }
            for filename in stage_files.values():
                (fixture_dir / filename).write_bytes(b"fixture")
            (fixture_dir / "manifest.json").write_text(
                json.dumps(
                    {
                        "target_kanji": "橋",
                        "stages": {
                            name: {"file": filename, "sha256": "0" * 64}
                            for name, filename in stage_files.items()
                        },
                    }
                ),
                encoding="utf-8",
            )

            result = self.run_script(
                str(fixture_dir),
                env={"KANJI_RETIRED_LIFECYCLE_DRY_RUN": "1"},
            )

        self.assertEqual(0, result.returncode, result.stderr)
        phase_lines = [line for line in result.stdout.splitlines() if line.startswith("phase=")]
        self.assertEqual([f"phase={stage}" for stage in STAGES], phase_lines)

    def test_rejects_incomplete_fixture_set_before_touching_device(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            fixture_dir = Path(tmp)
            (fixture_dir / "manifest.json").write_text(
                json.dumps({"target_kanji": "橋", "stages": {}}),
                encoding="utf-8",
            )
            result = self.run_script(str(fixture_dir))

        self.assertEqual(2, result.returncode)
        self.assertIn("manifest missing stage weak_below_threshold", result.stderr)

    def test_script_runs_only_the_guarded_lifecycle_probe_and_checks_instrumentation_result(self) -> None:
        source = SCRIPT.read_text(encoding="utf-8")

        self.assertIn("probe_class=\"dev.bee.kanjianki.RetiredLifecycleRealProviderInstrumentedTest#lifecycleStage\"", source)
        self.assertIn("-e kanjiRetiredLifecycle true", source)
        self.assertIn("/storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid/collection.anki2", source)
        self.assertIn("/storage/emulated/0/AnkiDroid/collection.anki2", source)
        self.assertIn("repair_collection_ownership", source)
        self.assertIn(
            "owner_gid=\\$(stat -c '%g' /storage/emulated/0/Android/data/com.ichi2.anki",
            source,
        )
        self.assertIn('chown -R \\"\\${owner_uid}\\":\\"\\${owner_gid}\\"', source)
        self.assertNotIn("ext_data_rw", source)
        self.assertIn("FAILURES!!!", source)
        self.assertIn("^OK \\([0-9]+ tests?\\)", source)


if __name__ == "__main__":
    unittest.main()
