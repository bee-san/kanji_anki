package dev.bee.kanjianki.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryTextUtilTest {
    @Test
    fun identifiesExactUnicodeCjkIdeographBlockBounds() {
        val ranges = listOf(
            0x3400..0x4DBF,
            0x4E00..0x9FFF,
            0xF900..0xFAFF,
            0x20000..0x2A6DF,
            0x2A700..0x2B73F,
            0x2B740..0x2B81F,
            0x2B820..0x2CEAF,
            0x2CEB0..0x2EBEF,
            0x2EBF0..0x2EE5F,
            0x2F800..0x2FA1F,
            0x30000..0x3134F,
            0x31350..0x323AF,
            0x323B0..0x3347F,
        )

        for (range in ranges) {
            assertTrue("Expected block start U+${range.first.toString(16)}", DictionaryTextUtil.isKanji(range.first))
            assertTrue("Expected block end U+${range.last.toString(16)}", DictionaryTextUtil.isKanji(range.last))
        }

        for (codePoint in listOf(0x33FF, 0x4DC0, 0xA000, 0xFB00, 0x1FFFF, 0x2A6E0, 0x2EE60, 0x2FA20, 0x2FFFF, 0x33480)) {
            assertFalse("Expected gap U+${codePoint.toString(16)}", DictionaryTextUtil.isKanji(codePoint))
        }
    }
}
