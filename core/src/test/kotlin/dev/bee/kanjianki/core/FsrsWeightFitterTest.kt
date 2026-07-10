package dev.bee.kanjianki.core

import dev.bee.fsrs.FsrsEngine
import dev.bee.fsrs.FsrsMemoryState
import dev.bee.fsrs.FsrsParameters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FsrsWeightFitterTest {
    @Test
    fun syntheticNonDefaultHistoryImprovesValidationLossWithinBoundsDeterministically() {
        val sequences = syntheticSequences()
        val config = FsrsWeightFitter.Config(
            maximumEpochs = 20,
            learningRate = 0.05,
            finiteDifferenceStep = 1e-4,
            earlyStoppingPatience = 8,
            seed = 42L,
        )

        val first = FsrsWeightFitter(config).fit(sequences)
        val second = FsrsWeightFitter(config).fit(sequences)

        assertTrue(first.fittedValidationLoss < first.defaultValidationLoss)
        assertTrue(first.relativeValidationImprovement() > 0.01)
        assertTrue(first.adopted)
        assertEquals(FsrsWeightFitter.REASON_ADOPTED, first.reason)
        first.weights.indices.forEach { index ->
            assertTrue(first.weights[index] >= FsrsWeightFitter.lowerBound(index))
            assertTrue(first.weights[index] <= FsrsWeightFitter.upperBound(index))
        }
        assertArrayEquals(first.weights, second.weights, 0.0)
        assertEquals(first.fittedValidationLoss, second.fittedValidationLoss, 0.0)
        assertEquals(400, first.trainingSampleCount)
        assertEquals(100, first.validationSampleCount)
        assertTrue(first.epochsCompleted > 0)
    }

    @Test
    fun adoptionGateRefusesSmallOrInsufficientlyImprovedFits() {
        val small = FsrsWeightFitter.adoptionDecision(399, 0.5, 0.1)
        assertFalse(small.adopted)
        assertEquals(FsrsWeightFitter.REASON_NOT_ENOUGH_HISTORY, small.reason)

        val weak = FsrsWeightFitter.adoptionDecision(400, 0.5, 0.496)
        assertFalse(weak.adopted)
        assertEquals(FsrsWeightFitter.REASON_INSUFFICIENT_IMPROVEMENT, weak.reason)

        val invalid = FsrsWeightFitter.adoptionDecision(400, Double.NaN, 0.1)
        assertFalse(invalid.adopted)
    }

    @Test
    fun fourHundredMinimumAppliesToTrainingPartitionNotWholeHistory() {
        val result = FsrsWeightFitter(
            FsrsWeightFitter.Config(maximumEpochs = 1, earlyStoppingPatience = 1),
        ).fit(syntheticSequences().take(499))

        assertEquals(499, result.sampleCount)
        assertEquals(399, result.trainingSampleCount)
        assertEquals(100, result.validationSampleCount)
        assertFalse(result.adopted)
        assertEquals(FsrsWeightFitter.REASON_NOT_ENOUGH_HISTORY, result.reason)
    }

    @Test
    fun emptyAndCancelledRunsReturnDefaultsWithoutAdoption() {
        val empty = FsrsWeightFitter().fit(emptyList())
        assertEquals(0, empty.sampleCount)
        assertFalse(empty.adopted)
        assertEquals(FsrsWeightFitter.REASON_NOT_ENOUGH_HISTORY, empty.reason)

        val cancelled = FsrsWeightFitter().fit(syntheticSequences()) { true }
        assertFalse(cancelled.adopted)
        assertEquals(FsrsWeightFitter.REASON_CANCELLED, cancelled.reason)
        assertEquals(0, cancelled.epochsCompleted)
    }

    @Test(expected = IllegalArgumentException::class)
    fun fitterConfigurationRejectsInvalidEpochs() {
        FsrsWeightFitter.Config(maximumEpochs = 0)
    }

    private fun syntheticSequences(): List<FsrsReplaySequence> {
        val knownWeights = FsrsParameters.latestDefaultValues().also { it[20] = 0.7 }
        val engine = FsrsEngine.create(FsrsParameters.of(knownWeights))
        return (0 until 500).map { index ->
            val stability = 2.0 + index % 19
            val elapsed = 1 + (index * 7) % 60
            val probability = engine.retrievability(FsrsMemoryState(stability, 5.0), elapsed)
            val quantile = ((index * 73) % 997) / 997.0
            val recalled = quantile < probability
            FsrsReplaySequence(
                stability,
                5.0,
                listOf(
                    FsrsReplaySample(
                        elapsedDays = elapsed,
                        rating = if (recalled) 3 else 1,
                        outcome = recalled,
                        reviewedAtMillis = index.toLong(),
                    ),
                ),
            )
        }
    }
}
