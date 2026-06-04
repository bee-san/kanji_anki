package dev.bee.kanjianki.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentMistakePolicyTest {
    @Test
    fun boundedLimitKeepsAtLeastOneResult() {
        assertEquals(1, RecentMistakePolicy.boundedLimit(-5))
        assertEquals(1, RecentMistakePolicy.boundedLimit(0))
        assertEquals(12, RecentMistakePolicy.boundedLimit(12))
    }

    @Test
    fun mistakeRatingsAreAgainAndHardOnly() {
        assertArrayEquals(
            arrayOf(StudyRatings.AGAIN, StudyRatings.HARD),
            RecentMistakePolicy.mistakeRatings(),
        )
    }

    @Test
    fun mistakeNormalizesTextButPreservesTimestamp() {
        val mistake = RecentMistakePolicy.mistake(null, null, -10L)
        val constructed = RecentMistakePolicy.RecentMistake(null, null, -10L)

        assertEquals("", mistake.kanji())
        assertEquals("", mistake.rating())
        assertEquals(-10L, mistake.reviewedAtMillis())
        assertEquals("RecentMistake[kanji=, rating=, reviewedAtMillis=-10]", mistake.toString())
        assertEquals(mistake, constructed)
    }
}
