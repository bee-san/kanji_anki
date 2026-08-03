package dev.bee.kanjianki.core

import java.util.Locale

/**
 * Study-behaviour control labels and the new-card-sort mode names, in shared `:core`.
 *
 * The Android host spreads these across its settings panels; this is the locale-aware
 * text the shared Study-behaviour section needs so neither host owns the wording. The
 * bounds themselves are the settings model's (`RecordsSyncModels.Settings` clamps
 * them); this is only what the controls are called.
 */
object SettingsStudyBehaviorTextCopy {
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun promotionIntervalLabel(): String = localizedText("Promotion interval", "昇格の間隔")

    @JvmStatic
    fun demotionFailStreakLabel(): String = localizedText("Demotion after fails", "降格までの連続失敗")

    @JvmStatic
    fun studyAheadLabel(): String = localizedText("Study ahead", "前倒し学習")

    @JvmStatic
    fun daysUnit(): String = localizedText("days", "日")

    @JvmStatic
    fun failsUnit(): String = localizedText("fails", "回")

    @JvmStatic
    fun minutesUnit(): String = localizedText("min", "分")

    @JvmStatic
    fun newPerDayLabel(): String = localizedText("New cards per day", "1日の新規カード")

    @JvmStatic
    fun activeQueueCapLabel(): String = localizedText("Active queue cap", "アクティブ上限")

    @JvmStatic
    fun cardsUnit(): String = localizedText("cards", "枚")

    @JvmStatic
    fun newCardSortLabel(): String = localizedText("New card order", "新規カードの順序")

    @JvmStatic
    fun newCardSortModeLabel(mode: String): String = when (mode) {
        RecordsBase.NEW_CARD_SORT_FREQUENCY -> localizedText("Frequency", "頻度")
        RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY -> localizedText("Difficulty", "難易度")
        RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK -> localizedText("Forgetting risk", "忘却リスク")
        RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS -> localizedText("Weakness", "弱点")
        else -> localizedText("Balanced", "バランス")
    }

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
