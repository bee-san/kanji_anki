from __future__ import annotations

import json
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
SCRIPT = REPO_ROOT / "ci" / "scripts" / "create_ankidroid_retired_lifecycle_fixtures.py"
FIELD_SEPARATOR = "\x1f"
PRIMARY_NOTE_ID = 1_700_000_000_002
SECOND_SUPPORT_NOTE_ID = 1_700_000_000_003


class CreateAnkiDroidRetiredLifecycleFixturesTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp_context = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp_context.cleanup)
        self.output_dir = Path(self.tmp_context.name) / "nested" / "retired-lifecycle"

    def generate(self) -> dict[str, object]:
        result = subprocess.run(
            [sys.executable, str(SCRIPT), str(self.output_dir)],
            cwd=REPO_ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=30,
        )
        self.assertEqual(0, result.returncode, result.stderr)
        manifest_path = Path(result.stdout.strip())
        self.assertEqual((self.output_dir / "manifest.json").resolve(), manifest_path.resolve())
        return json.loads(manifest_path.read_text(encoding="utf-8"))

    def connect_stage(self, manifest: dict[str, object], stage: str) -> sqlite3.Connection:
        stages = manifest["stages"]
        self.assertIsInstance(stages, dict)
        stage_meta = stages[stage]
        self.assertIsInstance(stage_meta, dict)
        db = sqlite3.connect(self.output_dir / stage_meta["file"])
        self.addCleanup(db.close)
        return db

    def test_cli_requires_output_directory(self) -> None:
        result = subprocess.run(
            [sys.executable, str(SCRIPT)],
            cwd=REPO_ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=30,
        )

        self.assertEqual(2, result.returncode)
        self.assertIn("usage: create_ankidroid_retired_lifecycle_fixtures.py OUTPUT_DIR", result.stderr)

    def test_manifest_lists_sanitized_lifecycle_stages_and_contract(self) -> None:
        manifest = self.generate()

        self.assertEqual("橋", manifest["target_kanji"])
        self.assertEqual(21, manifest["mature_days"])
        self.assertEqual(2, manifest["mature_support_threshold"])
        self.assertEqual(
            {"weak_below_threshold", "mature_at_threshold", "missing_route", "invalid_ord1"},
            set(manifest["stages"]),
        )
        for stage_meta in manifest["stages"].values():
            self.assertTrue((self.output_dir / stage_meta["file"]).is_file())
            self.assertRegex(stage_meta["sha256"], r"^[0-9a-f]{64}$")

    def test_weak_and_mature_stages_cross_exact_support_boundary(self) -> None:
        manifest = self.generate()

        weak = self.connect_stage(manifest, "weak_below_threshold")
        mature = self.connect_stage(manifest, "mature_at_threshold")
        weak_intervals = self.target_active_intervals(weak)
        mature_intervals = self.target_active_intervals(mature)

        self.assertEqual([20, 28], weak_intervals)
        self.assertEqual([21, 28], mature_intervals)
        self.assertEqual(1, sum(interval >= 21 for interval in weak_intervals))
        self.assertEqual(2, sum(interval >= 21 for interval in mature_intervals))
        self.assertEqual(1, self.target_suspended_count(weak))
        self.assertEqual(1, self.target_suspended_count(mature))
        support_meanings = [
            fields.split(FIELD_SEPARATOR)[2]
            for (fields,) in weak.execute(
                "SELECT flds FROM notes WHERE id IN (?, ?) ORDER BY id",
                (PRIMARY_NOTE_ID, SECOND_SUPPORT_NOTE_ID),
            )
        ]
        self.assertEqual(["bridge", "bridge"], support_meanings)

    def test_missing_route_keeps_nonempty_provider_snapshot_without_target(self) -> None:
        manifest = self.generate()
        db = self.connect_stage(manifest, "missing_route")

        expressions = [
            row[0].split(FIELD_SEPARATOR)[0]
            for row in db.execute("SELECT flds FROM notes ORDER BY id")
        ]

        self.assertEqual(["端"], expressions)
        self.assertEqual(1, db.execute("SELECT COUNT(*) FROM cards").fetchone()[0])
        self.assertEqual(0, len(self.target_active_intervals(db)))
        self.assertEqual(0, self.target_suspended_count(db))

    def test_invalid_ord1_fixture_is_isolated_from_supported_lifecycle_stages(self) -> None:
        manifest = self.generate()
        invalid = self.connect_stage(manifest, "invalid_ord1")
        weak = self.connect_stage(manifest, "weak_below_threshold")

        self.assertEqual([0, 1], [row[0] for row in invalid.execute("SELECT ord FROM cards WHERE nid=1700000000002 ORDER BY ord")])
        self.assertEqual([0], [row[0] for row in weak.execute("SELECT ord FROM cards WHERE nid=1700000000002 ORDER BY ord")])
        models = json.loads(invalid.execute("SELECT models FROM col").fetchone()[0])
        self.assertEqual([0, 1], [template["ord"] for template in models["1700000000000"]["tmpls"]])

    @staticmethod
    def target_active_intervals(db: sqlite3.Connection) -> list[int]:
        rows = db.execute(
            "SELECT n.flds, c.ivl FROM notes n JOIN cards c ON c.nid=n.id "
            "WHERE c.queue >= 0 ORDER BY c.id"
        ).fetchall()
        return [
            interval
            for fields, interval in rows
            if "橋" in fields.split(FIELD_SEPARATOR)[0]
        ]

    @staticmethod
    def target_suspended_count(db: sqlite3.Connection) -> int:
        rows = db.execute(
            "SELECT n.flds FROM notes n JOIN cards c ON c.nid=n.id WHERE c.queue < 0"
        ).fetchall()
        return sum("橋" in fields.split(FIELD_SEPARATOR)[0] for (fields,) in rows)


if __name__ == "__main__":
    unittest.main()
