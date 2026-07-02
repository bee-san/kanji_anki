package dev.bee.kanjianki.core

import java.util.Locale

object RetentionSettingsPolicy {
    const val SAVED_MESSAGE: String = "Review retention saved."

    private const val JAPANESE_LANGUAGE = "ja"

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
        val parameters = safeLatest
            .withTargetRetention(SettingsInputRules.retentionPercent(retentionPercent / 100.0) / 100.0)
            .withFrequencyRetention(frequencyRetentionEnabled, ranges)
        return SaveResult.valid(parameters)
    }

    @JvmStatic
    fun savedMessage(): String = localizedText(SAVED_MESSAGE, "レビュー保持率を保存しました。")

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE

    class SaveResult private constructor(
        @JvmField val valid: Boolean,
        @JvmField val parameters: RecordsSchedulerModels.SchedulerParameters?,
        @JvmField val message: String?,
    ) {
        companion object {
            fun valid(parameters: RecordsSchedulerModels.SchedulerParameters): SaveResult {
                return SaveResult(true, parameters, RetentionSettingsPolicy.savedMessage())
            }

            fun invalid(message: String?): SaveResult {
                return SaveResult(false, null, message)
            }
        }
    }
}
