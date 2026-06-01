package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsStudyPlanTextCopyTest {
    @Test
    fun workloadAndSortHelpersPreserveFormatting() {
        assertEquals("Daily workload", SettingsStudyPlanTextCopy.dailyWorkloadTitle())
        assertEquals("Save maximum", SettingsStudyPlanTextCopy.saveMaximumLabel())
        assertEquals("Use manual workload", SettingsStudyPlanTextCopy.manualWorkloadLabel())
        assertEquals("Pareto: up to 5 items", SettingsStudyPlanTextCopy.workloadStatusText(20, 5))
        assertEquals("Maximum: 1 item", SettingsStudyPlanTextCopy.maxItemsStatusText(0))
        assertEquals("Auto Pareto: waiting for problem kanji", SettingsStudyPlanTextCopy.autoWorkloadStatusText(null))
        assertEquals("Current: Frequency", SettingsStudyPlanTextCopy.newCardSortStatusText(RecordsBase.DEFAULT_NEW_CARD_SORT_MODE))
        assertEquals("Kani weakness", SettingsStudyPlanTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS))
        assertEquals("Balanced priority", SettingsStudyPlanTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY))
        assertEquals("Jiten frequency first.", SettingsStudyPlanTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_FREQUENCY))
        assertEquals("Harder Anki cards first.", SettingsStudyPlanTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY))
        assertEquals("Cards most likely to be forgotten first.", SettingsStudyPlanTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK))
        assertEquals("Kanji with weaker Kani history first.", SettingsStudyPlanTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS))
        assertEquals(
            "Mixes Kani weakness, Anki risk, missed examples, and frequency.",
            SettingsStudyPlanTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY),
        )
        assertEquals("New card sort", SettingsStudyPlanTextCopy.newCardSortTitle())
        assertEquals("Choose how unseen cards enter study; due reviews and repeats stay first.", SettingsStudyPlanTextCopy.newCardSortBody())
        assertEquals("Save new card sort", SettingsStudyPlanTextCopy.saveNewCardSortLabel())
        assertEquals(
            "Heads up: visually similar kanji appear close together in this preview: 人/入, 土/士.",
            SettingsStudyPlanTextCopy.newCardSortConfusablePreviewWarning(listOf("人/入", "土/士")),
        )
        assertEquals(
            "Heads up: visually similar kanji appear close together in this preview.",
            SettingsStudyPlanTextCopy.newCardSortConfusablePreviewWarning(emptyList()),
        )
        assertEquals("Deck limits", SettingsStudyPlanTextCopy.deckLimitsTitle())
        assertEquals("Set the daily new-card cap from Anki deck options.", SettingsStudyPlanTextCopy.deckLimitsBody())
        assertEquals("Maximum new cards/day", SettingsStudyPlanTextCopy.newCardsPerDayLabel())
        assertEquals("Save deck limits", SettingsStudyPlanTextCopy.saveDeckLimitsLabel())
        assertEquals("Jiten ranks 1-20000", SettingsStudyPlanTextCopy.frequencyRangeStatusText(1, 20000))
        assertEquals("Desired retention: 95%", SettingsStudyPlanTextCopy.retentionStatusText(95))
        assertEquals("FSRS retention", SettingsStudyPlanTextCopy.fsrsRetentionTitle())
        assertEquals("Higher retention keeps Kani intervals shorter; Anki due dates stay unchanged.", SettingsStudyPlanTextCopy.fsrsRetentionBody())
        assertEquals("Use Jiten-rank retention ranges", SettingsStudyPlanTextCopy.useJitenRankRetentionRangesLabel())
        assertEquals("Optional: one Jiten rank range per line, like 1-500=95%. Other kanji use global retention.", SettingsStudyPlanTextCopy.jitenRankRetentionRangesBody())
        assertEquals("Use example ranges", SettingsStudyPlanTextCopy.useExampleRangesLabel())
        assertEquals("Save retention", SettingsStudyPlanTextCopy.saveRetentionLabel())
        assertEquals("95%", SettingsStudyPlanTextCopy.retentionPresetLabel(95))
        assertEquals("Study ladder", SettingsStudyPlanTextCopy.studyLadderTitle())
        assertEquals("On", SettingsStudyPlanTextCopy.ladderToggleLabel(true))
        assertEquals("Off", SettingsStudyPlanTextCopy.ladderToggleLabel(false))
        assertEquals("Write kanji off.", SettingsStudyPlanTextCopy.ladderRungToggleToast(RecordsBase.LadderRung.WRITE_KANJI, true))
        assertEquals(
            "Enabled conditional rung",
            SettingsStudyPlanTextCopy.ladderRungSubtitle(
                RecordsBase.StudyLadderSettings.defaults().withRungEnabled(RecordsBase.LadderRung.SIMILAR_KANJI, true),
                RecordsBase.LadderRung.SIMILAR_KANJI,
            ),
        )
    }
}
