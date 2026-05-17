package dev.bee.fsrs;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class FsrsEngineReferenceTest {
    private static final double TOLERANCE = 1.0e-9;

    @Test
    public void algorithmMetadataPinsUpstreamSource() throws Exception {
        assertEquals("open-spaced-repetition/py-fsrs", FsrsAlgorithmInfo.UPSTREAM_REPOSITORY);
        assertEquals("v6.3.1", FsrsAlgorithmInfo.UPSTREAM_RELEASE);
        assertEquals("3abe686e9c058d3f3c00bbeb92e68b71211b2b31", FsrsAlgorithmInfo.UPSTREAM_COMMIT);
        assertEquals("6d42ecb259bbaaa02101f13c5e1b2ec7cdc77eae", FsrsAlgorithmInfo.UPSTREAM_SCHEDULER_BLOB);
        assertEquals("FSRS-6.x 21-parameter snapshot", FsrsAlgorithmInfo.ALGORITHM_LABEL);
        assertEquals(21, FsrsAlgorithmInfo.PARAMETER_COUNT);

        Constructor<FsrsAlgorithmInfo> constructor = FsrsAlgorithmInfo.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());
    }

    @Test
    public void ratingsExposeUpstreamValuesAndRejectUnknownValues() {
        assertEquals(1, FsrsRating.AGAIN.value());
        assertEquals(2, FsrsRating.HARD.value());
        assertEquals(3, FsrsRating.GOOD.value());
        assertEquals(4, FsrsRating.EASY.value());
        assertEquals(FsrsRating.AGAIN, FsrsRating.fromValue(1));
        assertEquals(FsrsRating.EASY, FsrsRating.fromValue(4));
        expectIllegalArgument(() -> FsrsRating.fromValue(0));
    }

    @Test
    public void parametersValidateAndDefensivelyCopyValues() {
        FsrsParameters defaults = FsrsParameters.latestDefault();
        double[] values = defaults.toArray();

        assertEquals(21, FsrsParameters.PARAMETER_COUNT);
        assertArrayEquals(FsrsParameters.latestDefaultValues(), values, 0.0);
        assertEquals(0.212, defaults.get(0), 0.0);
        assertEquals(0.1542, defaults.decayMagnitude(), 0.0);
        assertEquals(-0.1542, defaults.decay(), 0.0);
        assertEquals(Math.pow(0.9, 1.0 / defaults.decay()) - 1.0, defaults.factor(), 0.0);
        assertTrue(defaults.toString().startsWith("FsrsParameters["));

        values[0] = 99.0;
        assertEquals(0.212, defaults.get(0), 0.0);

        double[] custom = FsrsParameters.latestDefaultValues();
        FsrsParameters copied = FsrsParameters.of(custom);
        custom[0] = 99.0;
        assertEquals(0.212, copied.get(0), 0.0);

        expectIllegalArgument(() -> FsrsParameters.of(null));
        expectIllegalArgument(() -> FsrsParameters.of(Arrays.copyOf(custom, 20)));
        double[] nonFinite = FsrsParameters.latestDefaultValues();
        nonFinite[0] = Double.NaN;
        expectIllegalArgument(() -> FsrsParameters.of(nonFinite));
        double[] badDecay = FsrsParameters.latestDefaultValues();
        badDecay[20] = 0.0;
        expectIllegalArgument(() -> FsrsParameters.of(badDecay));
    }

    @Test
    public void memoryStateAndReviewModelsValidateInputs() {
        FsrsMemoryState state = new FsrsMemoryState(5.0, 6.0);
        assertEquals(5.0, state.stability(), 0.0);
        assertEquals(6.0, state.difficulty(), 0.0);
        assertEquals("FsrsMemoryState{stability=5.0, difficulty=6.0}", state.toString());

        FsrsReviewInput input = new FsrsReviewInput(state, FsrsRating.GOOD, 7, 0.9, 36500);
        assertEquals(state, input.previousState());
        assertEquals(FsrsRating.GOOD, input.rating());
        assertEquals(7, input.elapsedDays());
        assertEquals(0.9, input.desiredRetention(), 0.0);
        assertEquals(36500, input.maximumInterval());

        FsrsReviewOutput output = new FsrsReviewOutput(state, 0.8, 9);
        assertEquals(state, output.nextState());
        assertEquals(0.8, output.retrievability(), 0.0);
        assertEquals(9, output.nextIntervalDays());

        expectIllegalArgument(() -> new FsrsMemoryState(0.0, 6.0));
        expectIllegalArgument(() -> new FsrsMemoryState(5.0, 0.5));
        expectIllegalArgument(() -> new FsrsMemoryState(5.0, 10.5));
        expectIllegalArgument(() -> new FsrsReviewInput(null, FsrsRating.GOOD, 7, 0.9, 36500));
        expectIllegalArgument(() -> new FsrsReviewInput(state, null, 7, 0.9, 36500));
        expectIllegalArgument(() -> new FsrsReviewInput(state, FsrsRating.GOOD, -1, 0.9, 36500));
        expectIllegalArgument(() -> new FsrsReviewInput(state, FsrsRating.GOOD, 7, 1.0, 36500));
        expectIllegalArgument(() -> new FsrsReviewInput(state, FsrsRating.GOOD, 7, 0.9, 0));
        expectIllegalArgument(() -> new FsrsReviewOutput(null, 0.8, 9));
        expectIllegalArgument(() -> new FsrsReviewOutput(state, -0.1, 9));
        expectIllegalArgument(() -> new FsrsReviewOutput(state, 1.1, 9));
        expectIllegalArgument(() -> new FsrsReviewOutput(state, 0.8, 0));
    }

    @Test
    public void helperValidationRejectsInvalidCallerInputs() {
        assertEquals(3.0, Fsrs.clamp(4.0, 1.0, 3.0), 0.0);
        assertEquals(1.0, Fsrs.clamp(0.0, 1.0, 3.0), 0.0);
        assertEquals(2.0, Fsrs.clamp(2.0, 1.0, 3.0), 0.0);
        assertEquals("value", Fsrs.requireNonNull("value", "name"));

        expectIllegalArgument(() -> Fsrs.requireNonNull(null, "name"));
        expectIllegalArgument(() -> Fsrs.validateElapsedDays(-1));
        expectIllegalArgument(() -> Fsrs.validateDesiredRetention(Double.NaN));
        expectIllegalArgument(() -> Fsrs.validateDesiredRetention(0.0));
        expectIllegalArgument(() -> Fsrs.validateDesiredRetention(1.0));
        expectIllegalArgument(() -> Fsrs.validateMaximumInterval(0));
    }

    @Test
    public void engineMatchesPinnedReferenceCases() {
        FsrsEngine engine = FsrsEngine.latestDefault();

        assertState(engine.initialState(FsrsRating.AGAIN), 0.212, 6.4133);
        assertState(engine.initialState(FsrsRating.HARD), 1.2931, 5.112170705601);
        assertState(engine.initialState(FsrsRating.GOOD), 2.3065, 2.118103970459);
        assertState(engine.initialState(FsrsRating.EASY), 8.2956, 1.0);

        FsrsMemoryState previous = new FsrsMemoryState(5.0, 6.0);
        assertEquals(0.875273180864, engine.retrievability(previous, 7), TOLERANCE);
        assertEquals(1.0, engine.retrievability(previous, 0), TOLERANCE);

        assertState(engine.nextState(previous, FsrsRating.AGAIN, 7), 0.960159333642, 8.670455569297);
        assertState(engine.nextState(previous, FsrsRating.HARD, 7), 10.728832429032, 7.329841969297);
        assertState(engine.nextState(previous, FsrsRating.GOOD, 7), 18.005364860252, 5.989228369297);
        assertState(engine.nextState(previous, FsrsRating.EASY, 7), 35.874574083399, 4.648614769297);
        assertState(engine.nextState(previous, FsrsRating.HARD, 0), 2.747009588922, 7.329841969297);
        assertState(engine.nextState(previous, FsrsRating.GOOD, 0), 5.0, 5.989228369297);
        assertState(engine.nextState(previous, FsrsRating.EASY, 0), 8.129609559916, 4.648614769297);

        assertEquals(1, engine.nextIntervalDays(0.001, 0.9, 36500));
        assertEquals(365, engine.nextIntervalDays(50000.0, 0.9, 365));
        assertEquals(2.747009588922, engine.shortTermStability(5.0, FsrsRating.HARD), TOLERANCE);
        assertEquals(5.0, engine.shortTermStability(5.0, FsrsRating.GOOD), TOLERANCE);
        assertEquals(5.989228369297, engine.nextDifficulty(6.0, FsrsRating.GOOD), TOLERANCE);

        FsrsReviewOutput output = engine.review(new FsrsReviewInput(previous, FsrsRating.GOOD, 7, 0.9, 36500));
        assertState(output.nextState(), 18.005364860252, 5.989228369297);
        assertEquals(0.875273180864, output.retrievability(), TOLERANCE);
        assertEquals(18, output.nextIntervalDays());
    }

    @Test
    public void engineRejectsInvalidRuntimeInputs() {
        FsrsEngine engine = FsrsEngine.create(FsrsParameters.latestDefault());
        FsrsMemoryState state = new FsrsMemoryState(5.0, 6.0);

        assertNotEquals(engine, FsrsEngine.latestDefault());
        expectIllegalArgument(() -> FsrsEngine.create(null));
        expectIllegalArgument(() -> engine.initialState(null));
        expectIllegalArgument(() -> engine.retrievability(null, 1));
        expectIllegalArgument(() -> engine.retrievability(state, -1));
        expectIllegalArgument(() -> engine.nextState(null, FsrsRating.GOOD, 1));
        expectIllegalArgument(() -> engine.nextState(state, null, 1));
        expectIllegalArgument(() -> engine.nextState(state, FsrsRating.GOOD, -1));
        expectIllegalArgument(() -> engine.nextDifficulty(0.0, FsrsRating.GOOD));
        expectIllegalArgument(() -> engine.nextDifficulty(5.0, null));
        expectIllegalArgument(() -> engine.shortTermStability(0.0, FsrsRating.GOOD));
        expectIllegalArgument(() -> engine.shortTermStability(5.0, null));
        expectIllegalArgument(() -> engine.nextIntervalDays(0.0, 0.9, 36500));
        expectIllegalArgument(() -> engine.nextIntervalDays(5.0, 1.0, 36500));
        expectIllegalArgument(() -> engine.nextIntervalDays(5.0, 0.9, 0));
        expectIllegalArgument(() -> engine.review(null));
    }

    private static void assertState(FsrsMemoryState state, double stability, double difficulty) {
        assertEquals(stability, state.stability(), TOLERANCE);
        assertEquals(difficulty, state.difficulty(), TOLERANCE);
    }

    private static void expectIllegalArgument(ThrowingRunnable runnable) {
        try {
            runnable.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        } catch (ReflectiveOperationException reflectionFailure) {
            throw new AssertionError(reflectionFailure);
        }
    }

    private interface ThrowingRunnable {
        void run() throws ReflectiveOperationException;
    }
}
