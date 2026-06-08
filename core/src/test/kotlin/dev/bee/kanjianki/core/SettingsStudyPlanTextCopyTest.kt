package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsStudyPlanTextCopyTest {
    @Test
    fun workloadAndSortHelpersPreserveFormatting() {
        assertEquals("Daily workload", SettingsStudyPlanTextCopy.dailyWorkloadTitle())
        assertEquals("Save limit", SettingsStudyPlanTextCopy.saveMaximumLabel())
        assertEquals("Choose workload manually", SettingsStudyPlanTextCopy.manualWorkloadLabel())
        assertEquals("Kani chooses today's item count. Review due dates stay fixed.", SettingsStudyPlanTextCopy.automaticWorkloadBody())
        assertEquals("Choose today's item count. Review due dates stay fixed.", SettingsStudyPlanTextCopy.manualWorkloadBody())
        assertEquals("Very little: up to 1 item", SettingsStudyPlanTextCopy.workloadStatusText(0, 5))
        assertEquals("Focused: up to 5 items", SettingsStudyPlanTextCopy.workloadStatusText(20, 5))
        assertEquals("Balanced: up to 11 items", SettingsStudyPlanTextCopy.workloadStatusText(50, 20))
        assertEquals("More: up to 17 items", SettingsStudyPlanTextCopy.workloadStatusText(80, 20))
        assertEquals("Maximum: 1 item", SettingsStudyPlanTextCopy.maxItemsStatusText(0))
        assertEquals("Automatic workload: waiting for cards", SettingsStudyPlanTextCopy.autoWorkloadStatusText(null))
        assertEquals("Current: Frequency", SettingsStudyPlanTextCopy.newCardSortStatusText(RecordsBase.DEFAULT_NEW_CARD_SORT_MODE))
        assertEquals("Kani weakness", SettingsStudyPlanTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS))
        assertEquals("Balanced priority", SettingsStudyPlanTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY))
        assertEquals("Jiten frequency first.", SettingsStudyPlanTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_FREQUENCY))
        assertEquals("Harder cards first.", SettingsStudyPlanTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY))
        assertEquals("Most-forgotten cards first.", SettingsStudyPlanTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK))
        assertEquals("Weaker Kani cards first.", SettingsStudyPlanTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS))
        assertEquals(
            "Balances weakness, risk, misses, and frequency.",
            SettingsStudyPlanTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY),
        )
        assertEquals("New card sort", SettingsStudyPlanTextCopy.newCardSortTitle())
        assertEquals("Choose how new cards enter study. Due reviews and repeats stay first.", SettingsStudyPlanTextCopy.newCardSortBody())
        assertEquals("Save new card sort", SettingsStudyPlanTextCopy.saveNewCardSortLabel())
        assertEquals(
            "Similar kanji stay close on this screen: 人/入, 土/士.",
            SettingsStudyPlanTextCopy.newCardSortConfusablePreviewWarning(listOf("人/入", "土/士")),
        )
        assertEquals(
            "Similar kanji stay close on this screen.",
            SettingsStudyPlanTextCopy.newCardSortConfusablePreviewWarning(emptyList()),
        )
        assertEquals("Daily limits", SettingsStudyPlanTextCopy.deckLimitsTitle())
        assertEquals("Limit new cards per day.", SettingsStudyPlanTextCopy.deckLimitsBody())
        assertEquals("Daily new card limit", SettingsStudyPlanTextCopy.newCardsPerDayLabel())
        assertEquals("Save daily limits", SettingsStudyPlanTextCopy.saveDeckLimitsLabel())
        assertEquals("Jiten ranks 1-20000", SettingsStudyPlanTextCopy.frequencyRangeStatusText(1, 20000))
        assertEquals("Desired retention: 95%", SettingsStudyPlanTextCopy.retentionStatusText(95))
        assertEquals("Review retention", SettingsStudyPlanTextCopy.fsrsRetentionTitle())
        assertEquals(
            "FSRS stays local. Review due dates stay unchanged.",
            SettingsStudyPlanTextCopy.fsrsRetentionBody(),
        )
        assertEquals("Jiten-rank retention ranges", SettingsStudyPlanTextCopy.useJitenRankRetentionRangesLabel())
        assertEquals("One range per line, e.g. 1-500=95%. Others use global retention.", SettingsStudyPlanTextCopy.jitenRankRetentionRangesBody())
        assertEquals("Use example ranges", SettingsStudyPlanTextCopy.useExampleRangesLabel())
        assertEquals("Save retention", SettingsStudyPlanTextCopy.saveRetentionLabel())
        assertEquals("95%", SettingsStudyPlanTextCopy.retentionPresetLabel(95))
        assertEquals("Study ladder", SettingsStudyPlanTextCopy.studyLadderTitle())
        assertEquals("Choose rung order. Keep one enabled.", SettingsStudyPlanTextCopy.studyLadderBody())
        assertEquals("Keep one rung enabled.", SettingsStudyPlanTextCopy.keepAlwaysAvailableRungToast())
        assertEquals("On", SettingsStudyPlanTextCopy.ladderToggleLabel(true))
        assertEquals("Off", SettingsStudyPlanTextCopy.ladderToggleLabel(false))
        assertEquals("Write kanji off.", SettingsStudyPlanTextCopy.ladderRungToggleToast(RecordsBase.LadderRung.WRITE_KANJI, true))
        assertEquals(
            "On: always available",
            SettingsStudyPlanTextCopy.ladderRungSubtitle(
                RecordsBase.StudyLadderSettings.defaults(),
                RecordsBase.LadderRung.WRITE_KANJI,
            ),
        )
        assertEquals(
            "Off: skipped",
            SettingsStudyPlanTextCopy.ladderRungSubtitle(
                RecordsBase.StudyLadderSettings.defaults().withRungEnabled(RecordsBase.LadderRung.WRITE_KANJI, false),
                RecordsBase.LadderRung.WRITE_KANJI,
            ),
        )
        assertEquals(
            "On when similar kanji exist",
            SettingsStudyPlanTextCopy.ladderRungSubtitle(
                RecordsBase.StudyLadderSettings.defaults().withRungEnabled(RecordsBase.LadderRung.SIMILAR_KANJI, true),
                RecordsBase.LadderRung.SIMILAR_KANJI,
            ),
        )
        assertEquals(
            "Off: similar kanji skipped",
            SettingsStudyPlanTextCopy.ladderRungSubtitle(
                RecordsBase.StudyLadderSettings.defaults().withRungEnabled(RecordsBase.LadderRung.SIMILAR_KANJI, false),
                RecordsBase.LadderRung.SIMILAR_KANJI,
            ),
        )
    }
}
