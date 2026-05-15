package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class Fsrs5EngineTest {
    @Test
    public void constructorDefensivelyCopiesValidWeightsAndClampsRetention() {
        double[] weights = Fsrs5Engine.defaultWeights();
        weights[0] = 0.25;

        Fsrs5Engine lowRetention = new Fsrs5Engine(weights, -5.0);
        weights[0] = 99.0;
        Fsrs5Engine highRetention = new Fsrs5Engine(Fsrs5Engine.defaultWeights(), 5.0);

        assertEquals(0.25, lowRetention.getWeights()[0], 0.0001);
        assertEquals(0.01, lowRetention.getTargetRetention(), 0.0001);
        assertEquals(0.99, highRetention.getTargetRetention(), 0.0001);
    }

    @Test
    public void constructorFallsBackToDefaultWeightsWhenInputIsMissingOrShort() {
        assertArrayEquals(Fsrs5Engine.defaultWeights(), new Fsrs5Engine(null, 0.9).getWeights(), 0.0);
        assertArrayEquals(Fsrs5Engine.defaultWeights(), new Fsrs5Engine(new double[]{1.0, 2.0}, 0.9).getWeights(), 0.0);
    }

    @Test
    public void initialValuesClampOutOfRangeRatingsAndDegenerateWeights() {
        double[] weights = Fsrs5Engine.defaultWeights();
        weights[0] = -1.0;
        weights[3] = 50_000.0;
        weights[4] = -5.0;
        Fsrs5Engine engine = new Fsrs5Engine(weights, 0.9);

        assertEquals(0.01, engine.initialStability(-10), 0.0001);
        assertEquals(36500.0, engine.initialStability(99), 0.0001);
        assertEquals(1.0, engine.initialDifficulty(1), 0.0001);
        assertEquals(1.0, engine.initialDifficulty(99), 0.0001);
    }

    @Test
    public void retrievabilityTreatsInvalidElapsedOrStabilityAsFullyRetrievable() {
        Fsrs5Engine engine = new Fsrs5Engine();

        assertEquals(1.0, engine.retrievability(3.0, 0.0), 0.0001);
        assertEquals(1.0, engine.retrievability(-1.0, 4.0), 0.0001);
        assertTrue(engine.retrievability(10.0, 4.0) < 1.0);
    }

    @Test
    public void nextIntervalsClampToAtLeastOneDayAndCapHugeStability() {
        Fsrs5Engine engine = new Fsrs5Engine(Fsrs5Engine.defaultWeights(), 0.99);

        assertEquals(1, engine.nextIntervalDays(0.0));
        assertEquals(86_400_000L, engine.nextIntervalMillis(0.0));
        assertEquals(engine.nextIntervalDays(36_500.0), engine.nextIntervalDays(99_999.0));
    }

    @Test
    public void reviewFormulasCoverAgainHardGoodAndEasyBoundaries() {
        Fsrs5Engine engine = new Fsrs5Engine();
        double stability = 4.0;
        double difficulty = 6.0;
        double r = engine.retrievability(3.0, stability);

        double forgot = engine.stabilityAfterForgetting(stability, difficulty, r);
        double hard = engine.stabilityAfterRecall(stability, difficulty, r, Fsrs5Engine.ratingToInt("hard"));
        double good = engine.stabilityAfterRecall(stability, difficulty, r, Fsrs5Engine.ratingToInt("good"));
        double easy = engine.stabilityAfterRecall(stability, difficulty, r, Fsrs5Engine.ratingToInt("easy"));

        assertTrue(forgot <= stability);
        assertTrue(hard >= stability);
        assertTrue(good >= hard);
        assertTrue(easy >= good);
        assertEquals(forgot, engine.applyReview(stability, difficulty, 3.0, Fsrs5Engine.ratingToInt("again")), 0.0001);
        assertEquals(good, engine.applyReview(stability, difficulty, 3.0, Fsrs5Engine.ratingToInt("good")), 0.0001);
    }

    @Test
    public void sameDayReviewDoesNotReduceGoodOrEasyButCanReduceAgain() {
        Fsrs5Engine engine = new Fsrs5Engine();

        assertTrue(engine.stabilityAfterSameDayReview(4.0, Fsrs5Engine.ratingToInt("again")) < 4.0);
        assertTrue(engine.stabilityAfterSameDayReview(4.0, Fsrs5Engine.ratingToInt("good")) >= 4.0);
        assertTrue(engine.stabilityAfterSameDayReview(4.0, Fsrs5Engine.ratingToInt("easy")) >= 4.0);
    }

    @Test
    public void ratingStringsDefaultSafelyAndElapsedDaysIgnoreFutureDueTimes() {
        assertEquals(1, Fsrs5Engine.ratingToInt(null));
        assertEquals(1, Fsrs5Engine.ratingToInt("unexpected"));
        assertEquals(2, Fsrs5Engine.ratingToInt("hard"));
        assertEquals(3, Fsrs5Engine.ratingToInt("good"));
        assertEquals(4, Fsrs5Engine.ratingToInt("easy"));
        assertEquals(0.0, Fsrs5Engine.elapsedDays(2_000L, 1_000L), 0.0);
        assertEquals(2.0, Fsrs5Engine.elapsedDays(1_000L, 1_000L + 2L * 86_400_000L), 0.0);
    }
}
