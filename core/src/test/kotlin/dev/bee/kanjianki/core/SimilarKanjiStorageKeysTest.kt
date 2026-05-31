package dev.bee.kanjianki.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SimilarKanjiStorageKeysTest {
    @Test
    fun canonicalPairOrdersKanjiLexically() {
        assertArrayEquals(arrayOf("拉", "麺"), SimilarKanjiStorageKeys.canonicalPair("麺", "拉"))
        assertArrayEquals(arrayOf("拉", "麺"), SimilarKanjiStorageKeys.canonicalPair("拉", "麺"))
    }

    @Test
    fun pairKeyKeepsLegacyNulDelimiter() {
        assertEquals("拉\u0000麺\u0000fixture", SimilarKanjiStorageKeys.pairKey("拉", "麺", "fixture"))
    }

    @Test
    fun choiceKeyKeepsLegacyControlDelimiter() {
        assertEquals("拉\u0001拉|麺", SimilarKanjiStorageKeys.choiceKey("拉", "拉|麺"))
        assertEquals("拉\u0001", SimilarKanjiStorageKeys.choiceKey("拉", null))
    }

    @Test
    fun splitChoiceKeyAcceptsMissingOrEmptySignature() {
        assertArrayEquals(arrayOf("拉", "拉|麺"), SimilarKanjiStorageKeys.splitChoiceKey("拉\u0001拉|麺"))
        assertArrayEquals(arrayOf("拉", ""), SimilarKanjiStorageKeys.splitChoiceKey("拉\u0001"))
        assertArrayEquals(emptyArray<String>(), SimilarKanjiStorageKeys.splitChoiceKey(null))
    }
}
