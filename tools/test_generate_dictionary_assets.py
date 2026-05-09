#!/usr/bin/env python3

from __future__ import annotations

import gzip
import json
import tempfile
import unittest
from pathlib import Path

from tools import generate_dictionary_assets as generator


JMDICT_FIXTURE = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE JMdict [
<!ENTITY n "noun (common) (futsuumeishi)">
<!ENTITY vs "noun or participle which takes the aux. verb suru">
<!ENTITY vi "intransitive verb">
]>
<JMdict>
<entry>
<ent_seq>1520010</ent_seq>
<k_ele><keb>膨張</keb><ke_pri>ichi1</ke_pri></k_ele>
<r_ele><reb>ぼうちょう</reb><re_pri>news1</re_pri></r_ele>
<sense>
<pos>&n;</pos><pos>&vs;</pos><pos>&vi;</pos>
<gloss>expansion</gloss><gloss>swelling</gloss>
</sense>
</entry>
</JMdict>
"""


KANJIDIC_FIXTURE = """<?xml version="1.0" encoding="UTF-8"?>
<kanjidic2>
<header><file_version>4</file_version><database_version>2026-129</database_version><date_of_creation>2026-05-09</date_of_creation></header>
<character>
<literal>膨</literal>
<radical><rad_value rad_type="classical">130</rad_value></radical>
<misc><grade>8</grade><stroke_count>16</stroke_count><freq>2077</freq></misc>
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
</kanjidic2>
"""


class GenerateDictionaryAssetsTest(unittest.TestCase):
    def test_parses_jmdict_word_sense_metadata_and_glosses(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp) / "JMdict_e.gz"
            source.write_bytes(gzip.compress(JMDICT_FIXTURE.encode("utf-8")))

            rows = generator.parse_jmdict(source)

            self.assertEqual(1, len(rows))
            self.assertEqual("膨張", rows[0].expression)
            self.assertEqual("ぼうちょう", rows[0].reading)
            self.assertEqual(("expansion", "swelling"), rows[0].glosses)
            self.assertIn("noun", rows[0].pos[0])

    def test_parses_kanjidic2_kanji_fields(self) -> None:
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

    def test_manifest_records_license_version_hashes_and_excluded_skip_note(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            jmdict = root / "JMdict_e.gz"
            kanjidic = root / "kanjidic2.xml.gz"
            words = root / "jmdict_e_words.tsv.gz"
            kanji = root / "kanjidic2_kanji.tsv.gz"
            manifest = root / "dictionary_sources.json"
            jmdict.write_bytes(gzip.compress(JMDICT_FIXTURE.encode("utf-8")))
            kanjidic.write_bytes(gzip.compress(KANJIDIC_FIXTURE.encode("utf-8")))
            words.write_bytes(gzip.compress(b"expression\treading\tglosses\tpos\tpriority\tcommonness\n"))
            kanji.write_bytes(gzip.compress(b"literal\tmeanings\ton_readings\tkun_readings\tnanori_readings\tstroke_count\tgrade\tradical\tfrequency\n"))

            generator.write_manifest(
                manifest,
                "2026-05-09",
                jmdict,
                kanjidic,
                words,
                kanji,
                1,
                1,
                {"file_version": "4", "database_version": "2026-129", "date_of_creation": "2026-05-09"},
            )

            data = json.loads(manifest.read_text(encoding="utf-8"))
            self.assertEqual("CC BY-SA 4.0 via EDRDG licence", data["sources"][0]["license"])
            self.assertEqual("2026-129", data["sources"][1]["database_version"])
            self.assertRegex(data["sources"][0]["source_sha256"], r"^[0-9a-f]{64}$")
            self.assertIn("SKIP", data["notes"][0])

    def test_generated_gzip_assets_are_reproducible(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            first = root / "first.tsv.gz"
            second = root / "second.tsv.gz"
            records = [
                generator.WordRecord(
                    "悲しみ",
                    "かなしみ",
                    ("sorrow", "despair"),
                    ("noun",),
                    ("ichi1",),
                    1,
                )
            ]

            generator.write_words(records, first)
            generator.write_words(records, second)

            self.assertEqual(first.read_bytes(), second.read_bytes())


if __name__ == "__main__":
    unittest.main()
