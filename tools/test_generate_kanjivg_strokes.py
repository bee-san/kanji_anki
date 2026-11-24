#!/usr/bin/env python3

from __future__ import annotations

import gzip
import tempfile
import unittest
from pathlib import Path

from tools import generate_kanjivg_strokes as generator


EXPECTED_KANJI_RANGES = (
    (0x3400, 0x4DBF),
    (0x4E00, 0x9FFF),
    (0xF900, 0xFAFF),
    (0x20000, 0x2A6DF),
    (0x2A700, 0x2B73F),
    (0x2B740, 0x2B81F),
    (0x2B820, 0x2CEAF),
    (0x2CEB0, 0x2EBEF),
    (0x2EBF0, 0x2EE5F),
    (0x2F800, 0x2FA1F),
    (0x30000, 0x3134F),
    (0x31350, 0x323AF),
    (0x323B0, 0x3347F),
)


class GenerateKanjiVgStrokesTest(unittest.TestCase):
    def test_should_include_uses_exact_unicode_cjk_ideograph_block_bounds(self) -> None:
        self.assertEqual(EXPECTED_KANJI_RANGES, generator.KANJI_RANGES)
        for start, end in EXPECTED_KANJI_RANGES:
            self.assertTrue(generator.should_include(chr(start)))
            self.assertTrue(generator.should_include(chr(end)))
        for codepoint in (0x2A6E0, 0x2EE60, 0x2FA20, 0x33480):
            self.assertFalse(generator.should_include(chr(codepoint)))

    def test_should_include_retains_kanjivg_non_kanji_symbols(self) -> None:
        self.assertTrue(generator.should_include("\u3005"))
        self.assertTrue(generator.should_include("\u3007"))
        self.assertTrue(generator.should_include("\u30A0"))
        self.assertTrue(generator.should_include("\u30FF"))
        self.assertFalse(generator.should_include("\u3004"))
        self.assertFalse(generator.should_include("\u3008"))



    def test_parts_split_at_the_first_branching_group_and_mark_side_radicals(self) -> None:
        xml = """<kanjivg xmlns:kvg='http://kanjivg.tagaini.net'>
<kanji id="kvg:kanji_0501f"><g id="kvg:0501f" kvg:element="借">
  <g kvg:element="亻" kvg:radical="general"><path d="M1,1L2,2"/><path d="M3,3L4,4"/></g>
  <g kvg:element="昔" kvg:phon="昔"><g><path d="M5,5L6,6"/></g><g kvg:element="日"><path d="M7,7L8,8"/></g></g>
</g></kanji>
<kanji id="kvg:kanji_056fd"><g id="kvg:056fd" kvg:element="国">
  <g kvg:element="囗" kvg:part="1" kvg:radical="general"><path d="M1,1L2,2"/></g>
  <g kvg:element="玉" kvg:radical="nelson"><path d="M3,3L4,4"/></g>
  <g kvg:element="囗" kvg:part="2" kvg:radical="general"><path d="M5,5L6,6"/></g>
</g></kanji>
<kanji id="kvg:kanji_0672b"><g id="kvg:0672b" kvg:element="末">
  <path d="M1,1L2,2"/>
  <g kvg:element="木" kvg:radical="tradit"><path d="M3,3L4,4"/><path d="M5,5L6,6"/></g>
</g></kanji>
</kanjivg>"""
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "kanjivg.xml.gz"
            with gzip.open(source, "wt", encoding="utf-8") as out:
                out.write(xml)
            records = {char: parts for char, _strokes, parts in generator.read_kanjivg_with_parts(source)}
            # 借: 亻 (a radical) then 昔, however deep 昔's strokes sit.
            self.assertEqual(([0, 0, 1, 1], [0]), records["借"])
            # 囗 wraps 玉 in two pieces but is one part; a "nelson" mark is not the radical.
            self.assertEqual(([0, 1, 0], [0]), records["国"])
            # A stroke straight under the kanji is a part of its own.
            self.assertEqual(([0, 1, 1], [1]), records["末"])

            output = Path(directory) / "parts.tsv"
            generator.write_parts_tsv(generator.read_kanjivg_with_parts(source), output, source.name)
            lines = [line for line in output.read_text(encoding="utf-8").splitlines() if not line.startswith("#")]
            self.assertEqual(["借\t0,0,1,1\t0", "国\t0,1,0\t0", "末\t0,1,1\t1"], lines)


if __name__ == "__main__":
    unittest.main()
