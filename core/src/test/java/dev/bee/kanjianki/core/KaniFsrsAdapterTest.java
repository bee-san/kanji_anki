package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class KaniFsrsAdapterTest {
    @Test
    public void latestAdapterUsesTwentyOneParameterEngineAndClampsMigratedState() {
        LatestFsrsAdapter adapter = new LatestFsrsAdapter();

        KaniFsrsReviewResult initial = adapter.initialReview(BridgeScheduler.RATING_GOOD, 6.0, 0.9, true);
        assertEquals(2.3065, initial.stability, 0.000001);
        assertEquals(2.118103970459, initial.difficulty, 0.000001);
        assertEquals(2, initial.intervalDays());

        KaniFsrsReviewResult relearningGraduation =
                adapter.initialReview(BridgeScheduler.RATING_GOOD, 6.0, 0.9, false);
        assertEquals(2.3065, relearningGraduation.stability, 0.000001);
        assertEquals(5.989228369297, relearningGraduation.difficulty, 0.000001);
        assertEquals(2, relearningGraduation.intervalDays());

        KaniFsrsReviewResult review = adapter.review(
                0.0,
                50.0,
                BridgeScheduler.RATING_AGAIN,
                2,
                5.0
        );
        assertTrue(review.stability >= 0.001);
        assertTrue(review.difficulty <= 10.0);
        assertEquals(1, review.intervalDays());
    }

    @Test
    public void resultRoundsIntervalsToAtLeastOneDay() {
        KaniFsrsReviewResult result = new KaniFsrsReviewResult(1.0, 2.0, 1L);

        assertEquals(1, result.intervalDays());
    }
}
