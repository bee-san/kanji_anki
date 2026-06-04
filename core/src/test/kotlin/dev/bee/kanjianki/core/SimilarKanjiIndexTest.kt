package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

class SimilarKanjiIndexTest {
    @Test
    fun emptyIndexAndNullLookupsAreSafe() {
        val index = SimilarKanjiIndex.empty()

        assertEquals(0, index.pairCount())
        assertFalse(index.areSimilar(null, "拉"))
        assertFalse(index.areSimilar("拉", null))
        assertFalse(index.areSimilar("拉", "提"))
        assertTrue(index.similarTo(null).isEmpty())
        assertTrue(index.similarTo("拉").isEmpty())
        assertTrue(index.similarTo("あ").isEmpty())
        assertTrue(index.pairsWithin(null).isEmpty())
        assertTrue(index.pairsWithin(emptyList()).isEmpty())
    }

    @Test
    fun parseTsvTreatsPairsAsSymmetricAndDeduplicates() {
        val index = SimilarKanjiIndex.parseTsv(
            StringReader(
                """
                # generated
                kanji_a	kanji_b	source
                拉	麺	fixture
                麺	拉	fixture
                拉	謎	fixture
                """
            )
        )

        assertEquals(2, index.pairCount())
        assertTrue(index.areSimilar("拉", "麺"))
        assertTrue(index.areSimilar("麺", "拉"))
        assertTrue(index.areSimilar("謎", "拉"))
        assertFalse(index.areSimilar("拉", "拉"))
        assertFalse(index.areSimilar("拉", "提"))
        assertEquals(listOf("謎", "麺"), index.similarTo("拉"))
    }

    @Test
    fun parseTsvSkipsMalformedRows() {
        val index = SimilarKanjiIndex.parseTsv(
            StringReader(
                listOf(
                    "",
                    "# generated",
                    "not enough cells",
                    "abc	麺	fixture",
                    "拉	kana	fixture",
                    "拉	拉	fixture",
                    "拉	麺	",
                    "提	謎",
                    "",
                ).joinToString("\n")
            )
        )

        assertEquals(2, index.pairCount())
        assertTrue(index.areSimilar("拉", "麺"))
        assertEquals(SimilarKanjiIndex.SOURCE_KIKU_VISUALLY_SIMILAR, index.pairsWithin(listOf("拉", "麺")).first().source)
        assertEquals(SimilarKanjiIndex.SOURCE_KIKU_VISUALLY_SIMILAR, index.pairsWithin(listOf("提", "謎")).first().source)
    }

    @Test
    fun pairsWithinOnlyReturnsPairsWhereBothKanjiArePresent() {
        val index = SimilarKanjiIndex.parseTsv(
            StringReader(
                """
                拉	麺	fixture
                拉	謎	fixture
                確	認	fixture
                """
            )
        )

        val pairs = index.pairsWithin(listOf("拉", "謎", "提"))

        assertEquals(1, pairs.size)
        assertEquals("拉", pairs[0].kanjiA)
        assertEquals("謎", pairs[0].kanjiB)
        assertTrue(index.pairsWithin(listOf("拉")).isEmpty())
        assertTrue(index.pairsWithin(listOf("拉", "not-kanji", "")).isEmpty())
    }

    @Test
    fun pairEqualityUsesCanonicalKanjiAndSource() {
        val pair = SimilarKanjiIndex.Pair.canonical("麺", "拉", "fixture")
        val same = SimilarKanjiIndex.Pair.canonical("拉", "麺", "fixture")
        val differentSource = SimilarKanjiIndex.Pair.canonical("拉", "麺", "other")
        val differentKanji = SimilarKanjiIndex.Pair.canonical("拉", "謎", "fixture")
        val differentFirstKanji = SimilarKanjiIndex.Pair.canonical("亜", "麺", "fixture")

        assertEquals(pair, pair)
        assertEquals(pair, same)
        assertEquals(pair.hashCode(), same.hashCode())
        assertEquals(0, pair.compareTo(same))
        assertFalse(pair.equals(differentSource))
        assertFalse(pair.equals(differentKanji))
        assertFalse(pair.equals(differentFirstKanji))
        assertFalse(pair.equals("not a pair"))
    }

    @Test
    fun pairCanonicalDefaultsBlankSourceAndSortsBySource() {
        val nullSource = SimilarKanjiIndex.Pair.canonical("拉", "麺", null)
        val defaultSource = SimilarKanjiIndex.Pair.canonical("拉", "麺", " ")
        val explicitSource = SimilarKanjiIndex.Pair.canonical("拉", "麺", "zz")

        assertEquals(defaultSource, nullSource)
        assertEquals(SimilarKanjiIndex.SOURCE_KIKU_VISUALLY_SIMILAR, defaultSource.source)
        assertTrue(defaultSource.compareTo(explicitSource) < 0)
        assertTrue(SimilarKanjiIndex.Pair.canonical("拉", "麺", "a").compareTo(defaultSource) < 0)
    }
}
