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
        assertTrue(relearning.difficulty >= 1.0);
        assertTrue(relearning.difficulty <= 10.0);
        assertTrue(relearning.intervalDays() >= 1);

        KaniFsrsReviewResult review = adapter.review(
                Double.NEGATIVE_INFINITY,
                Double.NaN,
                StudyRatings.HARD,
                0,
                Double.POSITIVE_INFINITY
        );
        assertTrue(review.stability >= 0.001);
        assertTrue(review.difficulty >= 1.0);
        assertTrue(review.difficulty <= 10.0);
        assertTrue(review.intervalDays() >= 1);
    }

    @Test
    public void resultCeilsIntervalsToAtLeastOneDay() {
        KaniFsrsReviewResult result = new KaniFsrsReviewResult(1.0, 2.0, 1L);

        assertEquals(1, result.intervalDays());
        assertEquals(21, new KaniFsrsReviewResult(1.0, 2.0, 21L * KaniFsrsReviewResult.DAY_MILLIS).intervalDays());
        assertEquals(22, new KaniFsrsReviewResult(1.0, 2.0, 21L * KaniFsrsReviewResult.DAY_MILLIS + 1L).intervalDays());
    }
}
