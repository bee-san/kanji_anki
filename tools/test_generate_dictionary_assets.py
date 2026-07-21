#!/usr/bin/env python3

from __future__ import annotations

import gzip
import json
import sqlite3
import tempfile
import unittest
from contextlib import closing
from pathlib import Path

from tools import generate_dictionary_assets as generator


KANJIDIC_FIXTURE = """<?xml version="1.0" encoding="UTF-8"?>
<kanjidic2>
<header><file_version>4</file_version><database_version>2026-129</database_version><date_of_creation>2026-05-09</date_of_creation></header>
<character>
<literal>膨</literal>
<radical><rad_value rad_type="classical">130</rad_value></radical>
<misc><grade>8</grade><stroke_count>16</stroke_count><freq>2077</freq></misc>
<dic_number><query_code><q_code qc_type="skip">1-4-12</q_code></query_code></dic_number>
<reading_meaning>
<rmgroup>
<reading r_type="ja_on">ボウ</reading>
<meaning>swell</meaning><meaning>get fat</meaning>
</rmgroup>
<nanori>ふく</nanori>
</reading_meaning>
</character>
<character>
<literal>𠂉</literal>
<misc><stroke_count>2</stroke_count></misc>
</character>
<character>
<literal>日本</literal>
<misc><stroke_count>4</stroke_count></misc>
</character>
</kanjidic2>
"""


class GenerateDictionaryAssetsTest(unittest.TestCase):
    def test_parses_kanjidic2_kanji_fields_without_skip_codes(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp) / "kanjidic2.xml.gz"
            source.write_bytes(gzip.compress(KANJIDIC_FIXTURE.encode("utf-8")))

            metadata, rows = generator.parse_kanjidic2(source)

            self.assertEqual("2026-129", metadata["database_version"])
            self.assertEqual(2, len(rows))
            self.assertEqual("膨", rows[0].literal)
            self.assertEqual(("swell", "get fat"), rows[0].meanings)
            self.assertEqual(("ボウ",), rows[0].on_readings)
            self.assertEqual(16, rows[0].stroke_count)
            self.assertEqual("𠂉", rows[1].literal)
            self.assertEqual((), rows[1].meanings)
            self.assertFalse(hasattr(rows[0], "skip"))

    def test_parses_existing_jiten_rank_csv_shape(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp) / "jiten.csv"
            source.write_text("Kanji,Rank\n人,1\n裂,824\n1,日\nword,not-rank\n", encoding="utf-8")

            ranks = generator.parse_jiten_ranks(source)

            self.assertEqual(1, ranks["人"])
            self.assertEqual(824, ranks["裂"])
            self.assertEqual(1, ranks["日"])
            self.assertNotIn("word", ranks)

    def test_jiten_ranks_require_single_kanji_and_signed_int32_rank(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp) / "jiten.csv"
            source.write_text(
                "\n".join(
                    [
                        "Kanji,Rank",
                        "日,2147483647",
                        "-2147483648,提",
                        "大,2147483648",
                        "-2147483649,小",
                        "中,1_000",
                        "+7,上",
                        "日本,4",
                        "櫛,5",
                        "6,𰀀",
                    ]
                ),
                encoding="utf-8",
            )

            ranks = generator.parse_jiten_ranks(source)

            self.assertEqual(
                {
                    "日": generator.INT32_MAX,
                    "提": generator.INT32_MIN,
                    "櫛": 5,
                    "𰀀": 6,
                },
                ranks,
            )
            self.assertEqual(0, generator.int_or_zero("2147483648"))
            self.assertEqual(0, generator.int_or_zero("-1"))

    def test_writes_database_schema_metadata_and_jiten_join(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            kanjidic = root / "kanjidic2.xml.gz"
            jiten = root / "jiten_kanji_rank.csv"
            db_path = root / "kanji_dictionary.db"
            kanjidic.write_bytes(gzip.compress(KANJIDIC_FIXTURE.encode("utf-8")))
            jiten.write_text("Kanji,Rank\n膨,77\n", encoding="utf-8")
            metadata, rows = generator.parse_kanjidic2(kanjidic)
            ranks = generator.parse_jiten_ranks(jiten)

            generator.write_database(rows, ranks, db_path, "2026-05-09", kanjidic, jiten, metadata)

            with closing(sqlite3.connect(db_path)) as db:
                columns = {row[1] for row in db.execute("PRAGMA table_info(kanji)")}
                rank_columns = {row[1] for row in db.execute("PRAGMA table_info(jiten_ranks)")}
                meta = dict(db.execute("SELECT key, value FROM dictionary_meta"))
                row = db.execute("SELECT meanings, on_readings, stroke_count, kanjidic_frequency, jiten_rank FROM kanji WHERE literal='膨'").fetchone()
                rank_row = db.execute("SELECT rank FROM jiten_ranks WHERE literal='膨'").fetchone()
            self.assertEqual(
                {
                    "literal",
                    "meanings",
                    "on_readings",
                    "kun_readings",
                    "nanori_readings",
                    "stroke_count",
                    "grade",
                    "radical",
                    "kanjidic_frequency",
                    "jiten_rank",
                },
                columns,
            )
            self.assertEqual({"literal", "rank"}, rank_columns)
            self.assertEqual("1", meta["schema_version"])
            self.assertEqual("2026-129", meta["kanjidic2_database_version"])
            self.assertEqual("1", meta["jiten_rank_count"])
            self.assertEqual("1", meta["jiten_rank_join_count"])
            self.assertEqual("false", meta["skip_codes_imported"])
            self.assertEqual(("swell\u001fget fat", "ボウ", 16, 2077, 77), row)
            self.assertEqual((77,), rank_row)

    def test_manifest_records_update_package_sources_hashes_and_no_word_dictionary(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            kanjidic = root / "kanjidic2.xml.gz"
            jiten = root / "jiten_kanji_rank.csv"
            db_path = root / "kanji_dictionary.db"
            checksum = root / "kanji_dictionary.db.sha256"
            manifest = root / "dictionary_sources.json"
            kanjidic.write_bytes(gzip.compress(KANJIDIC_FIXTURE.encode("utf-8")))
            jiten.write_text("Kanji,Rank\n膨,77\n", encoding="utf-8")
            metadata, rows = generator.parse_kanjidic2(kanjidic)
            ranks = generator.parse_jiten_ranks(jiten)
            generator.write_database(rows, ranks, db_path, "2026-05-09", kanjidic, jiten, metadata)
            generator.write_sha256_file(checksum, db_path)

            generator.write_manifest(
                manifest,
                "2026-05-09",
                kanjidic,
                jiten,
                db_path,
                checksum,
                len(rows),
                1,
                1,
                metadata,
            )

            data = json.loads(manifest.read_text(encoding="utf-8"))
            self.assertEqual(["kanji_dictionary.db", "dictionary_sources.json", "kanji_dictionary.db.sha256"], data["update_package"])
            self.assertEqual("kanji_dictionary.db", data["assets"][0]["path"])
            self.assertRegex(data["assets"][0]["sha256"], r"^[0-9a-f]{64}$")
            self.assertEqual("KANJIDIC2", data["sources"][0]["name"])
            self.assertEqual("Jiten kanji frequency ranks", data["sources"][1]["name"])
            self.assertNotIn("jmdict_e", json.dumps(data, ensure_ascii=False))
            self.assertIn("SKIP", data["notes"][0])

    def test_generated_database_is_reproducible(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            kanjidic = root / "kanjidic2.xml.gz"
            jiten = root / "jiten_kanji_rank.csv"
            first = root / "first.db"
            second = root / "second.db"
            kanjidic.write_bytes(gzip.compress(KANJIDIC_FIXTURE.encode("utf-8")))
            jiten.write_text("Kanji,Rank\n膨,77\n", encoding="utf-8")
            metadata, rows = generator.parse_kanjidic2(kanjidic)
            ranks = generator.parse_jiten_ranks(jiten)

            generator.write_database(rows, ranks, first, "2026-05-09", kanjidic, jiten, metadata)
            generator.write_database(rows, ranks, second, "2026-05-09", kanjidic, jiten, metadata)

            self.assertEqual(first.read_bytes(), second.read_bytes())

    def test_bundled_database_contains_all_kanjidic2_literals(self) -> None:
        db_path = Path("app/src/main/assets/dictionaries/kanji_dictionary.db")
        checksum_path = db_path.with_suffix(".db.sha256")
        manifest_path = db_path.with_name("dictionary_sources.json")
        jiten_path = Path("tools/data/jiten_kanji_rank.csv")
        if not db_path.exists():
            self.fail("Bundled SQLite dictionary is missing. Run tools/generate_dictionary_assets.py before release validation.")

        with closing(sqlite3.connect(db_path)) as db:
            integrity = db.execute("PRAGMA integrity_check").fetchone()[0]
            count = db.execute("SELECT COUNT(*) FROM kanji").fetchone()[0]
            jiten_count = db.execute("SELECT COUNT(*) FROM jiten_ranks").fetchone()[0]
            meta = dict(db.execute("SELECT key, value FROM dictionary_meta"))
            ranked = db.execute("SELECT COUNT(*) FROM kanji WHERE jiten_rank IS NOT NULL").fetchone()[0]
            bundled_ranks = dict(db.execute("SELECT literal, rank FROM jiten_ranks"))
            bundled_literals = db.execute("SELECT literal FROM kanji").fetchall()

        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest_assets = {asset["path"]: asset for asset in manifest["assets"]}
        source_ranks = generator.parse_jiten_ranks(jiten_path)
        db_hash = generator.sha256(db_path)
        checksum_hash = generator.sha256(checksum_path)

        self.assertEqual("ok", integrity)
        self.assertEqual(13108, count)
        self.assertEqual(10666, jiten_count)
        self.assertEqual("1", meta["schema_version"])
        self.assertEqual("2026-129", meta["kanjidic2_database_version"])
        self.assertEqual("10666", meta["jiten_rank_count"])
        self.assertEqual("8031", meta["jiten_rank_join_count"])
        self.assertEqual(8031, ranked)
        self.assertEqual([], [literal for (literal,) in bundled_literals if not generator.is_kanji_literal(literal)])
        self.assertEqual(source_ranks, bundled_ranks)
        self.assertEqual(f"{db_hash}  {db_path.name}\n", checksum_path.read_text(encoding="utf-8"))
        self.assertEqual(db_hash, manifest_assets[db_path.name]["sha256"])
        self.assertEqual(checksum_hash, manifest_assets[checksum_path.name]["sha256"])
        self.assertEqual(generator.sha256(jiten_path), manifest["sources"][1]["source_sha256"])

    def test_bundled_stroke_guides_are_complete_and_well_formed(self) -> None:
        stroke_path = Path("app/src/main/res/raw/kanji_strokes.tsv")
        lines = stroke_path.read_text(encoding="utf-8").splitlines()
        self.assertEqual(
            [
                "# Compact normalized stroke guides generated from KanjiVG.",
                "# Source: kanjivg-20250816.xml.gz",
                "# Format: kanji<TAB>x,y;x,y|x,y;x,y",
            ],
            lines[:3],
        )

        literals: list[str] = []
        for line_number, line in enumerate(lines[3:], start=4):
            cells = line.split("\t")
            if len(cells) != 2 or len(cells[0]) != 1:
                self.fail(f"Malformed stroke guide key on line {line_number}")
            literal, encoded_strokes = cells
            literals.append(literal)
            for encoded_stroke in encoded_strokes.split("|"):
                points = encoded_stroke.split(";")
                if len(points) < 2:
                    self.fail(f"Stroke with fewer than two points on line {line_number}")
                for point in points:
                    coordinates = point.split(",")
                    if len(coordinates) != 2:
                        self.fail(f"Malformed stroke point on line {line_number}")
                    x, y = (float(value) for value in coordinates)
                    if not (0.0 <= x <= 1.0 and 0.0 <= y <= 1.0):
                        self.fail(f"Out-of-range stroke point on line {line_number}")

        self.assertEqual(6526, len(literals))
        self.assertEqual(len(literals), len(set(literals)))
        self.assertEqual(sorted(literals, key=ord), literals)


if __name__ == "__main__":
    unittest.main()
