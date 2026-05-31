package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KanjiInventorySearchQueryTest {
    @Test
    fun requiresEachTermAcrossSearchText() {
        val query = KanjiInventorySearchQuery.parse("語 vocabulary")

        assertEquals(2, query.terms().size)
        assertTrue(query.matches("語 ご vocabulary words"))
        assertTrue(query.matches("vocabulary from 語彙 examples"))
        assertFalse(query.matches("語 ご words"))
        assertFalse(query.matches("vocabulary only"))
    }

    @Test
    fun normalizesWidthAndCaseForTerms() {
        val query = KanjiInventorySearchQuery.parse(" ｶﾀｶﾅ  ＬＡＮＧＵＡＧＥ ")

        assertTrue(query.matches("カタカナ language study"))
        assertFalse(query.matches("カタカナ reading"))
    }

    @Test
    fun termsListIsUnmodifiableFromJava() {
        val query = KanjiInventorySearchQuery.parse("語 vocabulary")
        val terms = query.terms() as MutableList<String>

        try {
            terms.add("extra")
            throw AssertionError("terms should be unmodifiable")
        } catch (expected: UnsupportedOperationException) {
            assertEquals(2, query.terms().size)
        }
    }

    @Test
    fun blankQueryMatchesEverything() {
        val query = KanjiInventorySearchQuery.parse("  ")

        assertTrue(query.isEmpty())
        assertTrue(query.matches("any inventory row"))
    }
}
