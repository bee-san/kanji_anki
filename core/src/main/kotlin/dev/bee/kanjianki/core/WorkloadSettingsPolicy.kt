package dev.bee.kanjianki.core

import java.util.Locale

object WorkloadSettingsPolicy {
    const val MAXIMUM_SAVED_MESSAGE: String = "Max items saved."
    const val MANUAL_ENABLED_MESSAGE: String = "Manual study load ready."
    const val AUTOMATIC_ENABLED_MESSAGE: String = "Kani will pick today's study load."
    const val WORKLOAD_SAVED_MESSAGE: String = "Study load saved."

    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun saveMaximum(maxItems: Int): SaveRequest {
        return SaveRequest.create(
            null,
            null,
            AdaptiveLoadPlanner.normalizeMaxItems(maxItems),
            localizedText(MAXIMUM_SAVED_MESSAGE, "最大件数を保存しました。"),
        )
    }

    @JvmStatic
    fun enableManualMode(): SaveRequest {
        return SaveRequest.create(
            AdaptiveLoadPlanner.MODE_MANUAL,
            null,
            null,
            localizedText(MANUAL_ENABLED_MESSAGE, "手動の学習量に切り替えました。"),
        )
    }

    @JvmStatic
    fun enableAutomaticMode(): SaveRequest {
        return SaveRequest.create(
            AdaptiveLoadPlanner.MODE_AUTO,
            null,
            null,
            localizedText(AUTOMATIC_ENABLED_MESSAGE, "今日の学習量はKaniが選びます。"),
        )
    }

    @JvmStatic
    fun saveManualWorkload(workloadPercent: Int, maxItems: Int): SaveRequest {
        return SaveRequest.create(
            AdaptiveLoadPlanner.MODE_MANUAL,
            AdaptiveLoadPlanner.snapWorkloadPercent(workloadPercent),
            AdaptiveLoadPlanner.normalizeMaxItems(maxItems),
            localizedText(WORKLOAD_SAVED_MESSAGE, "学習量を保存しました。"),
        )
    }

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE

    class SaveRequest private constructor(
        @JvmField val mode: String?,
        @JvmField val workloadPercent: Int?,
        @JvmField val maxItems: Int?,
        @JvmField val message: String,
    ) {
        companion object {
            @JvmSynthetic
            fun create(
                mode: String?,
                workloadPercent: Int?,
                maxItems: Int?,
                message: String,
            ): SaveRequest {
                return SaveRequest(mode, workloadPercent, maxItems, message)
            }
        }
    }
}
