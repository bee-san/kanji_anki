package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class KaniFsrsAdapterTest {
    @Test
    public void latestAdapterUsesTwentyOneParameterEngineAndClampsMigratedState() {
        LatestFsrsAdapter adapter = new LatestFsrsAdapter();

        KaniFsrsReviewResult initial = adapter.initialReview(StudyRatings.GOOD, 0.4, 6.0, 0.9, true);
        assertEquals(2.3065, initial.stability, 0.000001);
        assertEquals(2.118103970459, initial.difficulty, 0.000001);
        assertEquals(2, initial.intervalDays());

        KaniFsrsReviewResult relearningGraduation =
                adapter.initialReview(StudyRatings.GOOD, 0.96, 6.0, 0.9, false);
        assertEquals(0.96, relearningGraduation.stability, 0.000001);
        assertEquals(5.989228369297, relearningGraduation.difficulty, 0.000001);
        assertEquals(1, relearningGraduation.intervalDays());

        KaniFsrsReviewResult review = adapter.review(
                0.0,
                50.0,
                StudyRatings.AGAIN,
                2,
                5.0
        );
        assertTrue(review.stability >= 0.001);
        assertTrue(review.difficulty <= 10.0);
        assertEquals(1, review.intervalDays());
    }

    @Test
    public void latestAdapterNormalizesNonFiniteImportedStateAtTheAppBoundary() {
        LatestFsrsAdapter adapter = new LatestFsrsAdapter();

        KaniFsrsReviewResult relearning = adapter.initialReview(
                StudyRatings.GOOD,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NaN,
                false
        );
        assertTrue(relearning.stability >= 0.001);
        assertEquals(9.985228369297, relearning.difficulty, 0.000001);
        assertTrue(relearning.intervalDays() >= 1);

        KaniFsrsReviewResult review = adapter.review(
                Double.NEGATIVE_INFINITY,
                Double.NaN,
                StudyRatings.HARD,
                0,
                Double.POSITIVE_INFINITY
        );
        assertTrue(review.stability >= 0.001);
        assertEquals(6.665995369297, review.difficulty, 0.000001);
        assertEquals(1, review.intervalDays());

        KaniFsrsReviewResult clampedReview = adapter.review(
                5.0,
                Double.NEGATIVE_INFINITY,
                StudyRatings.GOOD,
                7,
                Double.NEGATIVE_INFINITY
        );
        assertEquals(1.0, clampedReview.difficulty, 0.000001);
        assertEquals(1, adapter.review(
                5.0,
                Double.POSITIVE_INFINITY,
                StudyRatings.EASY,
                7,
                Double.POSITIVE_INFINITY
        ).intervalDays());
    }

    @Test
    public void latestAdapterTreatsNullAndUnknownRatingsAsAgain() {
        LatestFsrsAdapter adapter = new LatestFsrsAdapter();

        KaniFsrsReviewResult nullInitial = adapter.initialReview(null, 0.4, 6.0, 0.9, true);
        KaniFsrsReviewResult unknownInitial = adapter.initialReview("???", 0.4, 6.0, 0.9, true);
        assertEquals(0.212, nullInitial.stability, 0.000001);
        assertEquals(unknownInitial.stability, nullInitial.stability, 0.000001);
        assertEquals(unknownInitial.difficulty, nullInitial.difficulty, 0.000001);
        assertEquals(unknownInitial.intervalDays(), nullInitial.intervalDays());

        KaniFsrsReviewResult nullReview = adapter.review(5.0, 6.0, null, 7, 0.9);
        KaniFsrsReviewResult unknownReview = adapter.review(5.0, 6.0, "???", 7, 0.9);
        assertEquals(unknownReview.stability, nullReview.stability, 0.000001);
        assertEquals(unknownReview.difficulty, nullReview.difficulty, 0.000001);
        assertEquals(unknownReview.intervalDays(), nullReview.intervalDays());
    }

    @Test
    public void latestAdapterNormalizesNegativeElapsedDaysAtTheAppBoundary() {
        LatestFsrsAdapter adapter = new LatestFsrsAdapter();

        KaniFsrsReviewResult negativeElapsed = adapter.review(5.0, 6.0, StudyRatings.GOOD, -7, 0.9);
        KaniFsrsReviewResult zeroElapsed = adapter.review(5.0, 6.0, StudyRatings.GOOD, 0, 0.9);

        assertEquals(zeroElapsed.stability, negativeElapsed.stability, 0.000001);
        assertEquals(zeroElapsed.difficulty, negativeElapsed.difficulty, 0.000001);
        assertEquals(zeroElapsed.intervalDays(), negativeElapsed.intervalDays());
    }

    @Test
    public void resultCeilsIntervalsToAtLeastOneDay() {
        KaniFsrsReviewResult result = new KaniFsrsReviewResult(1.0, 2.0, 1L);

        assertEquals(1, result.intervalDays());
        assertEquals(21, new KaniFsrsReviewResult(1.0, 2.0, 21L * KaniFsrsReviewResult.DAY_MILLIS).intervalDays());
        assertEquals(22, new KaniFsrsReviewResult(1.0, 2.0, 21L * KaniFsrsReviewResult.DAY_MILLIS + 1L).intervalDays());
    }
}
