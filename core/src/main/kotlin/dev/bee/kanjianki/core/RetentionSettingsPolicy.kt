package dev.bee.kanjianki.core

object RetentionSettingsPolicy {
    const val SAVED_MESSAGE: String = "Review retention saved."

    @JvmStatic
    fun saveRequest(
        retentionPercent: Int,
        frequencyRetentionEnabled: Boolean,
        frequencyRetentionRanges: String?,
        latest: RecordsSchedulerModels.SchedulerParameters?,
    ): SaveResult {
        val ranges = frequencyRetentionRanges?.trim().orEmpty()
        if (frequencyRetentionEnabled) {
            try {
                FrequencyRetentionRanges.parse(ranges)
            } catch (error: IllegalArgumentException) {
                return SaveResult.invalid(error.message)
            }
        }
        val safeLatest = latest ?: RecordsSchedulerModels.SchedulerParameters.defaults()
        val parameters = RecordsSchedulerModels.SchedulerParameters(
            SettingsInputRules.retentionPercent(retentionPercent / 100.0) / 100.0,
            safeLatest.againMultiplier,
            safeLatest.hardMultiplier,
            safeLatest.goodMultiplier,
            safeLatest.easyMultiplier,
            safeLatest.lastAdjustedAtMillis,
            safeLatest.lastAdjustmentReviewCount,
        ).withFrequencyRetention(frequencyRetentionEnabled, ranges)
        return SaveResult.valid(parameters)
    }

    class SaveResult private constructor(
        @JvmField val valid: Boolean,
        @JvmField val parameters: RecordsSchedulerModels.SchedulerParameters?,
        @JvmField val message: String?,
    ) {
        companion object {
            fun valid(parameters: RecordsSchedulerModels.SchedulerParameters): SaveResult {
                return SaveResult(true, parameters, SAVED_MESSAGE)
            }

            fun invalid(message: String?): SaveResult {
                return SaveResult(false, null, message)
            }
        }
    }
}
