package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingAnswerMatcherTest {
    @Test
    fun accentsAreOptionalWithoutWeakeningExactMatch() {
        val lookup = lookup("Café")

        assertTrue(matches(lookup, "cafe"))
        assertTrue(matches(lookup, "CAFE\u0301"))
        assertFalse(matches(lookup, "cafe noir"))
    }

    @Test
    fun compatibilityNormalizationFoldsFullWidthLatinLettersAndDigits() {
        val lookup = lookup("Model 3")

        assertTrue(matches(lookup, "ＭＯＤＥＬ　３"))
    }

    @Test
    fun localizedMeaningsPreserveUnicodeLettersAndScriptSignificantMarks() {
        val lookup = lookup("日本語", "Привет мир", "ガク")

        assertTrue(matches(lookup, "日本語"))
        assertTrue(matches(lookup, "ПРИВЕТ МИР"))
        assertTrue(matches(lookup, "ｶﾞｸ"))
        assertFalse(matches(lookup, "カク"))
    }

    @Test
    fun punctuationAndWhitespaceNormalizeToSingleSpaces() {
        val lookup = lookup("state-of-the-art")

        assertTrue(matches(lookup, "  STATE / of;\tthe, art  "))
        assertFalse(matches(lookup, "stateoftheart"))
    }

    @Test
    fun inputContainingOnlySeparatorsAndMarksIsEmpty() {
        val lookup = lookup("valid", "— … !!!")

        assertFalse(matches(lookup, " — … !!! \t\u0301 "))
        assertEquals(listOf("valid"), TypingAnswerMatcher.acceptedMeanings(lookup, KANJI, ""))
    }

    @Test
    fun acceptedMeaningsDeduplicateByUnicodeNormalizedValue() {
        val lookup = lookup("Café", "cafe", "ＣＡＦＥ", "日本語", "日本語！")

        assertEquals(
            listOf("Café", "日本語"),
            TypingAnswerMatcher.acceptedMeanings(lookup, KANJI, ""),
        )
    }

    private fun matches(lookup: DictionaryLookup, answer: String): Boolean =
        TypingAnswerMatcher.matches(lookup, KANJI, answer, "")

    private fun lookup(vararg meanings: String): DictionaryLookup = DictionaryLookup.fromKanjiEntries(
        listOf(
            DictionaryLookup.KanjiEntry(
                DictionaryLookup.KanjiEntryFields(
                    literal = KANJI,
                    meanings = meanings.toList(),
                    onReadings = emptyList(),
                    kunReadings = emptyList(),
                    nanoriReadings = emptyList(),
                    strokeCount = 0,
                    grade = 0,
                    radical = 0,
                    kanjidicFrequency = 0,
                    jitenRank = null,
                ),
            ),
        ),
    )

    private companion object {
        const val KANJI = "試"
    }
}
