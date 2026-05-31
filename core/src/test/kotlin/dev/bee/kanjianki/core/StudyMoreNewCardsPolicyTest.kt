package dev.bee.kanjianki.core

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
}
