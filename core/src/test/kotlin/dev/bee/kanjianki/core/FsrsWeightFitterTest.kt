package dev.bee.kanjianki.core

import dev.bee.fsrs.Fsrs7Engine
import dev.bee.fsrs.Fsrs7Parameters
import dev.bee.fsrs.FsrsMemoryState
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

    /**
     * The fitter's bounds table is a hand-copy of upstream's FSRS-7 clipper, kept
     * here because `:bee-fsrs` is a pristine vendored checkout that keeps its own
     * copy private. This test is what stops the duplication from rotting: it drives
     * every bound to its extreme, and past it, and asserts the engine accepts the
     * result. If a bound here ever contradicts the engine's, this fails at build
     * time rather than degrading a real fit into a caught exception and a silent
     * fallback to default weights.
     */
    @Test
    fun everyProjectedVectorIsAcceptedByTheEngine() {
        val count = Fsrs7Parameters.PARAMETER_COUNT
        val candidates = listOf(
            DoubleArray(count) { FsrsWeightFitter.lowerBound(it) },
            DoubleArray(count) { FsrsWeightFitter.upperBound(it) },
            DoubleArray(count) { Double.NEGATIVE_INFINITY },
            DoubleArray(count) { Double.POSITIVE_INFINITY },
            // Alternating extremes specifically break the three ordering
            // constraints a per-index box clamp cannot express.
            DoubleArray(count) { if (it % 2 == 0) FsrsWeightFitter.upperBound(it) else FsrsWeightFitter.lowerBound(it) },
            DoubleArray(count) { if (it % 2 == 0) FsrsWeightFitter.lowerBound(it) else FsrsWeightFitter.upperBound(it) },
            Fsrs7Parameters.latestDefaultValues(),
        )

        candidates.forEach { candidate ->
            val projected = FsrsWeightFitter.projectIntoBounds(candidate)
            // Throws IllegalArgumentException if any bound or ordering rule is violated.
            Fsrs7Parameters.of(projected)
            projected.indices.forEach { index ->
                assertTrue(
                    "parameter $index below its lower bound",
                    projected[index] >= FsrsWeightFitter.lowerBound(index),
                )
                assertTrue(
                    "parameter $index above its upper bound",
                    projected[index] <= FsrsWeightFitter.upperBound(index),
                )
            }
        }
    }

    @Test
    fun theFitterCoversAllThirtyFiveFsrs7Parameters() {
        assertEquals(35, Fsrs7Parameters.PARAMETER_COUNT)
        assertEquals(
            Fsrs7Parameters.PARAMETER_COUNT,
            FsrsWeightFitter.projectIntoBounds(Fsrs7Parameters.latestDefaultValues()).size,
        )
        // Probes the last index specifically: a bounds table that was still 21 long
        // would throw here rather than quietly fitting a truncated vector.
        val lastIndex = Fsrs7Parameters.PARAMETER_COUNT - 1
        assertTrue(FsrsWeightFitter.lowerBound(lastIndex) <= FsrsWeightFitter.upperBound(lastIndex))
    }

    private fun syntheticSequences(): List<FsrsReplaySequence> {
        // w[29] and w[30] are the two forgetting-curve bases — "retention after one
        // stability period" for each power law. They are the FSRS-7 analogue of the
        // single decay magnitude (w[20]) this test perturbed under FSRS-6, and they
        // are what recall outcomes most directly identify, so a fitter that works
        // recovers them and one that is silently broken cannot.
        //
        // Both are moved, and upward, because w[30] must stay at least w[29]: raising
        // only w[29] to 0.85 would exceed w[30]'s default 0.9555 and be rejected
        // outright. Noted because the obvious "perturb one parameter harder" edit
        // walks straight into that constraint.
        val knownWeights = Fsrs7Parameters.latestDefaultValues().also {
            it[29] = 0.85
            it[30] = 0.99
        }
        val engine = Fsrs7Engine.create(Fsrs7Parameters.of(knownWeights))
        return (0 until 500).map { index ->
            val stability = 2.0 + index % 19
            val elapsed = 1.0 + (index * 7) % 60
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
