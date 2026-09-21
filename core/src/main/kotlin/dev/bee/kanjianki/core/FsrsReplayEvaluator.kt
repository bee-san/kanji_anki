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

    /**
     * Same-day samples (elapsed 0) carry no forgetting-curve information:
     * retrievability is exactly 1 at zero elapsed time, so a pass scores ~0 and a
     * fail scores the clamp ceiling (~27 nats), which would dominate the fit.
     * Upstream FSRS also excludes same-day reviews from evaluation. They still
     * advance the replayed memory state.
     */
    fun isScored(): Boolean = elapsedDays > 0
}

/**
 * One card/task history. When [graduationRating] is known the replay seeds the
 * memory from the engine's own initial state for that grade, so the initial
 * stability/difficulty parameters (w0–w5) are fitted too. Otherwise the first
 * real review's exact pre-review memory is the seed. Learning/relearning
 * practice is intentionally absent from [samples].
 */
data class FsrsReplaySequence(
    val initialStability: Double,
    val initialDifficulty: Double,
    val samples: List<FsrsReplaySample>,
    val graduationRating: Int? = null,
) {
    init {
        FsrsMemoryState(initialStability, initialDifficulty)
        graduationRating?.let { FsrsRating.fromValue(it) }
    }

    fun scoredSampleCount(): Int = samples.count { it.isScored() }
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
            var state = initialState(engine, sequence)
            sequence.samples.forEachIndexed { sampleIndex, sample ->
                val scored = sample.isScored() &&
                    (includedSamples == null || sampleKey(sequenceIndex, sampleIndex) in includedSamples)
                if (scored) {
                    val retrievability = engine.retrievability(state, sample.elapsedDays)
                        .coerceIn(MIN_PROBABILITY, MAX_PROBABILITY)
                    total -= if (sample.outcome) ln(retrievability) else ln(1.0 - retrievability)
                    count++
                }
                state = engine.nextState(state, FsrsRating.fromValue(sample.rating), sample.elapsedDays)
            }
        }
        return Evaluation(count, total)
    }

    private fun initialState(engine: FsrsEngine, sequence: FsrsReplaySequence): FsrsMemoryState {
        val rating = sequence.graduationRating
            ?: return FsrsMemoryState(sequence.initialStability, sequence.initialDifficulty)
        return engine.initialState(FsrsRating.fromValue(rating))
    }

    internal fun sampleKey(sequenceIndex: Int, sampleIndex: Int): Long =
        (sequenceIndex.toLong() shl 32) or (sampleIndex.toLong() and 0xffff_ffffL)

    private const val MIN_PROBABILITY = 1e-12
    private const val MAX_PROBABILITY = 1.0 - MIN_PROBABILITY
}
