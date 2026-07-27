package dev.bee.kanjianki.core

import dev.bee.fsrs.FsrsParameters

/** Stable storage contract shared by the Android settings and fitting layers. */
object FsrsPersonalization {
    const val WEIGHTS_SETTING_KEY: String = "scheduler_fsrs_weights"
    const val FIT_SUMMARY_SETTING_KEY: String = "scheduler_fsrs_fit_summary"
    const val ENABLED_SETTING_KEY: String = "scheduler_fsrs_personalization_enabled"
    const val ENABLED_SETTING_DEFAULT: Int = 1

    const val MINIMUM_TRAINING_SAMPLES: Int = 400
    const val MINIMUM_RELATIVE_IMPROVEMENT: Double = 0.01

    /** Full-precision persistence; unlike SettingsRepository.putDouble, this never rounds. */
    @JvmStatic
    fun encodeWeights(weights: DoubleArray): String {
        val validated = FsrsParameters.of(weights).toArray()
        return validated.joinToString(",") { java.lang.Double.toString(it) }
    }

    /** Throws on malformed data so the Android reader can log once and fail open to defaults. */
    @JvmStatic
    fun decodeWeights(encoded: String?): DoubleArray? {
        if (encoded.isNullOrBlank()) {
            return null
        }
        val parts = encoded.split(',')
        val values = DoubleArray(parts.size) { index -> parts[index].trim().toDouble() }
        return FsrsParameters.of(values).toArray()
    }
}
