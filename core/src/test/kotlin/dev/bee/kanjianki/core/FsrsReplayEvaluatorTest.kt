package dev.bee.kanjianki.core

import dev.bee.fsrs.FsrsEngine
import dev.bee.fsrs.FsrsMemoryState
import dev.bee.fsrs.FsrsParameters
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
            FsrsReplaySequence(10.0, 5.0, listOf(FsrsReplaySample(10, 3, true, 1L))),
            FsrsReplaySequence(10.0, 5.0, listOf(FsrsReplaySample(10, 1, false, 2L))),
        )

        val evaluation = FsrsReplayEvaluator.evaluate(FsrsParameters.latestDefaultValues(), sequences)

        assertEquals(2, evaluation.sampleCount)
        assertEquals(-ln(0.9) - ln(0.1), evaluation.totalLogLoss, 1e-12)
        assertEquals((-ln(0.9) - ln(0.1)) / 2.0, evaluation.logLoss, 1e-12)
        assertEquals(Double.NaN, FsrsReplayEvaluator.evaluate(FsrsParameters.latestDefaultValues(), emptyList()).logLoss, 0.0)
    }

    @Test
    fun sameDaySamplesAdvanceStateButAreNeverScored() {
        val weights = FsrsParameters.latestDefaultValues()
        val engine = FsrsEngine.create(FsrsParameters.of(weights))
        // A same-day Again would otherwise cost -ln(1e-12) ≈ 27.6 nats and
        // dominate the whole fit.
        val sameDayFail = FsrsReplaySample(0, 1, false, 1L)
        val laterPass = FsrsReplaySample(10, 3, true, 2L)
        val sequence = FsrsReplaySequence(10.0, 5.0, listOf(sameDayFail, laterPass))

        val evaluation = FsrsReplayEvaluator.evaluate(weights, listOf(sequence))

        assertFalse(sameDayFail.isScored())
        assertTrue(laterPass.isScored())
        assertEquals(1, sequence.scoredSampleCount())
        assertEquals(1, evaluation.sampleCount)
        // The scored sample is evaluated against the state *after* the same-day
        // Again was applied, not against the untouched seed.
        val afterSameDay = engine.nextState(FsrsMemoryState(10.0, 5.0), FsrsRating.AGAIN, 0)
        val expected = -ln(engine.retrievability(afterSameDay, 10))
        assertEquals(expected, evaluation.totalLogLoss, 1e-12)
        assertTrue(evaluation.totalLogLoss < 1.0)
    }

    @Test
    fun graduationRatingSeedsFromEngineInitialStateAndMakesInitialWeightsMatter() {
        val defaults = FsrsParameters.latestDefaultValues()
        val sample = FsrsReplaySample(10, 3, true, 1L)
        val seededFromGrade = FsrsReplaySequence(10.0, 5.0, listOf(sample), graduationRating = 3)
        val seededFromMemory = FsrsReplaySequence(10.0, 5.0, listOf(sample))

        val engine = FsrsEngine.create(FsrsParameters.of(defaults))
        val expectedFromGrade = -ln(engine.retrievability(engine.initialState(FsrsRating.GOOD), 10))
        assertEquals(
            expectedFromGrade,
            FsrsReplayEvaluator.evaluate(defaults, listOf(seededFromGrade)).totalLogLoss,
            1e-12,
        )
        assertEquals(
            -ln(0.9),
            FsrsReplayEvaluator.evaluate(defaults, listOf(seededFromMemory)).totalLogLoss,
            1e-12,
        )

        // Changing w2 (initial stability for Good) moves the grade-seeded loss but
        // leaves the memory-seeded loss untouched.
        val bumped = defaults.clone().also { it[2] *= 2.0 }
        val gradeLossBumped = FsrsReplayEvaluator.evaluate(bumped, listOf(seededFromGrade)).totalLogLoss
        val memoryLossBumped = FsrsReplayEvaluator.evaluate(bumped, listOf(seededFromMemory)).totalLogLoss
        assertTrue(gradeLossBumped != expectedFromGrade)
        assertEquals(-ln(0.9), memoryLossBumped, 1e-12)
    }

    @Test
    fun replayModelsValidateRatingsElapsedAndInitialMemory() {
        assertThrows(IllegalArgumentException::class.java) { FsrsReplaySample(-1, 3) }
        assertThrows(IllegalArgumentException::class.java) { FsrsReplaySample(0, 9) }
        assertThrows(IllegalArgumentException::class.java) { FsrsReplaySequence(0.0, 5.0, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            FsrsReplaySequence(10.0, 5.0, emptyList(), graduationRating = 7)
        }
    }
}
