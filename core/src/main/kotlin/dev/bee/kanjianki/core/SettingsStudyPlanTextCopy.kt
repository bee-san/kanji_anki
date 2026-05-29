package dev.bee.kanjianki.core

import java.util.Locale

object SettingsStudyPlanTextCopy {
    @JvmStatic
    fun deckLimitsTitle(): String = "Deck limits"

    @JvmStatic
    fun deckLimitsBody(): String = "Set the maximum new cards Kani admits each day, matching Anki deck options."

    @JvmStatic
    fun newCardsPerDayLabel(): String = "Maximum new cards/day"

    @JvmStatic
    fun saveDeckLimitsLabel(): String = "Save deck limits"

    @JvmStatic
    fun dailyWorkloadTitle(): String = "Daily workload"

    @JvmStatic
    fun automaticWorkloadBody(): String {
        return "Kani automatically chooses where today's problem-kanji priority curve drops off. This changes how much it admits today, not Anki's schedule."
    }

    @JvmStatic
    fun saveMaximumLabel(): String = "Save maximum"

    @JvmStatic
    fun manualWorkloadLabel(): String = "Use manual workload"

    @JvmStatic
    fun manualWorkloadBody(): String {
        return "Manual workload overrides the automatic Pareto drop-off. This changes how much Kani admits today, not Anki's schedule."
    }

    @JvmStatic
    fun workloadScaleLabels(): Array<String> {
        return arrayOf("Very little", "Pareto", "Balanced", "More", "All kanji")
    }

    @JvmStatic
    fun saveWorkloadLabel(): String = "Save workload"

    @JvmStatic
    fun automaticParetoLabel(): String = "Use automatic Pareto"

    @JvmStatic
    fun workloadStatusText(percent: Int, maxItems: Int): String {
        val snapped = AdaptiveLoadPlanner.snapWorkloadPercent(percent)
        val normalizedMax = AdaptiveLoadPlanner.normalizeMaxItems(maxItems)
        val label = AdaptiveLoadPlanner.workloadLabel(snapped)
        if (snapped >= 100) {
            return "$label: up to $normalizedMax items"
        }
        return "$label: up to ${minOf(AdaptiveLoadPlanner.targetCeiling(snapped), normalizedMax)} items"
    }

    @JvmStatic
    fun maxItemsStatusText(maxItems: Int): String {
        return "Maximum: " + StudyTextCopy.countText(AdaptiveLoadPlanner.normalizeMaxItems(maxItems), "item", "items")
    }

    @JvmStatic
    fun autoWorkloadStatusText(plan: RecordsSchedulerModels.AdaptiveLoadPlan?): String {
        if (plan == null || plan.target <= 0) {
            return "Auto Pareto: waiting for problem kanji"
        }
        return "Auto Pareto: " + StudyTextCopy.countText(plan.target, "item", "items") + " today"
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
            else -> "Frequency"
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
        return "Choose how Kani admits and shows unseen new cards. Due reviews and learning repeats still keep their normal priority."
    }

    @JvmStatic
    fun saveNewCardSortLabel(): String = "Save new card sort"

    @JvmStatic
    fun fsrsRetentionTitle(): String = "FSRS retention"

    @JvmStatic
    fun fsrsRetentionBody(): String {
        return "Higher retention keeps intervals shorter. This changes Kani's internal FSRS intervals, not Anki's schedule."
    }

    @JvmStatic
    fun useJitenRankRetentionRangesLabel(): String = "Use Jiten-rank retention ranges"

    @JvmStatic
    fun jitenRankRetentionRangesBody(): String {
        return "Optional: one inclusive Jiten rank range per line, such as 1-500=95%. Unmatched or unranked kanji use the global retention above."
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
        return "Turn rungs off or move them up and down. At least one always-available rung stays on."
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
    fun restoreDefaultLadderLabel(): String = "Restore default ladder"

    @JvmStatic
    fun studyLadderRestoredToast(): String = "Study ladder restored."

    @JvmStatic
    fun keepAlwaysAvailableRungToast(): String {
        return "Keep at least one always-available rung on."
    }

    @JvmStatic
    fun ladderRungToggleToast(rung: RecordsBase.LadderRung, wasEnabled: Boolean): String {
        return settingsLadderRungLabel(rung) + if (wasEnabled) " off." else " on."
    }

    @JvmStatic
    fun ladderRungSubtitle(ladder: RecordsBase.StudyLadderSettings, rung: RecordsBase.LadderRung): String {
        val status = if (ladder.isEnabled(rung)) "Enabled" else "Disabled"
        val kind = if (rung == RecordsBase.LadderRung.SIMILAR_KANJI) "conditional" else "always available"
        return "$status $kind rung"
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
