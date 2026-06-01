package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaniFsrsAdapterTest {
    @Test
    fun latestAdapterUsesTwentyOneParameterEngineAndClampsMigratedState() {
        val adapter = LatestFsrsAdapter()

        val initial = adapter.initialReview(StudyRatings.GOOD, 0.4, 6.0, 0.9, true)
        assertEquals(2.3065, initial.stability, 0.000001)
        assertEquals(2.118103970459, initial.difficulty, 0.000001)
        assertEquals(2, initial.intervalDays())

        val relearningGraduation = adapter.initialReview(StudyRatings.GOOD, 0.96, 6.0, 0.9, false)
        assertEquals(0.96, relearningGraduation.stability, 0.000001)
        assertEquals(5.989228369297, relearningGraduation.difficulty, 0.000001)
        assertEquals(1, relearningGraduation.intervalDays())

        val review = adapter.review(
            0.0,
            50.0,
            StudyRatings.AGAIN,
            2,
            5.0,
        )
        assertTrue(review.stability >= 0.001)
        assertTrue(review.difficulty <= 10.0)
        assertEquals(1, review.intervalDays())
    }

    @Test
    fun latestAdapterNormalizesNonFiniteImportedStateAtTheAppBoundary() {
        val adapter = LatestFsrsAdapter()

        val relearning = adapter.initialReview(
            StudyRatings.GOOD,
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NaN,
            false,
        )
        assertTrue(relearning.stability >= 0.001)
        assertEquals(9.985228369297, relearning.difficulty, 0.000001)
        assertTrue(relearning.intervalDays() >= 1)

        val review = adapter.review(
            Double.NEGATIVE_INFINITY,
            Double.NaN,
            StudyRatings.HARD,
            0,
            Double.POSITIVE_INFINITY,
        )
        assertTrue(review.stability >= 0.001)
        assertEquals(6.665995369297, review.difficulty, 0.000001)
        assertEquals(1, review.intervalDays())

        val clampedReview = adapter.review(
            5.0,
            Double.NEGATIVE_INFINITY,
            StudyRatings.GOOD,
            7,
            Double.NEGATIVE_INFINITY,
        )
        assertEquals(1.0, clampedReview.difficulty, 0.000001)
        assertEquals(
            1,
            adapter.review(
                5.0,
                Double.POSITIVE_INFINITY,
                StudyRatings.EASY,
                7,
                Double.POSITIVE_INFINITY,
            ).intervalDays(),
        )
    }

    @Test
    fun latestAdapterTreatsNullAndUnknownRatingsAsAgain() {
        val adapter = LatestFsrsAdapter()

        val nullInitial = adapter.initialReview(null, 0.4, 6.0, 0.9, true)
        val unknownInitial = adapter.initialReview("???", 0.4, 6.0, 0.9, true)
        assertEquals(0.212, nullInitial.stability, 0.000001)
        assertEquals(unknownInitial.stability, nullInitial.stability, 0.000001)
        assertEquals(unknownInitial.difficulty, nullInitial.difficulty, 0.000001)
        assertEquals(unknownInitial.intervalDays(), nullInitial.intervalDays())

        val nullReview = adapter.review(5.0, 6.0, null, 7, 0.9)
        val unknownReview = adapter.review(5.0, 6.0, "???", 7, 0.9)
        assertEquals(unknownReview.stability, nullReview.stability, 0.000001)
        assertEquals(unknownReview.difficulty, nullReview.difficulty, 0.000001)
        assertEquals(unknownReview.intervalDays(), nullReview.intervalDays())
    }

    @Test
    fun latestAdapterNormalizesNegativeElapsedDaysAtTheAppBoundary() {
        val adapter = LatestFsrsAdapter()

        val negativeElapsed = adapter.review(5.0, 6.0, StudyRatings.GOOD, -7, 0.9)
        val zeroElapsed = adapter.review(5.0, 6.0, StudyRatings.GOOD, 0, 0.9)

        assertEquals(zeroElapsed.stability, negativeElapsed.stability, 0.000001)
        assertEquals(zeroElapsed.difficulty, negativeElapsed.difficulty, 0.000001)
        assertEquals(zeroElapsed.intervalDays(), negativeElapsed.intervalDays())
    }

    @Test
    fun resultCeilsIntervalsToAtLeastOneDay() {
        val result = KaniFsrsReviewResult(1.0, 2.0, 1L)

        assertEquals(1, result.intervalDays())
        assertEquals(21, KaniFsrsReviewResult(1.0, 2.0, 21L * KaniFsrsReviewResult.DAY_MILLIS).intervalDays())
        assertEquals(22, KaniFsrsReviewResult(1.0, 2.0, 21L * KaniFsrsReviewResult.DAY_MILLIS + 1L).intervalDays())
    }
}
