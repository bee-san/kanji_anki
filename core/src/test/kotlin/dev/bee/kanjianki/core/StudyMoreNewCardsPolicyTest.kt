package dev.bee.kanjianki.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyMoreNewCardsPolicyTest {
    @Test
    fun defaultRequestCountPreservesOneToFiveClamp() {
        assertEquals(1, StudyMoreNewCardsPolicy.defaultRequestCount(0))
        assertEquals(1, StudyMoreNewCardsPolicy.defaultRequestCount(1))
        assertEquals(3, StudyMoreNewCardsPolicy.defaultRequestCount(3))
        assertEquals(5, StudyMoreNewCardsPolicy.defaultRequestCount(9))
    }

    @Test
    fun requestedCountAcceptsTrimmedPositiveIntegers() {
        val decision = StudyMoreNewCardsPolicy.requestedCount(" 3 ")

        assertTrue(decision.accepted())
        assertEquals(3, decision.requestedCount())
        assertEquals("", decision.message())
    }

    @Test
    fun requestedCountRejectsNonIntegersAndNonPositiveValues() {
        val nonInteger = StudyMoreNewCardsPolicy.requestedCount("not a number")
        val zero = StudyMoreNewCardsPolicy.requestedCount("0")

        assertFalse(nonInteger.accepted())
        assertEquals(-1, nonInteger.requestedCount())
        assertEquals("Use a whole number of new cards.", nonInteger.message())
        assertFalse(zero.accepted())
        assertEquals(-1, zero.requestedCount())
        assertEquals("Use at least 1 new card.", zero.message())
    }

    @Test
    fun partialAvailabilityMessagePreservesPluralCopy() {
        assertEquals("Only 1 new card was available.", StudyMoreNewCardsPolicy.partialAvailabilityMessage(1))
        assertEquals("Only 2 new cards were available.", StudyMoreNewCardsPolicy.partialAvailabilityMessage(2))
    }

    @Test
    fun messagesLocalizeInJapaneseLocale() {
        withDefaultLocale(Locale.JAPANESE) {
            val nonInteger = StudyMoreNewCardsPolicy.requestedCount("abc")
            val zero = StudyMoreNewCardsPolicy.requestedCount("0")

            assertFalse(nonInteger.accepted())
            assertEquals("新規カード数は整数で入力してください。", nonInteger.message())
            assertFalse(zero.accepted())
            assertEquals("新規カード数は1以上で入力してください。", zero.message())
            assertEquals("新しいカードはありません。", StudyMoreNewCardsPolicy.noNewCardsAvailableMessage())
            assertEquals("新規カードは2件のみ使用できます。", StudyMoreNewCardsPolicy.partialAvailabilityMessage(2))
        }
    }

    private inline fun withDefaultLocale(locale: Locale, block: () -> Unit) {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            block()
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
