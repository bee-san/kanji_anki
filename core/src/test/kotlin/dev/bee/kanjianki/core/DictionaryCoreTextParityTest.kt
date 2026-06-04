package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryCoreTextParityTest {
    @Test
    fun dictionaryMeaningCleanerKeepsTextUtilFirstMeaningLineCompatible() {
        assertEquals(
            "Hidden visible & quoted",
            StudyCueFormatter.cleanMeaningText("<ruby>Hidden<rt>reading</rt></ruby><script>bad</script> visible &amp; quoted"),
        )
        assertEquals(
            "pull",
            TextUtil.firstMeaningLine("Meaning: Jitendex (noun) pull; second meaning"),
        )
    }

    @Test
    fun dictionaryRankKanjiRangeMatchesTextUtilKanjiRange() {
        assertTrue(TextUtil.isKanji("日".codePointAt(0)))
        assertEquals(0, JitenKanjiRanks.empty().size())
        assertFalse(TextUtil.isKanji("A".codePointAt(0)))
    }
}
