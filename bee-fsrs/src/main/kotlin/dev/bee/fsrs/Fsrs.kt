package dev.bee.fsrs

import kotlin.math.max
import kotlin.math.min

/**
 * Shared constants and small validation helpers for FSRS calculations.
 */
object Fsrs {
    const val STABILITY_MIN: Double = 0.001
    const val MIN_DIFFICULTY: Double = 1.0
    const val MAX_DIFFICULTY: Double = 10.0

    @JvmStatic
    fun <T> requireNonNull(value: T?, name: String): T {
        return requireNotNull(value) { "$name must not be null" }
    }

    @JvmStatic
    fun validateElapsedDays(elapsedDays: Int) {
        require(elapsedDays >= 0) { "elapsedDays must be non-negative" }
    }

    @JvmStatic
    fun validateDesiredRetention(desiredRetention: Double) {
        require(desiredRetention.isFinite() && desiredRetention > 0.0 && desiredRetention < 1.0) {
            "desiredRetention must be finite and in (0, 1)"
        }
    }

    @JvmStatic
    fun validateMaximumInterval(maximumInterval: Int) {
        require(maximumInterval >= 1) { "maximumInterval must be at least 1" }
    }

    @JvmStatic
    fun clamp(value: Double, min: Double, max: Double): Double =
        max(min, min(max, value))
}
