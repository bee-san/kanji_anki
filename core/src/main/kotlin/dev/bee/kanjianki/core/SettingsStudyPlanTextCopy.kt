package dev.bee.kanjianki.core

import java.util.Locale

object SettingsStudyPlanTextCopy {
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun deckLimitsTitle(): String = localizedText("Daily limits", "1日の上限")

    @JvmStatic
    fun deckLimitsBody(): String = localizedText("Set the daily new-card cap.", "1日の新規カード上限を設定する。")

    @JvmStatic
    fun newCardsPerDayLabel(): String = localizedText("New cards per day", "1日の新規カード数")

    @JvmStatic
    fun saveDeckLimitsLabel(): String = localizedText("Save daily limit", "1日の上限を保存")

    @JvmStatic
    fun dailyWorkloadTitle(): String = localizedText("Daily workload", "1日の学習量")

    @JvmStatic
    fun automaticWorkloadBody(): String {
        return localizedText("Kani picks today's count; due dates stay fixed.", "Kaniが今日の数を選ぶ。期限日は固定のまま。")
    }

    @JvmStatic
    fun saveMaximumLabel(): String = localizedText("Save workload", "学習量を保存")

    @JvmStatic
    fun manualWorkloadLabel(): String = localizedText("Set workload manually", "学習量を手動で設定")

    @JvmStatic
    fun manualWorkloadBody(): String {
        return localizedText("Set today's count; due dates stay fixed.", "今日の数を設定する。期限日は固定のまま。")
    }

    @JvmStatic
    fun workloadScaleLabels(): Array<String> {
        return if (isJapaneseLocale()) {
            arrayOf("ごく少なめ", "集中", "バランス", "多め", "すべての漢字")
        } else {
            arrayOf("Very little", "Focused", "Balanced", "More", "All kanji")
        }
    }

    @JvmStatic
    fun saveWorkloadLabel(): String = localizedText("Save workload", "学習量を保存")

    @JvmStatic
    fun automaticParetoLabel(): String = localizedText("Use automatic workload", "自動学習量を使う")

    @JvmStatic
    fun workloadStatusText(percent: Int, maxItems: Int): String {
        val snapped = AdaptiveLoadPlanner.snapWorkloadPercent(percent)
        val normalizedMax = AdaptiveLoadPlanner.normalizeMaxItems(maxItems)
        val label = workloadStatusLabel(snapped)
        if (snapped >= 100) {
            return "$label: " + localizedText("up to ", "最大") + itemCountText(normalizedMax)
        }
        return "$label: " + localizedText("up to ", "最大") + itemCountText(
            minOf(AdaptiveLoadPlanner.targetCeiling(snapped), normalizedMax),
        )
    }

    @JvmStatic
    fun maxItemsStatusText(maxItems: Int): String {
        return localizedText("Maximum: ", "最大: ") + itemCountText(AdaptiveLoadPlanner.normalizeMaxItems(maxItems))
    }

    @JvmStatic
    fun autoWorkloadStatusText(plan: RecordsSchedulerModels.AdaptiveLoadPlan?): String {
        if (plan == null || plan.target <= 0) {
            return localizedText("Waiting for cards", "カード待ち")
        }
        return if (isJapaneseLocale()) {
            "今日は" + itemCountText(plan.target)
        } else {
            StudyTextCopy.countText(plan.target, "item", "items") + " today"
        }
    }

    private fun workloadStatusLabel(snappedWorkloadPercent: Int): String {
        if (snappedWorkloadPercent <= 0) {
            return localizedText("Very little", "ごく少なめ")
        }
        if (snappedWorkloadPercent <= 20) {
            return localizedText("Focused", "集中")
        }
        if (snappedWorkloadPercent <= 50) {
            return localizedText("Balanced", "バランス")
        }
        if (snappedWorkloadPercent < 100) {
            return localizedText("More", "多め")
        }
        return localizedText("All kanji", "すべての漢字")
    }

    @JvmStatic
    fun newCardSortStatusText(mode: String?): String {
        return localizedText("Current: ", "現在: ") + newCardSortLabel(mode)
    }

    @JvmStatic
    fun newCardSortLabel(mode: String?): String {
        return when (RecordsSyncModels.Settings.normalizeNewCardSortMode(mode)) {
            RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY -> localizedText("Hardest first", "難しい順")
            RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK -> localizedText("Forgetting risk", "忘れやすさ")
            RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS -> localizedText("Kani misses", "Kaniのミス")
            RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY -> localizedText("Balanced mix", "バランス")
            else -> localizedText("Frequency", "頻度")
        }
    }

    @JvmStatic
    fun newCardSortDescription(mode: String?): String {
        return when (RecordsSyncModels.Settings.normalizeNewCardSortMode(mode)) {
            RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY -> localizedText("Harder cards first.", "難しいカードから。")
            RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK -> localizedText("Likely forgotten first.", "忘れそうなカードから。")
            RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS -> localizedText("Missed in Kani first.", "Kaniで間違えたカードから。")
            RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY -> localizedText(
                "Balances misses, risk, and frequency.",
                "ミス、リスク、頻度のバランス。",
            )
            else -> localizedText("Jiten frequency first.", "Jiten頻度順。")
        }
    }

    @JvmStatic
    fun frequencyRangeStatusText(minRank: Int, maxRank: Int): String {
        return String.format(
            Locale.ROOT,
            localizedText("Jiten ranks %d-%d", "Jiten順位 %d-%d"),
            minRank,
            maxRank,
        )
    }

    @JvmStatic
    fun retentionStatusText(retentionPercent: Int): String {
        return localizedText("Desired retention: ", "目標保持率: ") + "$retentionPercent%"
    }

    @JvmStatic
    fun newCardSortTitle(): String = localizedText("New card sort", "新規カードの並び順")

    @JvmStatic
    fun newCardSortBody(): String {
        return localizedText("New cards only. Reviews and repeats stay first.", "新規カードのみ。レビューと繰り返しは先のまま。")
    }

    @JvmStatic
    fun saveNewCardSortLabel(): String = localizedText("Save new card sort", "新規カードの並び順を保存")

    @JvmStatic
    fun newCardSortConfusablePreviewWarning(examples: List<String>): String {
        val suffix = if (examples.isEmpty()) "" else ": " + examples.joinToString(", ")
        return localizedText("Similar kanji stay close$suffix.", "似た漢字を近くに並べます$suffix。")
    }

    @JvmStatic
    fun fsrsRetentionTitle(): String = localizedText("Review retention", "レビュー保持率")

    @JvmStatic
    fun fsrsRetentionBody(): String {
        return localizedText("FSRS stays local. Anki due dates stay fixed.", "FSRSは端末内のみ。Ankiの期限日は固定のまま。")
    }

    @JvmStatic
    fun useJitenRankRetentionRangesLabel(): String = localizedText("Jiten-rank retention ranges", "Jiten順位ごとの保持率範囲")

    @JvmStatic
    fun jitenRankRetentionRangesBody(): String {
        return localizedText("One range per line, e.g. 1-500=95%.", "1行に1範囲（例: 1-500=95%）。")
    }

    @JvmStatic
    fun useExampleRangesLabel(): String = localizedText("Use example ranges", "例の範囲を使う")

    @JvmStatic
    fun saveRetentionLabel(): String = localizedText("Save retention", "保持率を保存")

    @JvmStatic
    fun retentionPresetLabel(value: Int): String {
        return "$value%"
    }

    @JvmStatic
    fun studyLadderTitle(): String = localizedText("Study ladder", "学習ラダー")

    @JvmStatic
    fun studyLadderBody(): String {
        return localizedText("Set practice order. Keep one rung on.", "練習順を設定。1段はオンのままにする。")
    }

    @JvmStatic
    fun ladderToggleLabel(enabled: Boolean): String {
        return if (enabled) localizedText("On", "オン") else localizedText("Off", "オフ")
    }

    @JvmStatic
    fun moveUpLabel(): String = localizedText("Move up", "上へ移動")

    @JvmStatic
    fun moveDownLabel(): String = localizedText("Move down", "下へ移動")

    @JvmStatic
    fun restoreDefaultLadderLabel(): String = localizedText("Restore defaults", "既定に戻す")

    @JvmStatic
    fun studyLadderRestoredToast(): String = localizedText("Ladder restored.", "ラダーを戻しました。")

    @JvmStatic
    fun keepAlwaysAvailableRungToast(): String {
        return localizedText("Leave one rung always on.", "常に1段はオンにしてください。")
    }

    @JvmStatic
    fun ladderRungToggleToast(rung: RecordsBase.LadderRung, wasEnabled: Boolean): String {
        val label = settingsLadderRungLabel(rung)
        return if (isJapaneseLocale()) {
            label + if (wasEnabled) "をオフにしました。" else "をオンにしました。"
        } else {
            label + if (wasEnabled) " turned off." else " turned on."
        }
    }

    @JvmStatic
    fun ladderRungSubtitle(ladder: RecordsBase.StudyLadderSettings, rung: RecordsBase.LadderRung): String {
        val enabled = ladder.isEnabled(rung)
        if (rung == RecordsBase.LadderRung.SIMILAR_KANJI) {
            return if (enabled) localizedText("Included when similar kanji exist", "似た漢字があるときに含める") else skippedInStudyText()
        }
        return if (enabled) localizedText("Included in study", "学習に含める") else skippedInStudyText()
    }

    @JvmStatic
    fun settingsLadderRungLabel(rung: RecordsBase.LadderRung): String {
        return when (rung) {
            RecordsBase.LadderRung.WRITE_KANJI -> localizedText("Write kanji", "漢字を書く")
            RecordsBase.LadderRung.SIMILAR_KANJI -> localizedText("Similar kanji", "似た漢字")
            RecordsBase.LadderRung.TYPE_MEANING -> localizedText("Type the meaning", "意味を入力")
            RecordsBase.LadderRung.MEANING_KANJI -> localizedText("Meaning -> kanji", "意味 → 漢字")
            RecordsBase.LadderRung.KANJI_MEANING -> localizedText("Kanji -> meaning", "漢字 → 意味")
            RecordsBase.LadderRung.FONT_MEANING -> localizedText("Font -> meaning", "フォント → 意味")
            RecordsBase.LadderRung.WORD_READING -> localizedText("Word -> reading", "単語 → 読み")
        }
    }

    private fun itemCountText(count: Int): String =
        if (isJapaneseLocale()) "${count}件" else StudyTextCopy.countText(count, "item", "items")

    private fun skippedInStudyText(): String = localizedText("Skipped in study", "学習でスキップ")

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
