package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class KaniFsrsAdapterTest {
    @Test
    public void fsrs5AdapterPreservesLegacyInitialAndReviewMath() {
        Fsrs5Adapter adapter = new Fsrs5Adapter();
        Fsrs5Engine engine = new Fsrs5Engine(null, 0.9);

        KaniFsrsReviewResult initial = adapter.initialReview(BridgeScheduler.RATING_GOOD, 6.0, 0.9, true);
        int good = Fsrs5Engine.ratingToInt(BridgeScheduler.RATING_GOOD);
        double expectedInitialStability = engine.initialStability(good);
        double expectedInitialDifficulty = engine.updateDifficulty(6.0, good);
        assertEquals(expectedInitialStability, initial.stability, 0.000001);
        assertEquals(expectedInitialDifficulty, initial.difficulty, 0.000001);
        assertEquals(engine.nextIntervalMillis(expectedInitialStability), initial.intervalMillis);

        long dueAt = 1_000L;
        long now = dueAt + 7L * BridgeScheduler.DAY;
        KaniFsrsReviewResult review = adapter.review(
                5.0,
                6.0,
                BridgeScheduler.RATING_HARD,
                dueAt,
                now,
                0.9
        );
        double nextDifficulty = engine.updateDifficulty(6.0, Fsrs5Engine.ratingToInt(BridgeScheduler.RATING_HARD));
        double retrievability = engine.retrievability(7.0, 5.0);
        double nextStability = engine.stabilityAfterRecall(
                5.0,
                nextDifficulty,
                retrievability,
                Fsrs5Engine.ratingToInt(BridgeScheduler.RATING_HARD)
        );
        assertEquals(nextDifficulty, review.difficulty, 0.000001);
        assertEquals(nextStability, review.stability, 0.000001);
        assertEquals(engine.nextIntervalMillis(nextStability), review.intervalMillis);
    }

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
                1_000L,
                1_000L + 2L * BridgeScheduler.DAY,
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
