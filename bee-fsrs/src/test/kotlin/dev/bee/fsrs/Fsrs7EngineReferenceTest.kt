package dev.bee.fsrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private const val TOLERANCE = 1.0e-9

/**
 * Contract tests for the FSRS-7 API, as distinct from its mathematics.
 *
 * The reference fixture covers whether the numbers are right. This covers what the
 * fixture cannot: that the algorithm's identity is pinned, that invalid input is
 * rejected rather than silently producing a plausible schedule, and that the
 * parameter bounds upstream's optimizer enforces are enforced here too.
 */
class Fsrs7EngineReferenceTest {
    @Test
    fun algorithmMetadataPinsUpstreamSource() {
        assertEquals("open-spaced-repetition/srs-benchmark", Fsrs7AlgorithmInfo.UPSTREAM_REPOSITORY)
        assertEquals("70cc4387f573ff20b13ac9c106333a335c8a4cb8", Fsrs7AlgorithmInfo.UPSTREAM_COMMIT)
        assertEquals("33893c3fed0f7dbe28c2b55874a50d9b3fa77df5", Fsrs7AlgorithmInfo.UPSTREAM_MODEL_BLOB)
        assertEquals("models/fsrs_v7.py", Fsrs7AlgorithmInfo.UPSTREAM_MODEL_PATH)
        assertEquals("FSRS-7 35-parameter snapshot", Fsrs7AlgorithmInfo.ALGORITHM_LABEL)
        assertEquals(35, Fsrs7AlgorithmInfo.PARAMETER_COUNT)
        assertTrue(Fsrs7AlgorithmInfo.upstreamReference().contains("srs-benchmark"))
        assertTrue(Fsrs7AlgorithmInfo.upstreamReference().contains("fsrs_v7.py"))
    }

    @Test
    fun thisIsNotTheTwentyOneParameterFsrs6Engine() {
        // The two engines coexist in this package, so the thing that distinguishes
        // them is asserted rather than left to a label. A consumer that resolved the
        // wrong one would reschedule every learner's queue.
        assertEquals(35, Fsrs7Parameters.PARAMETER_COUNT)
        assertEquals(21, FsrsParameters.PARAMETER_COUNT)
        assertNotEquals(Fsrs7AlgorithmInfo.ALGORITHM_LABEL, FsrsAlgorithmInfo.ALGORITHM_LABEL)

        // FSRS-7's initial stabilities differ from FSRS-6's in every position.
        val fsrs7 = Fsrs7Parameters.latestDefaultValues()
        val fsrs6 = FsrsParameters.latestDefaultValues()
        for (index in 0..3) {
            assertNotEquals(
                "parameter $index must differ between the two algorithms",
                fsrs6[index],
                fsrs7[index],
            )
        }
    }

    @Test
    fun defaultParametersAreExactlyUpstreamsInitWeights() {
        // Byte-exact to FSRS7.init_w. The most consequential silent regression
        // available: changing one value reschedules every existing learner.
        val expected = doubleArrayOf(
            0.041, 2.4175, 4.1283, 11.9709,
            5.6385, 0.4468, 3.262,
            2.3054, 0.1688, 1.3325, 0.3524, 0.0049, 0.7503, 0.0896, 0.6625, 1.3,
            0.882, 0.3072, 3.5875, 0.303, 0.0107, 0.2279, 2.6413, 0.5594, 1.3,
            2.5, 1.0,
            0.0723, 0.1634, 0.5, 0.9555, 0.2245, 0.6232, 0.1362, 0.3862,
        )
        val actual = Fsrs7Parameters.latestDefaultValues()
        assertEquals(35, actual.size)
        for (index in expected.indices) {
            assertEquals("FSRS-7 parameter $index", expected[index], actual[index], 0.0)
        }
    }

    @Test
    fun parametersDefensivelyCopyAndExposeNamedAccessors() {
        val parameters = Fsrs7Parameters.latestDefault()

        // Mutating a returned array must not reach into the parameter set, or a
        // caller inspecting parameters could silently change the schedule.
        val values = parameters.toArray()
        values[0] = 99.0
        assertEquals(0.041, parameters.get(0), 0.0)

        val supplied = Fsrs7Parameters.latestDefaultValues()
        val built = Fsrs7Parameters.of(supplied)
        supplied[0] = 99.0
        assertEquals(0.041, built.get(0), 0.0)

        assertEquals(0.041, parameters.initialStability(FsrsRating.AGAIN), 0.0)
        assertEquals(11.9709, parameters.initialStability(FsrsRating.EASY), 0.0)
        assertEquals(5.6385, parameters.initialDifficultyBase(), 0.0)
        assertEquals(3.262, parameters.difficultyDeltaScale(), 0.0)
        assertEquals(2.5, parameters.transitionRate(), 0.0)
        assertEquals(1.0, parameters.transitionWeight(), 0.0)
        assertTrue(parameters.toString().startsWith("Fsrs7Parameters["))

        // The two branches are the same nine equations at offsets 7 and 16, so the
        // accessors must actually read those offsets and not both the same one.
        assertEquals(2.3054, parameters.longTerm().increaseBase(), 0.0)
        assertEquals(0.882, parameters.shortTerm().increaseBase(), 0.0)
        assertEquals(0.6625, parameters.longTerm().hardPenalty(), 0.0)
        assertEquals(0.5594, parameters.shortTerm().hardPenalty(), 0.0)
        assertEquals(1.3, parameters.longTerm().easyBonus(), 0.0)
        assertEquals(1.3, parameters.shortTerm().easyBonus(), 0.0)

        // Decays are negated at the accessor, as upstream's callers negate them.
        assertEquals(-0.0723, parameters.forgettingCurve().firstDecay(), 0.0)
        assertEquals(-0.1634, parameters.forgettingCurve().secondDecay(), 0.0)
        assertEquals(0.5, parameters.forgettingCurve().firstBase(), 0.0)
        assertEquals(0.9555, parameters.forgettingCurve().secondBase(), 0.0)
    }

    @Test
    fun parametersRejectWrongCountAndNonFiniteValues() {
        expectIllegalArgument { Fsrs7Parameters.of(null) }
        // 21 values is FSRS-6's vector: passing it here must fail loudly rather
        // than read off the end or silently pad.
        expectIllegalArgument { Fsrs7Parameters.of(FsrsParameters.latestDefaultValues()) }
        expectIllegalArgument { Fsrs7Parameters.of(DoubleArray(34)) }
        expectIllegalArgument { Fsrs7Parameters.of(DoubleArray(36)) }

        val nonFinite = Fsrs7Parameters.latestDefaultValues()
        nonFinite[8] = Double.NaN
        expectIllegalArgument { Fsrs7Parameters.of(nonFinite) }
        val infinite = Fsrs7Parameters.latestDefaultValues()
        infinite[8] = Double.POSITIVE_INFINITY
        expectIllegalArgument { Fsrs7Parameters.of(infinite) }
    }

    @Test
    fun parametersEnforceUpstreamClipperBounds() {
        // Bounds are from FSRS7ParameterClipper. A vector outside them would
        // extrapolate the mathematics into a region the benchmark never evaluated,
        // so this rejects rather than clamps: silently altering a learner's fitted
        // parameters would make their schedule unexplainable.
        expectIllegalArgument { withParameter(4, 0.5) } // difficulty base >= 1
        expectIllegalArgument { withParameter(4, 10.5) } // difficulty base <= 10
        expectIllegalArgument { withParameter(8, -0.1) } // long-term exponent >= 0
        expectIllegalArgument { withParameter(8, 1.3) } // long-term exponent <= 1.2
        expectIllegalArgument { withParameter(25, 2.4) } // transition rate >= 2.5
        expectIllegalArgument { withParameter(25, 15.1) } // transition rate <= 15
        expectIllegalArgument { withParameter(27, 0.005) } // first decay >= 0.01
        expectIllegalArgument { withParameter(27, 0.3) } // first decay <= 0.25
        expectIllegalArgument { withParameter(34, 1.2) } // second power <= 1.1

        // A value at a bound is legal; the bounds are inclusive as upstream clamps.
        Fsrs7Parameters.of(valuesWith(25, 2.5))
        Fsrs7Parameters.of(valuesWith(25, 15.0))
    }

    @Test
    fun parametersEnforceTheOrderingsThatKeepTheCurveIdentifiable() {
        // Initial stabilities must not decrease: a first review rated Easy cannot be
        // less durable than one rated Again.
        val unordered = Fsrs7Parameters.latestDefaultValues()
        unordered[2] = unordered[1] - 0.1
        expectIllegalArgument { Fsrs7Parameters.of(unordered) }

        // The two power laws are ordered so the pair has one parameterisation
        // rather than two with the roles swapped.
        val swappedDecay = Fsrs7Parameters.latestDefaultValues()
        swappedDecay[28] = swappedDecay[27] - 0.01
        expectIllegalArgument { Fsrs7Parameters.of(swappedDecay) }

        val swappedBase = Fsrs7Parameters.latestDefaultValues()
        swappedBase[30] = swappedBase[29] - 0.01
        expectIllegalArgument { Fsrs7Parameters.of(swappedBase) }
    }

    @Test
    fun elapsedTimeIsFractionalUnlikeFsrs6() {
        val engine = Fsrs7Engine.latestDefault()
        val state = FsrsMemoryState(5.0, 6.0)

        // The point of the revision: a ten-minute gap and a one-day gap are
        // different reviews, where FSRS-6 collapsed both onto integer days.
        val tenMinutes = engine.nextState(state, FsrsRating.GOOD, 10.0 / (24.0 * 60.0))
        val oneDay = engine.nextState(state, FsrsRating.GOOD, 1.0)
        assertNotEquals(tenMinutes.stability, oneDay.stability)

        // Retrievability strictly decreases with elapsed time, and a same-day
        // review has a meaningful value rather than exactly 1.
        val immediate = engine.retrievability(state, 0.0)
        val tenMinutesLater = engine.retrievability(state, 10.0 / (24.0 * 60.0))
        assertEquals(1.0, immediate, TOLERANCE)
        assertTrue(tenMinutesLater < immediate)
        assertTrue(tenMinutesLater > 0.9)
    }

    @Test
    fun intervalsAreFractionalAndInvertTheForgettingCurve() {
        val engine = Fsrs7Engine.latestDefault()

        // A low-stability item is due in well under a day. Under FSRS-6's integer
        // contract this rounded to 1 day, which is the behaviour change.
        val shortInterval = engine.nextIntervalDays(0.1, 0.9, Fsrs7.STABILITY_MAX)
        assertTrue("expected a sub-day interval, got $shortInterval", shortInterval < 1.0)
        assertTrue(shortInterval > 0.0)

        // The returned interval is the inverse of the curve: retrievability at that
        // interval is the requested retention. This is the property that matters,
        // and it holds independently of how the root is found.
        for (stability in doubleArrayOf(0.01, 1.0, 10.0, 1000.0)) {
            for (retention in doubleArrayOf(0.7, 0.9, 0.97)) {
                val interval = engine.nextIntervalDays(stability, retention, Fsrs7.STABILITY_MAX)
                val achieved = engine.retrievability(FsrsMemoryState(stability, 5.0), interval)
                assertEquals("s=$stability dr=$retention", retention, achieved, 1.0e-9)
            }
        }

        // Higher desired retention means a shorter interval, always.
        val strict = engine.nextIntervalDays(10.0, 0.97, Fsrs7.STABILITY_MAX)
        val lenient = engine.nextIntervalDays(10.0, 0.7, Fsrs7.STABILITY_MAX)
        assertTrue(strict < lenient)
    }

    @Test
    fun intervalRespectsTheMaximumIncludingWhenTheRootIsBeyondIt() {
        val engine = Fsrs7Engine.latestDefault()

        // A very stable item's true interval exceeds any sane cap, so the cap is
        // returned rather than a value past it.
        assertEquals(30.0, engine.nextIntervalDays(36_500.0, 0.9, 30.0), TOLERANCE)

        // A cap above the true root leaves the root untouched.
        val uncapped = engine.nextIntervalDays(10.0, 0.9, Fsrs7.STABILITY_MAX)
        assertEquals(uncapped, engine.nextIntervalDays(10.0, 0.9, uncapped * 2.0), TOLERANCE)
    }

    @Test
    fun reviewCombinesStateRetrievabilityAndInterval() {
        val engine = Fsrs7Engine.latestDefault()
        val previous = FsrsMemoryState(5.0, 6.0)

        val output = requireNotNull(
            engine.review(Fsrs7ReviewInput(previous, FsrsRating.GOOD, 3.0, 0.9, Fsrs7.STABILITY_MAX)),
        )

        // review() must agree with the individual calls rather than reimplementing
        // them, or the convenience method becomes a second engine.
        assertEquals(engine.retrievability(previous, 3.0), output.retrievability, 0.0)
        val expectedState = engine.nextState(previous, FsrsRating.GOOD, 3.0)
        assertEquals(expectedState.stability, requireNotNull(output.nextState).stability, 0.0)
        assertEquals(expectedState.difficulty, requireNotNull(output.nextState).difficulty, 0.0)
        assertEquals(
            engine.nextIntervalDays(expectedState.stability, 0.9, Fsrs7.STABILITY_MAX),
            output.nextIntervalDays,
            0.0,
        )
    }

    @Test
    fun stabilityAndDifficultyStayInsideUpstreamsClamps() {
        val engine = Fsrs7Engine.latestDefault()

        // Repeated lapses must not drive stability below the floor or difficulty
        // above the ceiling, however long the fold runs.
        var state = engine.initialState(FsrsRating.AGAIN)
        repeat(200) {
            state = engine.nextState(state, FsrsRating.AGAIN, 30.0)
            assertTrue("stability underflowed: ${state.stability}", state.stability >= Fsrs7.STABILITY_MIN)
            assertTrue("difficulty overflowed: ${state.difficulty}", state.difficulty <= Fsrs7.MAX_DIFFICULTY)
        }

        // And repeated Easy reviews must not exceed the stability ceiling or drop
        // difficulty below its floor.
        var easy = engine.initialState(FsrsRating.EASY)
        repeat(200) {
            easy = engine.nextState(easy, FsrsRating.EASY, 365.0)
            assertTrue("stability overflowed: ${easy.stability}", easy.stability <= Fsrs7.STABILITY_MAX)
            assertTrue("difficulty underflowed: ${easy.difficulty}", easy.difficulty >= Fsrs7.MIN_DIFFICULTY)
        }
    }

    @Test
    fun engineRejectsInvalidRuntimeInputs() {
        val engine = Fsrs7Engine.latestDefault()
        val state = FsrsMemoryState(5.0, 6.0)

        expectIllegalArgument { Fsrs7Engine.create(null) }
        expectIllegalArgument { engine.initialState(null) }
        expectIllegalArgument { engine.retrievability(null, 1.0) }
        expectIllegalArgument { engine.retrievability(state, -1.0) }
        expectIllegalArgument { engine.retrievability(state, Double.NaN) }
        expectIllegalArgument { engine.nextState(null, FsrsRating.GOOD, 1.0) }
        expectIllegalArgument { engine.nextState(state, null, 1.0) }
        expectIllegalArgument { engine.nextState(state, FsrsRating.GOOD, -0.5) }
        expectIllegalArgument { engine.nextDifficulty(0.5, FsrsRating.GOOD) }
        expectIllegalArgument { engine.nextDifficulty(10.5, FsrsRating.GOOD) }
        expectIllegalArgument { engine.nextDifficulty(5.0, null) }
        expectIllegalArgument { engine.nextIntervalDays(0.0, 0.9, 100.0) }
        expectIllegalArgument { engine.nextIntervalDays(5.0, 0.0, 100.0) }
        expectIllegalArgument { engine.nextIntervalDays(5.0, 1.0, 100.0) }
        expectIllegalArgument { engine.nextIntervalDays(5.0, 0.9, 0.0) }
        expectIllegalArgument { engine.review(null) }
    }

    @Test
    fun reviewModelsValidateTheirFields() {
        val state = FsrsMemoryState(5.0, 6.0)

        assertTrue(Fsrs7ReviewInput::class.java.isRecord)
        assertTrue(Fsrs7ReviewOutput::class.java.isRecord)

        expectIllegalArgument { Fsrs7ReviewInput(null, FsrsRating.GOOD, 1.0, 0.9, 100.0) }
        expectIllegalArgument { Fsrs7ReviewInput(state, null, 1.0, 0.9, 100.0) }
        expectIllegalArgument { Fsrs7ReviewInput(state, FsrsRating.GOOD, -1.0, 0.9, 100.0) }
        expectIllegalArgument { Fsrs7ReviewInput(state, FsrsRating.GOOD, 1.0, 1.0, 100.0) }
        expectIllegalArgument { Fsrs7ReviewInput(state, FsrsRating.GOOD, 1.0, 0.9, 0.0) }

        expectIllegalArgument { Fsrs7ReviewOutput(null, 0.8, 1.0) }
        expectIllegalArgument { Fsrs7ReviewOutput(state, -0.1, 1.0) }
        expectIllegalArgument { Fsrs7ReviewOutput(state, 1.1, 1.0) }
        expectIllegalArgument { Fsrs7ReviewOutput(state, 0.8, 0.0) }

        // A sub-day interval is valid output here, unlike FSRS-6 where the minimum
        // was one whole day.
        val output = Fsrs7ReviewOutput(state, 0.8, 0.00694)
        assertEquals(0.00694, output.nextIntervalDays, 0.0)
    }

    @Test
    fun helperValidationRejectsInvalidCallerInputs() {
        assertEquals(0.0001, Fsrs7.STABILITY_MIN, 0.0)
        assertEquals(36_500.0, Fsrs7.STABILITY_MAX, 0.0)

        // FSRS-7's floor is ten times tighter than FSRS-6's, because upstream runs
        // it with sub-day intervals enabled.
        assertNotEquals(Fsrs.STABILITY_MIN, Fsrs7.STABILITY_MIN)

        expectIllegalArgument { Fsrs7.validateElapsedDays(-0.1) }
        expectIllegalArgument { Fsrs7.validateElapsedDays(Double.NaN) }
        expectIllegalArgument { Fsrs7.validateDesiredRetention(0.0) }
        expectIllegalArgument { Fsrs7.validateDesiredRetention(1.0) }
        expectIllegalArgument { Fsrs7.validateMaximumInterval(0.0) }
        expectIllegalArgument { Fsrs7.validateStability(0.0) }
        expectIllegalArgument { Fsrs7.validateStability(Double.NaN) }
    }

    private fun withParameter(index: Int, value: Double): Fsrs7Parameters =
        Fsrs7Parameters.of(valuesWith(index, value))

    private fun valuesWith(index: Int, value: Double): DoubleArray {
        val values = Fsrs7Parameters.latestDefaultValues()
        values[index] = value
        return values
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
