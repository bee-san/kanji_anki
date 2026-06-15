package dev.bee.kanjianki.core

import java.util.Locale

object AdaptiveFocusCopy {
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun adaptiveFocusText(plan: RecordsSchedulerModels.AdaptiveLoadPlan?): String {
        if (plan == null || plan.target <= 0) {
            return localizedText(
                "Adaptive focus is waiting for sync",
                "自動フォーカスは同期待ちです",
            )
        }
        if (plan.allKanjiMode) {
            return localizedText(
                "Adaptive focus covers all current problem kanji",
                "自動フォーカスは現在の苦手漢字をすべて含みます",
            )
        }
        return localizedText(
            "Today's adaptive focus: ${plan.remaining} of ${plan.target} left",
            "今日の自動フォーカス：${plan.target}件中、残り${plan.remaining}件",
        )
    }

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
