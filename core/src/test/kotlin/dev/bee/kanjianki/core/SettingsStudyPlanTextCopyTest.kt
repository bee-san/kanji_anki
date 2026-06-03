package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsStudyPlanTextCopyTest {
    @Test
    fun workloadAndSortHelpersPreserveFormatting() {
        assertEquals("Daily workload", SettingsStudyPlanTextCopy.dailyWorkloadTitle())
        assertEquals("Save item limit", SettingsStudyPlanTextCopy.saveMaximumLabel())
        assertEquals("Set workload manually", SettingsStudyPlanTextCopy.manualWorkloadLabel())
        assertEquals("Focused: up to 5 items", SettingsStudyPlanTextCopy.workloadStatusText(20, 5))
        assertEquals("Maximum: 1 item", SettingsStudyPlanTextCopy.maxItemsStatusText(0))
        assertEquals("Automatic workload: waiting for problem kanji", SettingsStudyPlanTextCopy.autoWorkloadStatusText(null))
        assertEquals("Current: Frequency", SettingsStudyPlanTextCopy.newCardSortStatusText(RecordsBase.DEFAULT_NEW_CARD_SORT_MODE))
        assertEquals("Kani weakness", SettingsStudyPlanTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS))
        assertEquals("Balanced priority", SettingsStudyPlanTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY))
        assertEquals("Jiten frequency first.", SettingsStudyPlanTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_FREQUENCY))
        assertEquals("Harder Anki cards first.", SettingsStudyPlanTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY))
        assertEquals("Cards most likely to be forgotten first.", SettingsStudyPlanTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK))
        assertEquals("Kanji with weaker Kani history first.", SettingsStudyPlanTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS))
        assertEquals(
            "Balances Kani weakness, Anki risk, missed examples, and frequency.",
            SettingsStudyPlanTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY),
        )
        assertEquals("New card sort", SettingsStudyPlanTextCopy.newCardSortTitle())
        assertEquals("Choose how new cards enter study; due reviews and repeats stay first.", SettingsStudyPlanTextCopy.newCardSortBody())
        assertEquals("Save new card sort", SettingsStudyPlanTextCopy.saveNewCardSortLabel())
        assertEquals(
            "Heads up: visually similar kanji appear close together in this preview: 人/入, 土/士.",
            SettingsStudyPlanTextCopy.newCardSortConfusablePreviewWarning(listOf("人/入", "土/士")),
        )
        assertEquals(
            "Heads up: visually similar kanji appear close together in this preview.",
            SettingsStudyPlanTextCopy.newCardSortConfusablePreviewWarning(emptyList()),
        )
        assertEquals("Daily limits", SettingsStudyPlanTextCopy.deckLimitsTitle())
        assertEquals("Limit how many new cards Kani can show each day.", SettingsStudyPlanTextCopy.deckLimitsBody())
        assertEquals("Daily new card limit", SettingsStudyPlanTextCopy.newCardsPerDayLabel())
        assertEquals("Save daily limits", SettingsStudyPlanTextCopy.saveDeckLimitsLabel())
        assertEquals("Jiten ranks 1-20000", SettingsStudyPlanTextCopy.frequencyRangeStatusText(1, 20000))
        assertEquals("Desired retention: 95%", SettingsStudyPlanTextCopy.retentionStatusText(95))
        assertEquals("Review retention", SettingsStudyPlanTextCopy.fsrsRetentionTitle())
        assertEquals(
            "FSRS stays local. Anki due dates stay unchanged.",
            SettingsStudyPlanTextCopy.fsrsRetentionBody(),
        )
        assertEquals("Jiten-rank retention ranges", SettingsStudyPlanTextCopy.useJitenRankRetentionRangesLabel())
        assertEquals("Optional: one Jiten rank range per line, like 1-500=95%. Other kanji use global retention.", SettingsStudyPlanTextCopy.jitenRankRetentionRangesBody())
        assertEquals("Use example ranges", SettingsStudyPlanTextCopy.useExampleRangesLabel())
        assertEquals("Save retention", SettingsStudyPlanTextCopy.saveRetentionLabel())
        assertEquals("95%", SettingsStudyPlanTextCopy.retentionPresetLabel(95))
        assertEquals("Study ladder", SettingsStudyPlanTextCopy.studyLadderTitle())
        assertEquals("On", SettingsStudyPlanTextCopy.ladderToggleLabel(true))
        assertEquals("Off", SettingsStudyPlanTextCopy.ladderToggleLabel(false))
        assertEquals("Write kanji off.", SettingsStudyPlanTextCopy.ladderRungToggleToast(RecordsBase.LadderRung.WRITE_KANJI, true))
        assertEquals(
            "Conditional rung enabled",
            SettingsStudyPlanTextCopy.ladderRungSubtitle(
                RecordsBase.StudyLadderSettings.defaults().withRungEnabled(RecordsBase.LadderRung.SIMILAR_KANJI, true),
                RecordsBase.LadderRung.SIMILAR_KANJI,
            ),
        )
    }
}
