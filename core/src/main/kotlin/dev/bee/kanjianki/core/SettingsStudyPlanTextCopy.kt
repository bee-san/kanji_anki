package dev.bee.kanjianki.core

import java.util.Locale

object SettingsStudyPlanTextCopy {
    @JvmStatic
    fun deckLimitsTitle(): String = "Daily limits"

    @JvmStatic
    fun deckLimitsBody(): String = "Cap new cards each day."

    @JvmStatic
    fun newCardsPerDayLabel(): String = "Daily new card limit"

    @JvmStatic
    fun saveDeckLimitsLabel(): String = "Save daily limits"

    @JvmStatic
    fun dailyWorkloadTitle(): String = "Daily workload"

    @JvmStatic
    fun automaticWorkloadBody(): String {
        return "Kani sets today's count. Due dates stay fixed."
    }

    @JvmStatic
    fun saveMaximumLabel(): String = "Save limit"

    @JvmStatic
    fun manualWorkloadLabel(): String = "Set workload manually"

    @JvmStatic
    fun manualWorkloadBody(): String {
        return "Pick today's count. Due dates stay fixed."
    }

    @JvmStatic
    fun workloadScaleLabels(): Array<String> {
        return arrayOf("Very little", "Focused", "Balanced", "More", "All kanji")
    }

    @JvmStatic
    fun saveWorkloadLabel(): String = "Save workload"

    @JvmStatic
    fun automaticParetoLabel(): String = "Use automatic workload"

    @JvmStatic
    fun workloadStatusText(percent: Int, maxItems: Int): String {
        val snapped = AdaptiveLoadPlanner.snapWorkloadPercent(percent)
        val normalizedMax = AdaptiveLoadPlanner.normalizeMaxItems(maxItems)
        val label = workloadStatusLabel(snapped)
        if (snapped >= 100) {
            return "$label: up to " + StudyTextCopy.countText(normalizedMax, "item", "items")
        }
        return "$label: up to " + StudyTextCopy.countText(
            minOf(AdaptiveLoadPlanner.targetCeiling(snapped), normalizedMax),
            "item",
            "items",
        )
    }

    @JvmStatic
    fun maxItemsStatusText(maxItems: Int): String {
        return "Maximum: " + StudyTextCopy.countText(AdaptiveLoadPlanner.normalizeMaxItems(maxItems), "item", "items")
    }

    @JvmStatic
    fun autoWorkloadStatusText(plan: RecordsSchedulerModels.AdaptiveLoadPlan?): String {
        if (plan == null || plan.target <= 0) {
            return "Automatic workload: waiting for cards"
        }
        return "Automatic workload: " + StudyTextCopy.countText(plan.target, "item", "items") + " today"
    }

    private fun workloadStatusLabel(snappedWorkloadPercent: Int): String {
        if (snappedWorkloadPercent <= 0) {
            return "Very little"
        }
        if (snappedWorkloadPercent <= 20) {
            return "Focused"
        }
        if (snappedWorkloadPercent <= 50) {
            return "Balanced"
        }
        if (snappedWorkloadPercent < 100) {
            return "More"
        }
        return "All kanji"
    }

    @JvmStatic
    fun newCardSortStatusText(mode: String?): String {
        return "Current: " + newCardSortLabel(mode)
    }

    @JvmStatic
    fun newCardSortLabel(mode: String?): String {
        return when (RecordsSyncModels.Settings.normalizeNewCardSortMode(mode)) {
            RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY -> "Anki difficulty"
            RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK -> "Retrievability risk"
            RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS -> "Kani weakness"
            RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY -> "Balanced priority"
            else -> "Frequency"
        }
    }

    @JvmStatic
    fun newCardSortDescription(mode: String?): String {
        return when (RecordsSyncModels.Settings.normalizeNewCardSortMode(mode)) {
            RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY -> "Harder cards first."
            RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK -> "Most-forgotten cards first."
            RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS -> "Weaker Kani cards first."
            RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY -> "Balances weakness, risk, misses, and frequency."
            else -> "Most frequent Jiten ranks first."
        }
    }

    @JvmStatic
    fun frequencyRangeStatusText(minRank: Int, maxRank: Int): String {
        return String.format(Locale.ROOT, "Jiten ranks %d-%d", minRank, maxRank)
    }

    @JvmStatic
    fun retentionStatusText(retentionPercent: Int): String {
        return "Desired retention: $retentionPercent%"
    }

    @JvmStatic
    fun newCardSortTitle(): String = "New card sort"

    @JvmStatic
    fun newCardSortBody(): String {
        return "New cards only; due reviews and repeats stay first."
    }

    @JvmStatic
    fun saveNewCardSortLabel(): String = "Save new card sort"

    @JvmStatic
    fun newCardSortConfusablePreviewWarning(examples: List<String>): String {
        val suffix = if (examples.isEmpty()) "" else ": " + examples.joinToString(", ")
        return "Similar kanji stay close on this screen$suffix."
    }

    @JvmStatic
    fun fsrsRetentionTitle(): String = "Review retention"

    @JvmStatic
    fun fsrsRetentionBody(): String {
        return "FSRS stays local; due dates stay unchanged."
    }

    @JvmStatic
    fun useJitenRankRetentionRangesLabel(): String = "Retention by Jiten rank"

    @JvmStatic
    fun jitenRankRetentionRangesBody(): String {
        return "One range per line: 1-500=95%. Other ranks use the global target."
    }

    @JvmStatic
    fun useExampleRangesLabel(): String = "Use example ranges"

    @JvmStatic
    fun saveRetentionLabel(): String = "Save retention"

    @JvmStatic
    fun retentionPresetLabel(value: Int): String {
        return "$value%"
    }

    @JvmStatic
    fun studyLadderTitle(): String = "Study ladder"

    @JvmStatic
    fun studyLadderBody(): String {
        return "Choose practice order. Keep at least one rung enabled."
    }

    @JvmStatic
    fun ladderToggleLabel(enabled: Boolean): String {
        return if (enabled) "On" else "Off"
    }

    @JvmStatic
    fun moveUpLabel(): String = "Up"

    @JvmStatic
    fun moveDownLabel(): String = "Down"

    @JvmStatic
    fun restoreDefaultLadderLabel(): String = "Restore defaults"

    @JvmStatic
    fun studyLadderRestoredToast(): String = "Ladder restored."

    @JvmStatic
    fun keepAlwaysAvailableRungToast(): String {
        return "Keep at least one rung on."
    }

    @JvmStatic
    fun ladderRungToggleToast(rung: RecordsBase.LadderRung, wasEnabled: Boolean): String {
        return settingsLadderRungLabel(rung) + if (wasEnabled) " turned off." else " turned on."
    }

    @JvmStatic
    fun ladderRungSubtitle(ladder: RecordsBase.StudyLadderSettings, rung: RecordsBase.LadderRung): String {
        val enabled = ladder.isEnabled(rung)
        if (rung == RecordsBase.LadderRung.SIMILAR_KANJI) {
            return if (enabled) "On when similar kanji exist" else "Off: similar kanji skipped"
        }
        return if (enabled) "On: always available" else "Off: skipped"
    }

    @JvmStatic
    fun settingsLadderRungLabel(rung: RecordsBase.LadderRung): String {
        return when (rung) {
            RecordsBase.LadderRung.WRITE_KANJI -> "Write kanji"
            RecordsBase.LadderRung.SIMILAR_KANJI -> "Similar kanji"
            RecordsBase.LadderRung.TYPE_MEANING -> "Type the meaning"
            RecordsBase.LadderRung.MEANING_KANJI -> "Meaning -> kanji"
            RecordsBase.LadderRung.KANJI_MEANING -> "Kanji -> meaning"
            RecordsBase.LadderRung.FONT_MEANING -> "Font -> meaning"
            RecordsBase.LadderRung.WORD_READING -> "Word -> reading"
        }
    }
}
