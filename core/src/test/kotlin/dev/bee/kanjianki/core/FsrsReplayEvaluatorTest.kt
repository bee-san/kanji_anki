package dev.bee.kanjianki.core

import dev.bee.fsrs.Fsrs7Engine
import dev.bee.fsrs.Fsrs7Parameters
import dev.bee.fsrs.FsrsMemoryState
import dev.bee.fsrs.FsrsRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln

class FsrsReplayEvaluatorTest {
    @Test
    fun logLossMatchesHandComputedRecallAndFailureFixture() {
        val sequences = listOf(
            FsrsReplaySequence(10.0, 5.0, listOf(FsrsReplaySample(10.0, 3, true, 1L))),
            FsrsReplaySequence(10.0, 5.0, listOf(FsrsReplaySample(10.0, 1, false, 2L))),
        )

        val evaluation = FsrsReplayEvaluator.evaluate(Fsrs7Parameters.latestDefaultValues(), sequences)

        // Retrievability comes from the engine rather than a literal. Under FSRS-6 this
        // was exactly 0.9 for any stability, because a review at t = stability sat on
        // the single power law's definition point. FSRS-7 blends two power laws with
        // stability-dependent weights, so R at t = stability is no longer a constant —
        // it is ~0.911 at stability 10 and different at another scale. Hardcoding the
        // old 0.9 would have made this test assert FSRS-6's curve shape while claiming
        // to test log-loss composition, which is what this test is actually for.
        val engine = Fsrs7Engine.latestDefault()
        val retrievability = engine.retrievability(FsrsMemoryState(10.0, 5.0), 10.0)
        assertTrue(
            "FSRS-7's blended curve should not reproduce FSRS-6's exact 0.9 at t = stability",
            Math.abs(retrievability - 0.9) > 1e-6,
        )

        assertEquals(2, evaluation.sampleCount)
        assertEquals(-ln(retrievability) - ln(1.0 - retrievability), evaluation.totalLogLoss, 1e-12)
        assertEquals(
            (-ln(retrievability) - ln(1.0 - retrievability)) / 2.0,
            evaluation.logLoss,
            1e-12,
        )
        assertEquals(
            Double.NaN,
            FsrsReplayEvaluator.evaluate(Fsrs7Parameters.latestDefaultValues(), emptyList()).logLoss,
            0.0,
        )
    }

    @Test
    fun sameDaySamplesAdvanceStateButAreNeverScored() {
        val weights = Fsrs7Parameters.latestDefaultValues()
        val engine = Fsrs7Engine.create(Fsrs7Parameters.of(weights))
        // A same-day Again would otherwise cost -ln(1e-12) ≈ 27.6 nats and
        // dominate the whole fit.
        val sameDayFail = FsrsReplaySample(0.0, 1, false, 1L)
        val laterPass = FsrsReplaySample(10.0, 3, true, 2L)
        val sequence = FsrsReplaySequence(10.0, 5.0, listOf(sameDayFail, laterPass))

        val evaluation = FsrsReplayEvaluator.evaluate(weights, listOf(sequence))

        assertFalse(sameDayFail.isScored())
        assertTrue(laterPass.isScored())
        assertEquals(1, sequence.scoredSampleCount())
        assertEquals(1, evaluation.sampleCount)
        // The scored sample is evaluated against the state *after* the same-day
        // Again was applied, not against the untouched seed.
        val afterSameDay = engine.nextState(FsrsMemoryState(10.0, 5.0), FsrsRating.AGAIN, 0.0)
        val expected = -ln(engine.retrievability(afterSameDay, 10.0))
        assertEquals(expected, evaluation.totalLogLoss, 1e-12)
        assertTrue(evaluation.totalLogLoss < 1.0)
    }

    @Test
    fun graduationRatingSeedsFromEngineInitialStateAndMakesInitialWeightsMatter() {
        val defaults = Fsrs7Parameters.latestDefaultValues()
        val sample = FsrsReplaySample(10.0, 3, true, 1L)
        val seededFromGrade = FsrsReplaySequence(10.0, 5.0, listOf(sample), graduationRating = 3)
        val seededFromMemory = FsrsReplaySequence(10.0, 5.0, listOf(sample))

        val engine = Fsrs7Engine.create(Fsrs7Parameters.of(defaults))
        val expectedFromGrade = -ln(engine.retrievability(engine.initialState(FsrsRating.GOOD), 10.0))
        // FSRS-7 blends short- and long-term stability, so retrievability at
        // elapsed == stability is not exactly 0.9; ask the engine instead.
        val expectedFromMemory = -ln(engine.retrievability(FsrsMemoryState(10.0, 5.0), 10.0))
        assertEquals(
            expectedFromGrade,
            FsrsReplayEvaluator.evaluate(defaults, listOf(seededFromGrade)).totalLogLoss,
            1e-12,
        )
        assertEquals(
            expectedFromMemory,
            FsrsReplayEvaluator.evaluate(defaults, listOf(seededFromMemory)).totalLogLoss,
            1e-12,
        )

        // Changing w2 (initial stability for Good) moves the grade-seeded loss but
        // leaves the memory-seeded loss untouched.
        val bumped = defaults.clone().also { it[2] *= 2.0 }
        val gradeLossBumped = FsrsReplayEvaluator.evaluate(bumped, listOf(seededFromGrade)).totalLogLoss
        val memoryLossBumped = FsrsReplayEvaluator.evaluate(bumped, listOf(seededFromMemory)).totalLogLoss
        assertTrue(gradeLossBumped != expectedFromGrade)
        assertEquals(expectedFromMemory, memoryLossBumped, 1e-12)
    }

    @Test
    fun replayModelsValidateRatingsElapsedAndInitialMemory() {
        assertThrows(IllegalArgumentException::class.java) { FsrsReplaySample(-1.0, 3) }
        assertThrows(IllegalArgumentException::class.java) { FsrsReplaySample(Double.NaN, 3) }
        assertThrows(IllegalArgumentException::class.java) { FsrsReplaySample(0.0, 9) }
        assertThrows(IllegalArgumentException::class.java) { FsrsReplaySequence(0.0, 5.0, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            FsrsReplaySequence(10.0, 5.0, emptyList(), graduationRating = 7)
        }
    }

    @Test
    fun subDayElapsedTimesAreDistinguishableRatherThanCollapsedToZero() {
        // The reason the sample carries a Double. Under FSRS-6 both of these floored
        // to elapsed 0 and produced an identical loss, so the fitter could not learn
        // anything from same-day review timing.
        val tenMinutes = FsrsReplayEvaluator.evaluate(
            Fsrs7Parameters.latestDefaultValues(),
            listOf(FsrsReplaySequence(1.0, 5.0, listOf(FsrsReplaySample(10.0 / 1440.0, 3, true, 1L)))),
        )
        val twentyHours = FsrsReplayEvaluator.evaluate(
            Fsrs7Parameters.latestDefaultValues(),
            listOf(FsrsReplaySequence(1.0, 5.0, listOf(FsrsReplaySample(20.0 / 24.0, 3, true, 1L)))),
        )

        assertTrue(
            "A ten-minute gap should be easier to recall than a twenty-hour one",
            tenMinutes.logLoss < twentyHours.logLoss,
        )
    }
}
