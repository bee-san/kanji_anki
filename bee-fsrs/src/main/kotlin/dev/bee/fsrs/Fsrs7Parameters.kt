package dev.bee.fsrs

import java.util.Arrays

/**
 * Immutable FSRS-7 parameter set: 35 values.
 *
 * The accessors below are named after what each parameter does rather than its
 * index, because FSRS-7's long-term and short-term stability branches use the
 * *same nine equations* at offsets 7 and 16. Reading `w[19]` at a call site gives
 * no clue whether the short-term failure multiplier was meant; reading
 * `shortTerm().failureMultiplier()` does, and an off-by-one lands on a compile
 * error instead of a plausible wrong number.
 *
 * Bounds come from upstream's `FSRS7ParameterClipper`. They are asserted here
 * rather than in the optimizer because this package has no optimizer: a caller
 * supplying a learner's fitted vector is the only way a non-default set arrives,
 * and a set outside the trained region would extrapolate the mathematics into
 * shapes the benchmark never evaluated.
 */
class Fsrs7Parameters private constructor(
    private val values: DoubleArray,
) {
    fun get(index: Int): Double = values[index]

    fun toArray(): DoubleArray = values.clone()

    /** Initial stability for a first review with [rating], from `w[0..3]`. */
    fun initialStability(rating: FsrsRating): Double = values[rating.value() - 1]

    fun initialDifficultyBase(): Double = values[4]

    fun initialDifficultyExponent(): Double = values[5]

    fun difficultyDeltaScale(): Double = values[6]

    /** The nine long-term stability parameters, `w[7..15]`. */
    fun longTerm(): StabilityBranch = StabilityBranch(values, LONG_TERM_BASE_INDEX)

    /** The nine short-term stability parameters, `w[16..24]`. */
    fun shortTerm(): StabilityBranch = StabilityBranch(values, SHORT_TERM_BASE_INDEX)

    /**
     * `w[25]`, the rate at which a review stops counting as same-day.
     *
     * Paired with [transitionWeight] in `1 - w[26] * exp(-w[25] * t)`, which is
     * FSRS-7's replacement for FSRS-6's hard `elapsed < 1 day` branch. The blend
     * is continuous, so a review at 23 hours and one at 25 hours no longer land on
     * different equations.
     */
    fun transitionRate(): Double = values[25]

    /** `w[26]`, how much of the short-term branch applies at zero elapsed time. */
    fun transitionWeight(): Double = values[26]

    /** The eight forgetting-curve parameters, `w[27..34]`. */
    fun forgettingCurve(): ForgettingCurve = ForgettingCurve(values)

    override fun toString(): String = "Fsrs7Parameters" + Arrays.toString(values)

    /**
     * One of FSRS-7's two stability branches.
     *
     * A view over the backing array rather than a copy: the long-term and
     * short-term branches are the same nine equations at two offsets, so
     * expressing that as one type used twice means the equations are written once
     * and cannot drift apart between the branches.
     */
    class StabilityBranch internal constructor(
        private val values: DoubleArray,
        private val base: Int,
    ) {
        /**
         * Log-scale multiplier on the stability increase, `exp(w[base] - 1.5)`.
         *
         * The `- 1.5` is upstream's, inside the exponent, and is what lets the
         * clipper bound this parameter to `[0, 4]` while the multiplier it
         * produces still spans a useful range. Applying the offset outside the
         * exponent would be a different function entirely.
         */
        fun increaseBase(): Double = values[base]

        fun increaseStabilityExponent(): Double = values[base + 1]

        fun increaseRetrievabilityMultiplier(): Double = values[base + 2]

        fun failureMultiplier(): Double = values[base + 3]

        fun failureDifficultyExponent(): Double = values[base + 4]

        fun failureStabilityExponent(): Double = values[base + 5]

        fun failureRetrievabilityMultiplier(): Double = values[base + 6]

        fun hardPenalty(): Double = values[base + 7]

        fun easyBonus(): Double = values[base + 8]
    }

    /**
     * FSRS-7's eight-parameter forgetting curve.
     *
     * Two power laws, blended by weights that are themselves functions of
     * stability, which is the substantive change from FSRS-6's single power law.
     * Because the first weight falls with stability (`s^-swp1`) and the second
     * rises (`s^swp2`), the curve's shape depends on how well-known an item is —
     * so a fixture that tested one stability scale could not detect a swapped
     * pair.
     */
    class ForgettingCurve internal constructor(private val values: DoubleArray) {
        /** `w[27]`. Negated before use, as upstream's callers do. */
        fun firstDecay(): Double = -values[27]

        /** `w[28]`. Negated before use. */
        fun secondDecay(): Double = -values[28]

        fun firstBase(): Double = values[29]

        fun secondBase(): Double = values[30]

        fun firstWeight(): Double = values[31]

        fun secondWeight(): Double = values[32]

        /** `w[33]`, applied as a negative power of stability. */
        fun firstStabilityWeightPower(): Double = values[33]

        /** `w[34]`, applied as a positive power of stability. */
        fun secondStabilityWeightPower(): Double = values[34]
    }

    companion object {
        const val PARAMETER_COUNT: Int = 35

        private const val LONG_TERM_BASE_INDEX: Int = 7
        private const val SHORT_TERM_BASE_INDEX: Int = 16

        /**
         * Upstream's default vector, obtained by multi-user optimization.
         *
         * Byte-exact to `FSRS7.init_w` in `models/fsrs_v7.py`. Grouped as upstream
         * comments them, because the grouping is the only thing that makes a
         * 35-long literal reviewable.
         */
        private val DEFAULT_TEMPLATE = doubleArrayOf(
            // Initial stability, w[0..3]
            0.041, 2.4175, 4.1283, 11.9709,
            // Difficulty, w[4..6]
            5.6385, 0.4468, 3.262,
            // Stability, long-term, w[7..15]
            2.3054, 0.1688, 1.3325, 0.3524, 0.0049, 0.7503, 0.0896, 0.6625, 1.3,
            // Stability, short-term, w[16..24]
            0.882, 0.3072, 3.5875, 0.303, 0.0107, 0.2279, 2.6413, 0.5594, 1.3,
            // Long-short term transition, w[25..26]
            2.5, 1.0,
            // Forgetting curve, w[27..34]
            0.0723, 0.1634, 0.5, 0.9555, 0.2245, 0.6232, 0.1362, 0.3862,
        )

        /**
         * Inclusive bounds per index, from upstream's `FSRS7ParameterClipper`.
         *
         * `NaN` marks a bound that is not a constant but another parameter — the
         * initial stabilities must be non-decreasing, and the second forgetting-curve
         * decay and base must each exceed the first. Those are checked separately
         * in [of], because they are relationships rather than ranges.
         */
        private val LOWER_BOUNDS = doubleArrayOf(
            Fsrs7.STABILITY_MIN, Double.NaN, Double.NaN, Double.NaN,
            1.0, 0.001, 0.1,
            0.0, 0.0, 0.3, 0.01, 0.001, 0.1, 0.0, 0.0, 1.0,
            0.0, 0.0, 0.5, 0.001, 0.001, 0.001, 0.0, 0.0, 1.0,
            2.5, 0.0,
            0.01, Double.NaN, 0.5, Double.NaN, 0.01, 0.1, 0.0, 0.1,
        )

        private const val INITIAL_STABILITY_MAX = 100.0

        private val UPPER_BOUNDS = doubleArrayOf(
            INITIAL_STABILITY_MAX / 2.0, INITIAL_STABILITY_MAX, INITIAL_STABILITY_MAX, INITIAL_STABILITY_MAX,
            10.0, 4.0, 4.0,
            4.0, 1.2, 3.0, 1.5, 0.9, 1.0, 3.5, 1.0, 7.0,
            4.0, 2.0, 6.0, 1.5, 2.0, 1.0, 5.0, 1.0, 7.0,
            15.0, 1.0,
            0.25, 0.95, 0.85, 0.99, 1.0, 1.0, 0.9, 1.1,
        )

        @JvmStatic
        fun latestDefault(): Fsrs7Parameters = of(DEFAULT_TEMPLATE)

        @JvmStatic
        fun latestDefaultValues(): DoubleArray = DEFAULT_TEMPLATE.clone()

        @JvmStatic
        fun of(values: DoubleArray?): Fsrs7Parameters {
            requireNotNull(values) { "parameters must not be null" }
            require(values.size == PARAMETER_COUNT) {
                "FSRS-7 requires exactly $PARAMETER_COUNT parameters, got ${values.size}"
            }
            val copy = values.clone()
            for (index in copy.indices) {
                require(copy[index].isFinite()) { "parameter $index must be finite" }
                val lower = LOWER_BOUNDS[index]
                if (!lower.isNaN()) {
                    require(copy[index] >= lower) {
                        "parameter $index must be at least $lower, was ${copy[index]}"
                    }
                }
                val upper = UPPER_BOUNDS[index]
                if (!upper.isNaN()) {
                    require(copy[index] <= upper) {
                        "parameter $index must be at most $upper, was ${copy[index]}"
                    }
                }
            }

            // Initial stabilities are ordered: a first review rated Easy cannot be
            // less durable than one rated Again. Upstream enforces this by clamping
            // each against the previous, and it also keeps the four values
            // interpretable as a rating scale.
            for (index in 1..3) {
                require(copy[index] >= copy[index - 1]) {
                    "initial stability $index must be at least parameter ${index - 1}"
                }
            }

            // The two power laws are ordered so the pair stays identifiable: without
            // this, the same curve has two parameterisations with the roles swapped,
            // and a fitted vector could not be compared against another.
            require(copy[28] >= copy[27]) { "parameter 28 (second decay) must be at least parameter 27" }
            require(copy[30] >= copy[29]) { "parameter 30 (second base) must be at least parameter 29" }

            return Fsrs7Parameters(copy)
        }
    }
}
