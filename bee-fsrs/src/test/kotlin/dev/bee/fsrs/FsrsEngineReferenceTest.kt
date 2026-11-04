@file:Suppress("DEPRECATION")

package dev.bee.fsrs

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private const val TOLERANCE = 1.0e-9

class FsrsEngineReferenceTest {
    @Test
    fun algorithmMetadataPinsUpstreamSource() {
        assertEquals("open-spaced-repetition/py-fsrs", FsrsAlgorithmInfo.UPSTREAM_REPOSITORY)
        assertEquals("v6.3.1", FsrsAlgorithmInfo.UPSTREAM_RELEASE)
        assertEquals("3abe686e9c058d3f3c00bbeb92e68b71211b2b31", FsrsAlgorithmInfo.UPSTREAM_COMMIT)
        assertEquals("6d42ecb259bbaaa02101f13c5e1b2ec7cdc77eae", FsrsAlgorithmInfo.UPSTREAM_SCHEDULER_BLOB)
        assertEquals("FSRS-6.x 21-parameter snapshot", FsrsAlgorithmInfo.ALGORITHM_LABEL)
        assertEquals(21, FsrsAlgorithmInfo.PARAMETER_COUNT)
        assertEquals(
            "open-spaced-repetition/py-fsrs v6.3.1 3abe686e9c058d3f3c00bbeb92e68b71211b2b31",
            FsrsAlgorithmInfo.upstreamReference(),
        )
    }

    @Test
    fun ratingsExposeUpstreamValuesAndRejectUnknownValues() {
        assertEquals(1, FsrsRating.AGAIN.value())
        assertEquals(2, FsrsRating.HARD.value())
        assertEquals(3, FsrsRating.GOOD.value())
        assertEquals(4, FsrsRating.EASY.value())
        assertEquals(FsrsRating.AGAIN, FsrsRating.fromValue(1))
        assertEquals(FsrsRating.EASY, FsrsRating.fromValue(4))
        expectIllegalArgument { FsrsRating.fromValue(0) }
    }

    @Test
    fun parametersValidateAndDefensivelyCopyValues() {
        val defaults = FsrsParameters.latestDefault()
        val values = defaults.toArray()

        assertEquals(21, FsrsParameters.PARAMETER_COUNT)
        assertArrayEquals(FsrsParameters.latestDefaultValues(), values, 0.0)
        assertArrayEquals(FsrsParameters.latestDefaultValues(), FsrsParameters.LATEST_DEFAULT_VALUES, 0.0)
        assertEquals(0.212, defaults.get(0), 0.0)
        assertEquals(0.1542, defaults.decayMagnitude(), 0.0)
        assertEquals(-0.1542, defaults.decay(), 0.0)
        assertEquals(Math.pow(0.9, 1.0 / defaults.decay()) - 1.0, defaults.factor(), 0.0)
        assertEquals(0.212, defaults.initialStability(FsrsRating.AGAIN), 0.0)
        assertEquals(8.2956, defaults.initialStability(FsrsRating.EASY), 0.0)
        assertEquals(6.4133, defaults.initialDifficultyBase(), 0.0)
        assertEquals(0.8334, defaults.initialDifficultyExponent(), 0.0)
        assertEquals(3.0194, defaults.difficultyDeltaScale(), 0.0)
        assertEquals(0.001, defaults.difficultyMeanReversionWeight(), 0.0)
        assertEquals(1.8722, defaults.recallStabilityBase(), 0.0)
        assertEquals(0.1666, defaults.recallStabilityStabilityDecay(), 0.0)
        assertEquals(0.796, defaults.recallStabilityRetrievabilitySensitivity(), 0.0)
        assertEquals(1.4835, defaults.forgetStabilityBase(), 0.0)
        assertEquals(0.0614, defaults.forgetStabilityDifficultyDecay(), 0.0)
        assertEquals(0.2629, defaults.forgetStabilityStabilityGrowth(), 0.0)
        assertEquals(1.6483, defaults.forgetStabilityRetrievabilitySensitivity(), 0.0)
        assertEquals(0.6014, defaults.hardPenalty(), 0.0)
        assertEquals(1.8729, defaults.easyBonus(), 0.0)
        assertEquals(0.5425, defaults.shortTermBase(), 0.0)
        assertEquals(0.0912, defaults.shortTermRatingOffset(), 0.0)
        assertEquals(0.0658, defaults.shortTermStabilityDecay(), 0.0)
        assertTrue(defaults.toString().startsWith("FsrsParameters["))

        values[0] = 99.0
        assertEquals(0.212, defaults.get(0), 0.0)

        val custom = FsrsParameters.latestDefaultValues()
        val copied = FsrsParameters.of(custom)
        custom[0] = 99.0
        assertEquals(0.212, copied.get(0), 0.0)
    }

    @Test
    fun parametersFactoriesAndInvalidInputsStayGuarded() {
        val custom = FsrsParameters.latestDefaultValues()
        expectIllegalArgument { FsrsParameters.of(null) }
        expectIllegalArgument { FsrsParameters.of(custom.copyOf(20)) }
        val nonFinite = FsrsParameters.latestDefaultValues()
        nonFinite[0] = Double.NaN
        expectIllegalArgument { FsrsParameters.of(nonFinite) }
        val badDecay = FsrsParameters.latestDefaultValues()
        badDecay[20] = 0.0
        expectIllegalArgument { FsrsParameters.of(badDecay) }
        val overflowingFactor = FsrsParameters.latestDefaultValues()
        overflowingFactor[20] = Double.MIN_VALUE
        expectIllegalArgument { FsrsParameters.of(overflowingFactor) }
        val zeroFactor = FsrsParameters.latestDefaultValues()
        zeroFactor[20] = Double.MAX_VALUE
        expectIllegalArgument { FsrsParameters.of(zeroFactor) }
        val operationallyUnsafeFactor = FsrsParameters.latestDefaultValues()
        operationallyUnsafeFactor[20] = 0.00015
        expectIllegalArgument { FsrsParameters.of(operationallyUnsafeFactor) }
    }

    @Test
    fun deprecatedPublicDefaultArrayDoesNotBackCurrentDefaults() {
        val original = FsrsParameters.LATEST_DEFAULT_VALUES[0]
        try {
            FsrsParameters.LATEST_DEFAULT_VALUES[0] = 99.0

            assertEquals(original, FsrsParameters.latestDefault().get(0), 0.0)
            assertEquals(original, FsrsParameters.latestDefaultValues()[0], 0.0)
            assertState(FsrsEngine.latestDefault().initialState(FsrsRating.AGAIN), original, 6.4133)
        } finally {
            FsrsParameters.LATEST_DEFAULT_VALUES[0] = original
        }
    }

    @Test
    fun memoryStateAndReviewModelsExposeValidatedFields() {
        assertTrue(FsrsMemoryState::class.java.isRecord)
        assertTrue(FsrsReviewInput::class.java.isRecord)
        assertTrue(FsrsReviewOutput::class.java.isRecord)

        val state = FsrsMemoryState(5.0, 6.0)
        assertEquals(5.0, state.stability, 0.0)
        assertEquals(6.0, state.difficulty, 0.0)
        assertEquals("FsrsMemoryState{stability=5.0, difficulty=6.0}", state.toString())

        val input = FsrsReviewInput(state, FsrsRating.GOOD, 7, 0.9, 36500)
        assertEquals(state, input.previousState)
        assertEquals(FsrsRating.GOOD, input.rating)
        assertEquals(7, input.elapsedDays)
        assertEquals(0.9, input.desiredRetention, 0.0)
        assertEquals(36500, input.maximumInterval)

        val output = FsrsReviewOutput(state, 0.8, 9)
        assertEquals(state, output.nextState)
        assertEquals(0.8, output.retrievability, 0.0)
        assertEquals(9, output.nextIntervalDays)
    }

    @Test
    fun memoryStateAndReviewModelsRejectInvalidInputs() {
        val state = FsrsMemoryState(5.0, 6.0)
        expectIllegalArgument { FsrsMemoryState(0.0, 6.0) }
        expectIllegalArgument { FsrsMemoryState(5.0, 0.5) }
        expectIllegalArgument { FsrsMemoryState(5.0, 10.5) }
        expectIllegalArgument { FsrsReviewInput(null, FsrsRating.GOOD, 7, 0.9, 36500) }
        expectIllegalArgument { FsrsReviewInput(state, null, 7, 0.9, 36500) }
        expectIllegalArgument { FsrsReviewInput(state, FsrsRating.GOOD, -1, 0.9, 36500) }
        expectIllegalArgument { FsrsReviewInput(state, FsrsRating.GOOD, 7, 1.0, 36500) }
        expectIllegalArgument { FsrsReviewInput(state, FsrsRating.GOOD, 7, 0.9, 0) }
        expectIllegalArgument { FsrsReviewOutput(null, 0.8, 9) }
        expectIllegalArgument { FsrsReviewOutput(state, -0.1, 9) }
        expectIllegalArgument { FsrsReviewOutput(state, 1.1, 9) }
        expectIllegalArgument { FsrsReviewOutput(state, 0.8, 0) }
    }

    @Test
    fun helperValidationRejectsInvalidCallerInputs() {
        assertEquals(3.0, Fsrs.clamp(4.0, 1.0, 3.0), 0.0)
        assertEquals(1.0, Fsrs.clamp(0.0, 1.0, 3.0), 0.0)
        assertEquals(2.0, Fsrs.clamp(2.0, 1.0, 3.0), 0.0)
        assertEquals("value", Fsrs.requireNonNull("value", "name"))

        expectIllegalArgument { Fsrs.requireNonNull(null, "name") }
        expectIllegalArgument { Fsrs.validateElapsedDays(-1) }
        expectIllegalArgument { Fsrs.validateDesiredRetention(Double.NaN) }
        expectIllegalArgument { Fsrs.validateDesiredRetention(0.0) }
        expectIllegalArgument { Fsrs.validateDesiredRetention(1.0) }
        expectIllegalArgument { Fsrs.validateMaximumInterval(0) }
    }

    @Test
    fun engineMatchesPinnedReferenceCases() {
        val engine = FsrsEngine.latestDefault()

        assertState(engine.initialState(FsrsRating.AGAIN), 0.212, 6.4133)
        assertState(engine.initialState(FsrsRating.HARD), 1.2931, 5.112170705601)
        assertState(engine.initialState(FsrsRating.GOOD), 2.3065, 2.118103970459)
        assertState(engine.initialState(FsrsRating.EASY), 8.2956, 1.0)

        val previous = FsrsMemoryState(5.0, 6.0)
        assertEquals(0.875273180864, engine.retrievability(previous, 7), TOLERANCE)
        assertEquals(1.0, engine.retrievability(previous, 0), TOLERANCE)

        assertState(engine.nextState(previous, FsrsRating.AGAIN, 7), 0.960159333642, 8.670455569297)
        assertState(engine.nextState(previous, FsrsRating.HARD, 7), 10.728832429032, 7.329841969297)
        assertState(engine.nextState(previous, FsrsRating.GOOD, 7), 18.005364860252, 5.989228369297)
        assertState(engine.nextState(previous, FsrsRating.EASY, 7), 35.874574083399, 4.648614769297)
        assertState(engine.nextState(previous, FsrsRating.HARD, 0), 2.747009588922, 7.329841969297)
        assertState(engine.nextState(previous, FsrsRating.GOOD, 0), 5.0, 5.989228369297)
        assertState(engine.nextState(previous, FsrsRating.EASY, 0), 8.129609559916, 4.648614769297)

        assertEquals(1, engine.nextIntervalDays(0.001, 0.9, 36500))
        assertEquals(365, engine.nextIntervalDays(50000.0, 0.9, 365))
        assertEquals(2.747009588922, engine.shortTermStability(5.0, FsrsRating.HARD), TOLERANCE)
        assertEquals(5.0, engine.shortTermStability(5.0, FsrsRating.GOOD), TOLERANCE)
        assertEquals(5.989228369297, engine.nextDifficulty(6.0, FsrsRating.GOOD), TOLERANCE)

        val output = requireNotNull(engine.review(FsrsReviewInput(previous, FsrsRating.GOOD, 7, 0.9, 36500)))
        assertState(output.nextState, 18.005364860252, 5.989228369297)
        assertEquals(0.875273180864, output.retrievability, TOLERANCE)
        assertEquals(18, output.nextIntervalDays)
    }

    @Test
    fun sameDayAgainReviewUsesShortTermStabilityLikeUpstream() {
        val engine = FsrsEngine.latestDefault()
        val previous = FsrsMemoryState(5.0, 6.0)

        val next = engine.nextState(previous, FsrsRating.AGAIN, 0)

        // py-fsrs v6.3.1 routes every same-day review, including Again,
        // through the short-term stability update instead of forget stability.
        assertEquals(engine.shortTermStability(5.0, FsrsRating.AGAIN), next.stability, TOLERANCE)
        assertEquals(engine.nextDifficulty(6.0, FsrsRating.AGAIN), next.difficulty, TOLERANCE)
    }

    @Test
    fun nextIntervalDaysClampsExtremeStabilityToMaximumInterval() {
        val engine = FsrsEngine.latestDefault()

        // A pathological stability must clamp to the maximum interval instead
        // of wrapping the Int narrowing and collapsing to the one-day minimum.
        assertEquals(36500, engine.nextIntervalDays(3.0e9, 0.9, 36500))
    }

    @Test
    fun engineRejectsInvalidRuntimeInputs() {
        val engine = FsrsEngine.create(FsrsParameters.latestDefault())
        val state = FsrsMemoryState(5.0, 6.0)

        assertNotEquals(engine, FsrsEngine.latestDefault())
        expectIllegalArgument { FsrsEngine.create(null) }
        expectIllegalArgument { engine.initialState(null) }
        expectIllegalArgument { engine.retrievability(null, 1) }
        expectIllegalArgument { engine.retrievability(state, -1) }
        expectIllegalArgument { engine.nextState(null, FsrsRating.GOOD, 1) }
        expectIllegalArgument { engine.nextState(state, null, 1) }
        expectIllegalArgument { engine.nextState(state, FsrsRating.GOOD, -1) }
        expectIllegalArgument { engine.nextDifficulty(0.0, FsrsRating.GOOD) }
        expectIllegalArgument { engine.nextDifficulty(5.0, null) }
        expectIllegalArgument { engine.shortTermStability(0.0, FsrsRating.GOOD) }
        expectIllegalArgument { engine.shortTermStability(5.0, null) }
        expectIllegalArgument { engine.nextIntervalDays(0.0, 0.9, 36500) }
        expectIllegalArgument { engine.nextIntervalDays(5.0, 1.0, 36500) }
        expectIllegalArgument { engine.nextIntervalDays(5.0, 0.9, 0) }
        expectIllegalArgument { engine.review(null) }
    }

    @Test
    fun engineInterfaceKeepsDefaultNextDifficultyForExistingImplementations() {
        val engine = object : FsrsEngine {
            override fun initialState(firstRating: FsrsRating?): FsrsMemoryState = FsrsMemoryState(1.0, 5.0)

            override fun retrievability(state: FsrsMemoryState?, elapsedDays: Int): Double = 0.9

            override fun nextState(
                previousState: FsrsMemoryState?,
                rating: FsrsRating?,
                elapsedDays: Int,
            ): FsrsMemoryState = FsrsMemoryState(requireNotNull(previousState).stability, 4.0)

            override fun shortTermStability(stability: Double, rating: FsrsRating?): Double = stability

            override fun nextIntervalDays(stability: Double, desiredRetention: Double, maximumInterval: Int): Int = 1

            override fun review(input: FsrsReviewInput?): FsrsReviewOutput =
                FsrsReviewOutput(requireNotNull(input).previousState, 0.9, 1)
        }

        assertEquals(4.0, engine.nextDifficulty(6.0, FsrsRating.GOOD), 0.0)
    }

    private fun assertState(state: FsrsMemoryState?, stability: Double, difficulty: Double) {
        val actual = requireNotNull(state)
        assertEquals(stability, actual.stability, TOLERANCE)
        assertEquals(difficulty, actual.difficulty, TOLERANCE)
    }

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertNotNull(expected.message)
        }
    }
}
