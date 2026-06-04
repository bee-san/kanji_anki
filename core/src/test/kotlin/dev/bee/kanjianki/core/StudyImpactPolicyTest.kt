package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class StudyImpactPolicyTest {
    @Test
    fun summarizePreservesReviewAndWritingCounts() {
        val impact = StudyImpactPolicy.summarize(6, 4, 3, 2, 1, 1)

        assertEquals(6, impact.totalReviews())
        assertEquals(4, impact.distinctReviewedKanji())
        assertEquals(3, impact.writingRequired())
        assertEquals(2, impact.writingPassed())
        assertEquals(1, impact.writingFailed())
        assertEquals(1, impact.manualOverrides())
    }

    @Test
    fun summarizeDoesNotClampRawAggregateValues() {
        val impact = StudyImpactPolicy.summarize(-1, -2, -3, -4, -5, -6)

        assertEquals(-1, impact.totalReviews())
        assertEquals(-2, impact.distinctReviewedKanji())
        assertEquals(-3, impact.writingRequired())
        assertEquals(-4, impact.writingPassed())
        assertEquals(-5, impact.writingFailed())
        assertEquals(-6, impact.manualOverrides())
    }
}
