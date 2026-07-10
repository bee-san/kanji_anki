package dev.bee.kanjianki.core

import dev.bee.fsrs.FsrsParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
    fun replayModelsValidateRatingsElapsedAndInitialMemory() {
        assertThrows(IllegalArgumentException::class.java) { FsrsReplaySample(-1, 3) }
        assertThrows(IllegalArgumentException::class.java) { FsrsReplaySample(0, 9) }
        assertThrows(IllegalArgumentException::class.java) { FsrsReplaySequence(0.0, 5.0, emptyList()) }
    }
}
