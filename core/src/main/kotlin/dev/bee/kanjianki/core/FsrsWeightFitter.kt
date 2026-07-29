package dev.bee.kanjianki.core

import dev.bee.fsrs.Fsrs7Parameters
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Deterministic, bounded finite-difference Adam fitter for FSRS-7 weights.
 * This deliberately lives in :core; :bee-fsrs remains the pristine vendored engine.
 */
class FsrsWeightFitter(
    private val config: Config = Config(),
) {
    data class Config(
        val maximumEpochs: Int = 40,
        val learningRate: Double = 0.03,
        val finiteDifferenceStep: Double = 1e-4,
        val earlyStoppingPatience: Int = 6,
        val seed: Long = 0L,
    ) {
        init {
            require(maximumEpochs > 0)
            require(learningRate > 0.0 && learningRate.isFinite())
            require(finiteDifferenceStep > 0.0 && finiteDifferenceStep.isFinite())
            require(earlyStoppingPatience > 0)
        }
    }

    data class Result(
        val weights: DoubleArray,
        val sampleCount: Int,
        val trainingSampleCount: Int,
        val validationSampleCount: Int,
        val defaultTrainingLoss: Double,
        val defaultValidationLoss: Double,
        val fittedTrainingLoss: Double,
        val fittedValidationLoss: Double,
        val adopted: Boolean,
        val reason: String,
        val epochsCompleted: Int,
    ) {
        fun relativeValidationImprovement(): Double =
            relativeImprovement(defaultValidationLoss, fittedValidationLoss)
    }

    @Suppress("kotlin:S3776")
    fun fit(
        sequences: List<FsrsReplaySequence>,
        shouldStop: () -> Boolean = { false },
    ): Result {
        // The fixed seed participates in stable tie ordering without introducing
        // stochastic optimization. Equal timestamps therefore remain repeatable.
        val split = split(sequences, config.seed)
        val defaults = Fsrs7Parameters.latestDefaultValues()
        val defaultTrain = safeEvaluate(defaults, sequences, split.trainingKeys)
        val defaultValidation = safeEvaluate(defaults, sequences, split.validationKeys)
        if (split.totalCount == 0 || split.validationKeys.isEmpty()) {
            return result(
                defaults, split, defaultTrain, defaultValidation,
                defaultTrain, defaultValidation, false, REASON_NOT_ENOUGH_HISTORY, 0,
            )
        }

        var current = clamp(defaults)
        var best = current.clone()
        var bestValidation = defaultValidation.logLoss
        var firstMoment = DoubleArray(PARAMETER_COUNT)
        var secondMoment = DoubleArray(PARAMETER_COUNT)
        var staleEpochs = 0
        var epochsCompleted = 0
        var cancelled = false

        for (epoch in 1..config.maximumEpochs) {
            if (shouldStop()) {
                cancelled = true
                break
            }
            val gradient = finiteDifferenceGradient(current, sequences, split.trainingKeys)
            for (index in 0 until PARAMETER_COUNT) {
                firstMoment[index] = BETA_ONE * firstMoment[index] + (1.0 - BETA_ONE) * gradient[index]
                secondMoment[index] = BETA_TWO * secondMoment[index] + (1.0 - BETA_TWO) * gradient[index] * gradient[index]
                val correctedFirst = firstMoment[index] / (1.0 - BETA_ONE.pow(epoch))
                val correctedSecond = secondMoment[index] / (1.0 - BETA_TWO.pow(epoch))
                current[index] -= config.learningRate * correctedFirst / (sqrt(correctedSecond) + ADAM_EPSILON)
            }
            current = clamp(current)
            epochsCompleted = epoch
            val validation = safeEvaluate(current, sequences, split.validationKeys).logLoss
            if (validation.isFinite() && validation + MIN_LOSS_DELTA < bestValidation) {
                bestValidation = validation
                best = current.clone()
                staleEpochs = 0
            } else {
                staleEpochs++
                if (staleEpochs >= config.earlyStoppingPatience) {
                    break
                }
            }
        }

        val fittedTrain = safeEvaluate(best, sequences, split.trainingKeys)
        val fittedValidation = safeEvaluate(best, sequences, split.validationKeys)
        val decision = if (cancelled) {
            AdoptionDecision(false, REASON_CANCELLED)
        } else {
            adoptionDecision(split.trainingKeys.size, defaultValidation.logLoss, fittedValidation.logLoss)
        }
        return result(
            best, split, defaultTrain, defaultValidation, fittedTrain, fittedValidation,
            decision.adopted, decision.reason, epochsCompleted,
        )
    }

    private fun finiteDifferenceGradient(
        weights: DoubleArray,
        sequences: List<FsrsReplaySequence>,
        trainingKeys: Set<Long>,
    ): DoubleArray {
        val gradient = DoubleArray(PARAMETER_COUNT)
        for (index in 0 until PARAMETER_COUNT) {
            val delta = config.finiteDifferenceStep * maxOf(1.0, abs(weights[index]))
            val lower = lowerBound(index)
            val upper = upperBound(index)
            val downValue = (weights[index] - delta).coerceIn(lower, upper)
            val upValue = (weights[index] + delta).coerceIn(lower, upper)
            if (upValue == downValue) {
                continue
            }
            val down = weights.clone().also { it[index] = downValue }
            val up = weights.clone().also { it[index] = upValue }
            val downLoss = safeEvaluate(down, sequences, trainingKeys).logLoss
            val upLoss = safeEvaluate(up, sequences, trainingKeys).logLoss
            gradient[index] = if (downLoss.isFinite() && upLoss.isFinite()) {
                (upLoss - downLoss) / (upValue - downValue)
            } else {
                0.0
            }
        }
        return gradient
    }

    private fun safeEvaluate(
        weights: DoubleArray,
        sequences: List<FsrsReplaySequence>,
        keys: Set<Long>,
    ): FsrsReplayEvaluator.Evaluation = try {
        FsrsReplayEvaluator.evaluateSelected(weights, sequences, keys)
    } catch (_: RuntimeException) {
        FsrsReplayEvaluator.Evaluation(keys.size, Double.POSITIVE_INFINITY)
    }

    @Suppress("kotlin:S107")
    private fun result(
        weights: DoubleArray,
        split: Split,
        defaultTrain: FsrsReplayEvaluator.Evaluation,
        defaultValidation: FsrsReplayEvaluator.Evaluation,
        fittedTrain: FsrsReplayEvaluator.Evaluation,
        fittedValidation: FsrsReplayEvaluator.Evaluation,
        adopted: Boolean,
        reason: String,
        epochs: Int,
    ): Result = Result(
        weights.clone(), split.totalCount, split.trainingKeys.size, split.validationKeys.size,
        defaultTrain.logLoss, defaultValidation.logLoss, fittedTrain.logLoss, fittedValidation.logLoss,
        adopted, reason, epochs,
    )

    private data class SampleReference(
        val key: Long,
        val reviewedAtMillis: Long,
        val stableOrder: Long,
    )

    private data class Split(
        val trainingKeys: Set<Long>,
        val validationKeys: Set<Long>,
        val totalCount: Int,
    )

    private fun split(sequences: List<FsrsReplaySequence>, seed: Long): Split {
        val references = ArrayList<SampleReference>()
        sequences.forEachIndexed { sequenceIndex, sequence ->
            sequence.samples.forEachIndexed { sampleIndex, sample ->
                val key = FsrsReplayEvaluator.sampleKey(sequenceIndex, sampleIndex)
                // Seeded xor is only a final tie-breaker; chronological ordering is
                // always primary and sequence order remains the next stable key.
                val seededTie = key xor seed
                references += SampleReference(key, sample.reviewedAtMillis, seededTie)
            }
        }
        references.sortWith(compareBy<SampleReference>({ it.reviewedAtMillis }, { it.stableOrder }))
        val total = references.size
        val trainingCount = when {
            total <= 1 -> total
            else -> (total * TRAINING_FRACTION).toInt().coerceIn(1, total - 1)
        }
        return Split(
            references.take(trainingCount).mapTo(linkedSetOf()) { it.key },
            references.drop(trainingCount).mapTo(linkedSetOf()) { it.key },
            total,
        )
    }

    /**
     * Project a candidate vector back into the region [Fsrs7Parameters.of] accepts.
     *
     * Two steps, because FSRS-7's clipper is not a box. The per-index bounds are
     * the box part; the ordering repair enforces the three constraints whose bound
     * is *another parameter* rather than a constant — the four initial stabilities
     * must be non-decreasing, and the second forgetting-curve decay and base must
     * each be at least the first. A pure box clamp would leave those free, and a
     * gradient step that crossed one would produce a vector the engine rejects,
     * turning a fit into a caught exception and a silent fallback to defaults.
     *
     * The repair is safe in one direction because each partner's upper bound
     * dominates the value being raised to: `w[0] <= 50 <= 100` bounds the initial
     * stabilities, `w[27] <= 0.25 < 0.95` the decays, and `w[29] <= 0.85 < 0.99`
     * the bases. So raising the later value can never push it past its own ceiling.
     */
    private fun clamp(values: DoubleArray): DoubleArray = projectIntoBounds(values)

    data class AdoptionDecision(val adopted: Boolean, val reason: String)

    companion object {
        const val REASON_ADOPTED: String = "adopted"
        const val REASON_NOT_ENOUGH_HISTORY: String = "not_enough_history"
        const val REASON_INSUFFICIENT_IMPROVEMENT: String = "insufficient_improvement"
        const val REASON_CANCELLED: String = "cancelled"
        const val REASON_DISABLED_DURING_FIT: String = "disabled_during_fit"
        const val REASON_FAILED: String = "failed"

        private const val PARAMETER_COUNT = Fsrs7Parameters.PARAMETER_COUNT
        private const val TRAINING_FRACTION = 0.8
        private const val BETA_ONE = 0.9
        private const val BETA_TWO = 0.999
        private const val ADAM_EPSILON = 1e-8
        private const val MIN_LOSS_DELTA = 1e-9

        internal const val FIRST_DECAY_INDEX = 27
        internal const val SECOND_DECAY_INDEX = 28
        internal const val FIRST_BASE_INDEX = 29
        internal const val SECOND_BASE_INDEX = 30

        // FSRS-7 bounds from upstream's FSRS7ParameterClipper, mirroring the
        // vendored engine's own table in Fsrs7Parameters. Duplicated here only
        // because the engine keeps its bounds private and :bee-fsrs is a pristine
        // vendored checkout that must not be edited to expose them. The duplication
        // is guarded rather than trusted: FsrsWeightFitterTest asserts that every
        // vector clamp() produces is accepted by Fsrs7Parameters.of, so bounds that
        // drifted apart from upstream would fail the build instead of degrading a
        // fit into a caught exception and a silent fallback to defaults.
        //
        // Where upstream's bound is another parameter rather than a constant, the
        // widest safe constant is used here and the relationship is enforced by
        // clamp()'s ordering repair: indices 1..3 take the stability floor, and 28
        // and 30 take the floors of 27 and 29 respectively.
        private val LOWER_BOUNDS = doubleArrayOf(
            0.0001, 0.0001, 0.0001, 0.0001,
            1.0, 0.001, 0.1,
            0.0, 0.0, 0.3, 0.01, 0.001, 0.1, 0.0, 0.0, 1.0,
            0.0, 0.0, 0.5, 0.001, 0.001, 0.001, 0.0, 0.0, 1.0,
            2.5, 0.0,
            0.01, 0.01, 0.5, 0.5, 0.01, 0.1, 0.0, 0.1,
        )
        private val UPPER_BOUNDS = doubleArrayOf(
            50.0, 100.0, 100.0, 100.0,
            10.0, 4.0, 4.0,
            4.0, 1.2, 3.0, 1.5, 0.9, 1.0, 3.5, 1.0, 7.0,
            4.0, 2.0, 6.0, 1.5, 2.0, 1.0, 5.0, 1.0, 7.0,
            15.0, 1.0,
            0.25, 0.95, 0.85, 0.99, 1.0, 1.0, 0.9, 1.1,
        )

        /**
         * Project a candidate vector back into the region [Fsrs7Parameters.of] accepts.
         *
         * Public because it is the fitter's contract, and because
         * `FsrsWeightFitterTest` asserts that its output is always accepted by the
         * engine — the guard on the bounds table below being a faithful copy of
         * upstream's clipper.
         */
        @JvmStatic
        fun projectIntoBounds(values: DoubleArray): DoubleArray {
            val clamped = DoubleArray(PARAMETER_COUNT) { index ->
                values[index].coerceIn(lowerBound(index), upperBound(index))
            }
            // The box clamp above cannot express FSRS-7's three relational
            // constraints, whose bound is another parameter rather than a constant:
            // the four initial stabilities must be non-decreasing, and the second
            // forgetting-curve decay and base must each be at least the first. A
            // gradient step that crossed one would produce a vector the engine
            // rejects, degrading a fit into a caught exception and a silent
            // fallback to defaults.
            //
            // Raising the later value is safe in one direction because each
            // partner's ceiling dominates: w[0] <= 50 <= 100 for the stabilities,
            // w[27] <= 0.25 < 0.95 for the decays, and w[29] <= 0.85 < 0.99 for the
            // bases. So the repair can never push a value past its own upper bound.
            for (index in 1..3) {
                clamped[index] = maxOf(clamped[index], clamped[index - 1])
            }
            clamped[SECOND_DECAY_INDEX] = maxOf(clamped[SECOND_DECAY_INDEX], clamped[FIRST_DECAY_INDEX])
            clamped[SECOND_BASE_INDEX] = maxOf(clamped[SECOND_BASE_INDEX], clamped[FIRST_BASE_INDEX])
            return clamped
        }

        @JvmStatic
        fun lowerBound(index: Int): Double = LOWER_BOUNDS[index]

        @JvmStatic
        fun upperBound(index: Int): Double = UPPER_BOUNDS[index]

        @JvmStatic
        fun adoptionDecision(
            trainingSampleCount: Int,
            defaultValidationLoss: Double,
            fittedValidationLoss: Double,
        ): AdoptionDecision {
            if (trainingSampleCount < FsrsPersonalization.MINIMUM_TRAINING_SAMPLES) {
                return AdoptionDecision(false, REASON_NOT_ENOUGH_HISTORY)
            }
            val improvement = relativeImprovement(defaultValidationLoss, fittedValidationLoss)
            return if (improvement.isFinite() && improvement >= FsrsPersonalization.MINIMUM_RELATIVE_IMPROVEMENT) {
                AdoptionDecision(true, REASON_ADOPTED)
            } else {
                AdoptionDecision(false, REASON_INSUFFICIENT_IMPROVEMENT)
            }
        }
    }
}

private fun relativeImprovement(baseline: Double, candidate: Double): Double {
    if (!baseline.isFinite() || !candidate.isFinite() || baseline <= 0.0) {
        return Double.NaN
    }
    return (baseline - candidate) / baseline
}
