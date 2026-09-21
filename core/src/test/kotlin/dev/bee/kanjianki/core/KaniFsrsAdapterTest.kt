package dev.bee.kanjianki.core

import dev.bee.fsrs.Fsrs7
import dev.bee.fsrs.Fsrs7Parameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaniFsrsAdapterTest {
    @Test
    fun latestAdapterUsesThirtyFiveParameterFsrs7EngineAndClampsMigratedState() {
        val adapter = LatestFsrsAdapter()

        // 4.1283 is FSRS-7's w[2], the initial stability for a Good first review.
        // FSRS-6's was 2.3065, so this value alone distinguishes the two engines.
        assertEquals(4.1283, Fsrs7Parameters.latestDefaultValues()[2], 0.0)

        val initial = adapter.initialReview(StudyRatings.GOOD, 0.4, 6.0, 0.9, true)
        assertEquals(4.1283, initial.stability, 0.000001)
        assertEquals(4.194588083372719, initial.difficulty, 0.000001)
        assertEquals(3, initial.intervalDays())

        val relearningGraduation = adapter.initialReview(StudyRatings.GOOD, 0.96, 6.0, 0.9, false)
        assertEquals(0.96, relearningGraduation.stability, 0.000001)
        assertEquals(5.968179285716673, relearningGraduation.difficulty, 0.000001)
        assertEquals(1, relearningGraduation.intervalDays())

        val review = adapter.review(
            0.0,
            50.0,
            StudyRatings.AGAIN,
            2.0,
            5.0,
        )
        assertTrue(review.stability >= Fsrs7.STABILITY_MIN)
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
        assertTrue(relearning.stability >= Fsrs7.STABILITY_MIN)
        assertEquals(9.928179285716674, relearning.difficulty, 0.000001)
        assertTrue(relearning.intervalDays() >= 1)

        val review = adapter.review(
            Double.NEGATIVE_INFINITY,
            Double.NaN,
            StudyRatings.HARD,
            0.0,
            Double.POSITIVE_INFINITY,
        )
        assertTrue(review.stability >= Fsrs7.STABILITY_MIN)
        assertEquals(6.772279285716673, review.difficulty, 0.000001)
        assertEquals(1, review.intervalDays())

        val clampedReview = adapter.review(
            5.0,
            Double.NEGATIVE_INFINITY,
            StudyRatings.GOOD,
            7.0,
            Double.NEGATIVE_INFINITY,
        )
        assertEquals(1.018179285716673, clampedReview.difficulty, 0.000001)
        assertEquals(
            1,
            adapter.review(
                5.0,
                Double.POSITIVE_INFINITY,
                StudyRatings.EASY,
                7.0,
                Double.POSITIVE_INFINITY,
            ).intervalDays(),
        )
    }

    @Test
    fun nonFiniteElapsedTimeIsNormalizedRatherThanThrown() {
        val adapter = LatestFsrsAdapter()

        // A Double elapsed time makes NaN reachable where the old Int could not
        // express it. The engine's require() would throw, which at this boundary
        // means a lost review rather than a scheduled one.
        val notANumber = adapter.review(5.0, 6.0, StudyRatings.GOOD, Double.NaN, 0.9)
        val zero = adapter.review(5.0, 6.0, StudyRatings.GOOD, 0.0, 0.9)

        assertEquals(zero.stability, notANumber.stability, 0.0)
        assertEquals(zero.intervalMillis, notANumber.intervalMillis)
    }

    @Test
    fun latestAdapterTreatsNullAndUnknownRatingsAsAgain() {
        val adapter = LatestFsrsAdapter()

        val nullInitial = adapter.initialReview(null, 0.4, 6.0, 0.9, true)
        val unknownInitial = adapter.initialReview("???", 0.4, 6.0, 0.9, true)
        // FSRS-7's w[0], the initial stability for an Again first review.
        assertEquals(0.041, nullInitial.stability, 0.000001)
        assertEquals(unknownInitial.stability, nullInitial.stability, 0.000001)
        assertEquals(unknownInitial.difficulty, nullInitial.difficulty, 0.000001)
        assertEquals(unknownInitial.intervalDays(), nullInitial.intervalDays())

        val nullReview = adapter.review(5.0, 6.0, null, 7.0, 0.9)
        val unknownReview = adapter.review(5.0, 6.0, "???", 7.0, 0.9)
        assertEquals(unknownReview.stability, nullReview.stability, 0.000001)
        assertEquals(unknownReview.difficulty, nullReview.difficulty, 0.000001)
        assertEquals(unknownReview.intervalDays(), nullReview.intervalDays())
    }

    @Test
    fun targetRetentionControlsReviewIntervalsWithHigherRetentionShorter() {
        val adapter = LatestFsrsAdapter()

        val lowerRetention = adapter.review(14.0, 5.0, StudyRatings.GOOD, 14.0, 0.80)
        val defaultRetention = adapter.review(14.0, 5.0, StudyRatings.GOOD, 14.0, 0.90)
        val higherRetention = adapter.review(14.0, 5.0, StudyRatings.GOOD, 14.0, 0.96)

        assertTrue(
            "Higher desired retention should shorten the next review interval.",
            higherRetention.intervalMillis < defaultRetention.intervalMillis,
        )
        assertTrue(
            "Lower desired retention should lengthen the next review interval.",
            defaultRetention.intervalMillis < lowerRetention.intervalMillis,
        )
    }

    @Test
    fun reviewRatingIntervalsKeepAnkiAnswerButtonOrdering() {
        val adapter = LatestFsrsAdapter()

        val again = adapter.review(12.0, 5.0, StudyRatings.AGAIN, 21.0, 0.90)
        val hard = adapter.review(12.0, 5.0, StudyRatings.HARD, 21.0, 0.90)
        val good = adapter.review(12.0, 5.0, StudyRatings.GOOD, 21.0, 0.90)
        val easy = adapter.review(12.0, 5.0, StudyRatings.EASY, 21.0, 0.90)

        assertTrue("Again should schedule sooner than Hard.", again.intervalMillis < hard.intervalMillis)
        assertTrue("Hard should schedule sooner than Good.", hard.intervalMillis < good.intervalMillis)
        assertTrue("Good should schedule sooner than Easy.", good.intervalMillis < easy.intervalMillis)
    }

    @Test
    fun latestAdapterNormalizesNegativeElapsedDaysAtTheAppBoundary() {
        val adapter = LatestFsrsAdapter()

        val negativeElapsed = adapter.review(5.0, 6.0, StudyRatings.GOOD, -7.0, 0.9)
        val zeroElapsed = adapter.review(5.0, 6.0, StudyRatings.GOOD, 0.0, 0.9)

        assertEquals(zeroElapsed.stability, negativeElapsed.stability, 0.000001)
        assertEquals(zeroElapsed.difficulty, negativeElapsed.difficulty, 0.000001)
        assertEquals(zeroElapsed.intervalDays(), negativeElapsed.intervalDays())
    }

    /**
     * The reason the adapter carries fractional days end to end.
     *
     * Under FSRS-6 every one of these reviews floored to elapsed 0 and produced a
     * byte-identical result, so the scheduler could not tell a ten-minute gap from a
     * twenty-hour one. Sub-day *inputs* are what FSRS-7 buys Kani, and any future
     * rounding of the elapsed time would silently undo it.
     *
     * Asserted on memory state rather than on the interval, because the interval is
     * floored at one day on the way out (see `atLeastOneDay`) and would hide a
     * difference the engine did register.
     */
    @Test
    fun subDayElapsedTimesProduceDistinctMemoryStates() {
        val adapter = LatestFsrsAdapter()

        val tenMinutes = adapter.review(5.0, 6.0, StudyRatings.GOOD, 10.0 / 1440.0, 0.9)
        val sixHours = adapter.review(5.0, 6.0, StudyRatings.GOOD, 0.25, 0.9)
        val twentyHours = adapter.review(5.0, 6.0, StudyRatings.GOOD, 20.0 / 24.0, 0.9)

        assertNotEquals(tenMinutes.stability, sixHours.stability)
        assertNotEquals(sixHours.stability, twentyHours.stability)
        assertTrue(
            "A longer gap survived is stronger evidence of retention.",
            tenMinutes.stability < twentyHours.stability,
        )
    }

    /**
     * Sub-day intervals still reach the persisted due time above the one-day floor.
     *
     * `intervalDays()` ceils, so it cannot show this; `intervalMillis` is what the
     * scheduler adds to `now` to get `dueAtMillis`. A 2.97-day interval must persist
     * as 2.97 days rather than snapping to 3, or the fractional scheduling would be
     * lost at the boundary while every interval still changed.
     */
    @Test
    fun intervalMillisIsNotQuantizedToWholeDays() {
        val adapter = LatestFsrsAdapter()

        val initial = adapter.initialReview(StudyRatings.GOOD, 0.4, 6.0, 0.9, true)

        assertTrue(initial.intervalMillis > KaniFsrsReviewResult.DAY_MILLIS)
        assertNotEquals(0L, initial.intervalMillis % KaniFsrsReviewResult.DAY_MILLIS)
        assertEquals(3, initial.intervalDays())
    }

    /**
     * A scheduled interval never drops below one day, however weak the memory.
     *
     * FSRS-6 clamped this inside the engine; FSRS-7 does not, so Kani floors it at
     * the adapter. Without the floor a lapsed card schedules seconds out, comes due
     * in the same session, and — because ladder movement keys off the persisted FSRS
     * due time rather than the calendar day — can accumulate real-due fails and
     * demote a rung in seconds.
     */
    @Test
    fun scheduledIntervalsNeverFallBelowOneDayEvenForNearZeroStability() {
        val adapter = LatestFsrsAdapter()

        val lapsed = adapter.review(0.01, 9.5, StudyRatings.AGAIN, 30.0, 0.9)
        assertTrue(
            "post-lapse stability should be well under a day for this to be a real test",
            lapsed.stability < 1.0,
        )
        assertEquals(KaniFsrsReviewResult.DAY_MILLIS, lapsed.intervalMillis)
        assertEquals(KaniFsrsReviewResult.DAY_MILLIS, lapsed.promotionIntervalMillis)
        assertEquals(1, lapsed.intervalDays())
    }

    @Test
    fun resultCeilsIntervalsToAtLeastOneDay() {
        val result = KaniFsrsReviewResult(1.0, 2.0, 1L)

        assertEquals(1, result.intervalDays())
        assertEquals(21, KaniFsrsReviewResult(1.0, 2.0, 21L * KaniFsrsReviewResult.DAY_MILLIS).intervalDays())
        assertEquals(22, KaniFsrsReviewResult(1.0, 2.0, 21L * KaniFsrsReviewResult.DAY_MILLIS + 1L).intervalDays())
    }
}
