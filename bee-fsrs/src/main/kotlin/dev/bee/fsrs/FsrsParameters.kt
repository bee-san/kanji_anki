package dev.bee.fsrs

import java.util.Arrays

/**
 * Immutable FSRS parameter set.
 */
class FsrsParameters private constructor(
    private val values: DoubleArray,
) {
    fun get(index: Int): Double = values[index]

    fun initialStability(rating: FsrsRating): Double = get(rating.value() - 1)

    fun initialDifficultyBase(): Double = values[4]

    fun initialDifficultyExponent(): Double = values[5]

    fun difficultyDeltaScale(): Double = values[6]

    fun difficultyMeanReversionWeight(): Double = values[7]

    fun recallStabilityBase(): Double = values[8]

    fun recallStabilityStabilityDecay(): Double = values[9]

    fun recallStabilityRetrievabilitySensitivity(): Double = values[10]

    fun forgetStabilityBase(): Double = values[11]

    fun forgetStabilityDifficultyDecay(): Double = values[12]

    fun forgetStabilityStabilityGrowth(): Double = values[13]

    fun forgetStabilityRetrievabilitySensitivity(): Double = values[14]

    fun hardPenalty(): Double = values[15]

    fun easyBonus(): Double = values[16]

    fun shortTermBase(): Double = values[17]

    fun shortTermRatingOffset(): Double = values[18]

    fun shortTermStabilityDecay(): Double = values[19]

    fun toArray(): DoubleArray = values.clone()

    fun decayMagnitude(): Double = values[20]

    fun decay(): Double = -values[20]

    fun factor(): Double = factorFor(decayMagnitude())

    override fun toString(): String = "FsrsParameters" + Arrays.toString(values)

    companion object {
        const val PARAMETER_COUNT: Int = 21

        private val LATEST_DEFAULT_TEMPLATE = doubleArrayOf(
            0.212, 1.2931, 2.3065, 8.2956, 6.4133,
            0.8334, 3.0194, 0.001, 1.8722, 0.1666,
            0.796, 1.4835, 0.0614, 0.2629, 1.6483,
            0.6014, 1.8729, 0.5425, 0.0912, 0.0658,
            0.1542,
        )

        @JvmField
        val LATEST_DEFAULT_VALUES: DoubleArray = LATEST_DEFAULT_TEMPLATE.clone()

        @JvmStatic
        fun latestDefault(): FsrsParameters = of(LATEST_DEFAULT_TEMPLATE)

        @JvmStatic
        fun latestDefaultValues(): DoubleArray = LATEST_DEFAULT_TEMPLATE.clone()

        @JvmStatic
        fun of(values: DoubleArray?): FsrsParameters {
            requireNotNull(values) { "parameters must not be null" }
            require(values.size == PARAMETER_COUNT) { "FSRS requires exactly $PARAMETER_COUNT parameters" }
            val copy = values.clone()
            for (index in copy.indices) {
                require(copy[index].isFinite()) { "parameter $index must be finite" }
            }
            require(copy[20] > 0.0) { "decay magnitude parameter must be positive" }
            val factor = factorFor(copy[20])
            require(factor.isFinite() && factor > 0.0 && factor <= MAX_SAFE_FACTOR) {
                "decay magnitude parameter must produce a finite operational factor"
            }
            return FsrsParameters(copy)
        }

        private fun factorFor(decayMagnitude: Double): Double {
            return Math.pow(0.9, -1.0 / decayMagnitude) - 1.0
        }

        private val MAX_SAFE_FACTOR: Double =
            Double.MAX_VALUE / Int.MAX_VALUE.toDouble() * Fsrs.STABILITY_MIN / 2.0
    }
}
