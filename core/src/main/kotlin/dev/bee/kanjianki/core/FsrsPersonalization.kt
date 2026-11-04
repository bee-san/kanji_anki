package dev.bee.kanjianki.core

import dev.bee.fsrs.Fsrs7Parameters

/**
 * Stable storage contract shared by the Android settings and fitting layers.
 *
 * `scheduler_fsrs_weights` now holds 35 FSRS-7 values rather than 21 FSRS-6 ones.
 * There is no migration path between them: a fitted 21-vector describes a
 * different function, so reinterpreting or padding it would produce a parameter
 * set no optimizer ever validated. A stored FSRS-6 vector is therefore rejected
 * by [decodeWeights] like any other malformed value, and the reader falls open to
 * the FSRS-7 defaults.
 *
 * That is a real, if small, user-visible consequence: anyone who had opted into
 * personalization returns to default weights and the weekly fitter re-earns a
 * vector from the review history it already has. Discarding is the honest option
 * — the alternative is scheduling against numbers fitted for different equations.
 */
object FsrsPersonalization {
    const val WEIGHTS_SETTING_KEY: String = "scheduler_fsrs_weights"
    const val FIT_SUMMARY_SETTING_KEY: String = "scheduler_fsrs_fit_summary"
    const val ENABLED_SETTING_KEY: String = "scheduler_fsrs_personalization_enabled"
    const val ENABLED_SETTING_DEFAULT: Int = 1

    const val MINIMUM_TRAINING_SAMPLES: Int = 400
    const val MINIMUM_RELATIVE_IMPROVEMENT: Double = 0.01

    /**
     * How many values a stored vector holds: 35 under FSRS-7.
     *
     * Re-exposed here so callers outside `:core` can reason about the storage format
     * without importing `dev.bee.fsrs`. The module boundary deliberately keeps the
     * engine types inside `:core` — that is what makes the engine replaceable — and
     * this switch is the demonstration: `:app` needed no engine import to follow it.
     */
    @JvmField
    val PARAMETER_COUNT: Int = Fsrs7Parameters.PARAMETER_COUNT

    /** A fresh copy of the engine's default weights. Never the engine's own array. */
    @JvmStatic
    fun defaultWeights(): DoubleArray = Fsrs7Parameters.latestDefaultValues()

    /** Full-precision persistence; unlike SettingsRepository.putDouble, this never rounds. */
    @JvmStatic
    fun encodeWeights(weights: DoubleArray): String {
        val validated = Fsrs7Parameters.of(weights).toArray()
        return validated.joinToString(",") { java.lang.Double.toString(it) }
    }

    /**
     * Throws on malformed data so the Android reader can log once and fail open to
     * defaults. A pre-FSRS-7 21-value vector is malformed by this definition.
     */
    @JvmStatic
    fun decodeWeights(encoded: String?): DoubleArray? {
        if (encoded.isNullOrBlank()) {
            return null
        }
        val parts = encoded.split(',')
        val values = DoubleArray(parts.size) { index -> parts[index].trim().toDouble() }
        return Fsrs7Parameters.of(values).toArray()
    }
}
