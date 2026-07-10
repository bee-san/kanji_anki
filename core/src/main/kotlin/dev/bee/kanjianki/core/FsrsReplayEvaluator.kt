package dev.bee.kanjianki.core

import dev.bee.fsrs.FsrsEngine
import dev.bee.fsrs.FsrsMemoryState
import dev.bee.fsrs.FsrsParameters
import dev.bee.fsrs.FsrsRating
import kotlin.math.ln

/** One persisted, real-due review used by the personalized FSRS replay. */
data class FsrsReplaySample(
    val elapsedDays: Int,
    val rating: Int,
    val outcome: Boolean = rating != FsrsRating.AGAIN.value(),
    val reviewedAtMillis: Long = 0L,
) {
    init {
        require(elapsedDays >= 0) { "elapsedDays must not be negative" }
        FsrsRating.fromValue(rating)
    }
}

/**
 * One card/task history. The first real review's exact pre-review memory is
 * the replay seed; learning/relearning practice is intentionally absent.
 */
data class FsrsReplaySequence(
    val initialStability: Double,
    val initialDifficulty: Double,
    val samples: List<FsrsReplaySample>,
) {
    init {
        FsrsMemoryState(initialStability, initialDifficulty)
    }
}

/** Pure FSRS replay and binary recall log-loss evaluator. */
object FsrsReplayEvaluator {
    data class Evaluation(
        val sampleCount: Int,
        val totalLogLoss: Double,
    ) {
        val logLoss: Double
            get() = if (sampleCount == 0) Double.NaN else totalLogLoss / sampleCount
    }

    @JvmStatic
    fun evaluate(weights: DoubleArray, sequences: List<FsrsReplaySequence>): Evaluation =
        evaluateSelected(weights, sequences, null)

    internal fun evaluateSelected(
        weights: DoubleArray,
        sequences: List<FsrsReplaySequence>,
        includedSamples: Set<Long>?,
    ): Evaluation {
        val engine = FsrsEngine.create(FsrsParameters.of(weights))
        var total = 0.0
        var count = 0
        sequences.forEachIndexed { sequenceIndex, sequence ->
            var state = FsrsMemoryState(sequence.initialStability, sequence.initialDifficulty)
            sequence.samples.forEachIndexed { sampleIndex, sample ->
                val retrievability = engine.retrievability(state, sample.elapsedDays)
                    .coerceIn(MIN_PROBABILITY, MAX_PROBABILITY)
                if (includedSamples == null || sampleKey(sequenceIndex, sampleIndex) in includedSamples) {
                    total -= if (sample.outcome) ln(retrievability) else ln(1.0 - retrievability)
                    count++
                }
                state = engine.nextState(state, FsrsRating.fromValue(sample.rating), sample.elapsedDays)
            }
        }
        return Evaluation(count, total)
    }

    internal fun sampleKey(sequenceIndex: Int, sampleIndex: Int): Long =
        (sequenceIndex.toLong() shl 32) or (sampleIndex.toLong() and 0xffff_ffffL)

    private const val MIN_PROBABILITY = 1e-12
    private const val MAX_PROBABILITY = 1.0 - MIN_PROBABILITY
}
