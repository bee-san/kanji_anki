#!/usr/bin/env python3

from __future__ import annotations

import gzip
import json
import tarfile
import tempfile
import unittest
from io import BytesIO
from pathlib import Path

from tools import generate_similar_kanji_tsv as generator


def compact_entry(visually_similar: list[str]) -> list[object]:
    return [[], [], "", [], "", [], "", "", visually_similar, []]


class GenerateSimilarKanjiTsvTest(unittest.TestCase):
    def test_loads_kiku_tar_shape_and_deduplicates_pairs(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            public = root / "apps/docs/public"
            public.mkdir(parents=True)
            compact = {
                "拉": compact_entry(["麺", "謎", "kana", "拉"]),
                "麺": compact_entry(["拉"]),
                "語": compact_entry([]),
            }
            payload = gzip.compress(json.dumps(compact).encode("utf-8"))
            with tarfile.open(public / "_kiku_db_main.tar", "w") as archive:
                info = tarfile.TarInfo(generator.KIKU_COMPACT_JSON_GZ)
                info.size = len(payload)
                archive.addfile(info, BytesIO(payload))
            (public / "_kiku_db_main_manifest.json").write_text(
                json.dumps({"files": {generator.KIKU_COMPACT_JSON_GZ: {"start": 512, "end": 512 + len(payload) - 1, "size": len(payload)}}}),
                encoding="utf-8",
            )

            pairs = generator.similar_pairs(generator.load_compact_db(root))

            self.assertEqual(
                [("拉", "謎", generator.SOURCE), ("拉", "麺", generator.SOURCE)],
                pairs,
            )


if __name__ == "__main__":
    unittest.main()
