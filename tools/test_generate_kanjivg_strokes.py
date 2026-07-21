#!/usr/bin/env python3

from __future__ import annotations

import unittest

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


if __name__ == "__main__":
    unittest.main()
