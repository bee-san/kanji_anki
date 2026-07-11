from __future__ import annotations

import hashlib
import json
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
SCRIPT = REPO_ROOT / "ci" / "scripts" / "create_ankidroid_kiku_fixture.py"
FIELD_SEPARATOR = "\x1f"


def anki_checksum(value: str) -> int:
    return int(hashlib.sha1(value.encode("utf-8")).hexdigest()[:8], 16)


class CreateAnkiDroidKikuFixtureTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp_context = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp_context.cleanup)
        self.tmp_path = Path(self.tmp_context.name)
        self.output = self.tmp_path / "nested" / "kiku-provider-fixture.anki2"

    def run_generator(self, output: Path | None = None) -> subprocess.CompletedProcess[str]:
        args = [sys.executable, str(SCRIPT)]
        if output is not None:
            args.append(str(output))
        return subprocess.run(
            args,
            cwd=REPO_ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=30,
        )

    def connect_generated_collection(self) -> sqlite3.Connection:
        result = self.run_generator(self.output)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(self.output.exists())
        db = sqlite3.connect(self.output)
        self.addCleanup(db.close)
        return db

    def test_cli_requires_output_collection_path(self) -> None:
        result = self.run_generator()

        self.assertEqual(2, result.returncode)
        self.assertIn("usage: create_ankidroid_kiku_fixture.py OUTPUT.anki2", result.stderr)

    def test_cli_creates_parent_directories_and_overwrites_existing_file(self) -> None:
        self.output.parent.mkdir(parents=True)
        self.output.write_text("not sqlite")

        result = self.run_generator(self.output)

        self.assertEqual(result.returncode, 0, result.stderr)
        with sqlite3.connect(self.output) as db:
            self.assertEqual(11, db.execute("PRAGMA user_version").fetchone()[0])

    def test_generated_collection_has_expected_schema_counts_and_indexes(self) -> None:
        db = self.connect_generated_collection()

        tables = {
            row[0]
            for row in db.execute(
                "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"
            )
        }
        indexes = {
            row[0]
            for row in db.execute(
                "SELECT name FROM sqlite_master WHERE type='index' ORDER BY name"
            )
        }

        self.assertEqual({"cards", "col", "graves", "notes", "revlog"}, tables)
        self.assertTrue({"ix_cards_nid", "ix_cards_sched", "ix_notes_csum"}.issubset(indexes))
        self.assertEqual(1, db.execute("SELECT COUNT(*) FROM col").fetchone()[0])
        self.assertEqual(4, db.execute("SELECT COUNT(*) FROM notes").fetchone()[0])
        self.assertEqual(4, db.execute("SELECT COUNT(*) FROM cards").fetchone()[0])
        self.assertEqual(0, db.execute("SELECT COUNT(*) FROM revlog").fetchone()[0])
        self.assertEqual(0, db.execute("SELECT COUNT(*) FROM graves").fetchone()[0])

    def test_model_metadata_matches_kiku_note_type_and_single_ord_zero_template(self) -> None:
        db = self.connect_generated_collection()
        models_json, decks_json, dconf_json = db.execute(
            "SELECT models, decks, dconf FROM col WHERE id = 1"
        ).fetchone()

        models = json.loads(models_json)
        decks = json.loads(decks_json)
        dconf = json.loads(dconf_json)
        model = models["1700000000000"]

        self.assertEqual("Kiku", model["name"])
        self.assertEqual(
            ["Expression", "ExpressionReading", "MainDefinition", "Sentence", "Frequency", "FreqSort"],
            [field["name"] for field in model["flds"]],
        )
        self.assertEqual([0], [template["ord"] for template in model["tmpls"]])
        self.assertEqual("{{Expression}}", model["tmpls"][0]["qfmt"])
        self.assertIn("{{MainDefinition}}", model["tmpls"][0]["afmt"])
        self.assertEqual("Default", decks["1"]["name"])
        self.assertEqual([1, 10], dconf["1"]["new"]["delays"])
        self.assertEqual([10], dconf["1"]["lapse"]["delays"])

    def test_notes_use_anki_field_separator_tags_and_deterministic_checksums(self) -> None:
        db = self.connect_generated_collection()
        notes = db.execute(
            "SELECT id, guid, mid, tags, flds, sfld, csum, data FROM notes ORDER BY id"
        ).fetchall()

        self.assertEqual(
            [
                (1700000000001, "kani-ci-hako", "箱"),
                (1700000000002, "kani-ci-hashi", "橋"),
                (1700000000003, "kani-ci-hashi-chopsticks", "箸"),
                (1700000000004, "kani-ci-hashi-edge", "端"),
            ],
            [(note[0], note[1], note[5]) for note in notes],
        )
        self.assertEqual([1700000000000] * 4, [note[2] for note in notes])
        self.assertEqual(
            [" kiku_ci ", " kiku_ci kani_query_test ", " kiku_ci ", " kiku_ci "],
            [note[3] for note in notes],
        )
        self.assertEqual([""] * 4, [note[7] for note in notes])
        for note in notes:
            fields = note[4].split(FIELD_SEPARATOR)
            self.assertEqual(6, len(fields))
            self.assertEqual(anki_checksum(note[5]), note[6])
        self.assertEqual(["箱", "はこ", "box", "箱を開けた。", "100", "100"], notes[0][4].split(FIELD_SEPARATOR))
        self.assertEqual(["橋", "はし", "bridge", "橋を渡る。", "200", "200"], notes[1][4].split(FIELD_SEPARATOR))
        self.assertEqual(["箸", "はし", "chopsticks", "箸で食べる。", "300", "300"], notes[2][4].split(FIELD_SEPARATOR))
        self.assertEqual(["端", "はし", "edge", "端に寄せる。", "400", "400"], notes[3][4].split(FIELD_SEPARATOR))

    def test_cards_cover_suspended_and_active_provider_rows_with_fsrs_payloads(self) -> None:
        db = self.connect_generated_collection()
        cards = db.execute(
            "SELECT id, nid, did, ord, type, queue, due, ivl, factor, reps, lapses, data FROM cards ORDER BY id"
        ).fetchall()

        self.assertEqual(4, len(cards))
        self.assertEqual((1700000001001, 1700000000001, 1, 0, 2, -1, 0, 42, 2500, 80, 3), cards[0][:11])
        self.assertEqual((1700000001002, 1700000000002, 1, 0, 2, 2, 1, 7, 2500, 9, 3), cards[1][:11])
        self.assertEqual((1700000001003, 1700000000003, 1, 0, 2, 2, 2, 28, 2500, 18, 3), cards[2][:11])
        self.assertEqual((1700000001004, 1700000000004, 1, 0, 2, 2, 3, 35, 2500, 24, 3), cards[3][:11])
        self.assertEqual({"s": 12.5, "d": 7.0, "r": 0.42}, json.loads(cards[0][11]))
        self.assertEqual({"s": 6.0, "d": 5.0, "r": 0.80}, json.loads(cards[1][11]))
        self.assertEqual({"s": 18.0, "d": 6.0, "r": 0.75}, json.loads(cards[2][11]))
        self.assertEqual({"s": 22.0, "d": 6.5, "r": 0.72}, json.loads(cards[3][11]))

    def test_active_homophone_trio_is_weak_and_has_valid_reading_evidence(self) -> None:
        db = self.connect_generated_collection()
        rows = db.execute(
            "SELECT n.flds, c.ivl, c.lapses, c.data "
            "FROM notes n JOIN cards c ON c.nid = n.id "
            "WHERE c.queue >= 0 ORDER BY n.id"
        ).fetchall()

        evidence = {}
        for fields_text, interval_days, lapses, data_text in rows:
            expression, reading, _, sentence, _, _ = fields_text.split(FIELD_SEPARATOR)
            fsrs = json.loads(data_text)
            evidence[expression] = (reading, sentence, interval_days, lapses, fsrs)

        self.assertEqual({"橋", "箸", "端"}, set(evidence))
        for expression, (reading, sentence, _, lapses, fsrs) in evidence.items():
            self.assertEqual("はし", reading, expression)
            self.assertTrue(sentence.strip(), expression)
            self.assertTrue(fsrs.keys() >= {"s", "d", "r"}, expression)
            self.assertGreater(fsrs["s"], 0.0, expression)
            self.assertTrue(1.0 <= fsrs["d"] <= 10.0, expression)
            self.assertTrue(0.0 <= fsrs["r"] <= 1.0, expression)
            self.assertTrue(fsrs["d"] >= 7.5 or lapses >= 3, expression)
        for expression in ("箸", "端"):
            self.assertGreaterEqual(evidence[expression][2], 21, expression)


if __name__ == "__main__":
    unittest.main()
