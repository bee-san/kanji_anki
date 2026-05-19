package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SettingsStudyPlanTextCopyTest {
    @Test
    public void workloadAndSortHelpersPreserveFormatting() {
        assertEquals("Daily workload", SettingsStudyPlanTextCopy.dailyWorkloadTitle());
        assertEquals("Save maximum", SettingsStudyPlanTextCopy.saveMaximumLabel());
        assertEquals("Use manual workload", SettingsStudyPlanTextCopy.manualWorkloadLabel());
        assertEquals("Pareto: up to 5 items", SettingsStudyPlanTextCopy.workloadStatusText(20, 5));
        assertEquals("Maximum: 1 item", SettingsStudyPlanTextCopy.maxItemsStatusText(0));
        assertEquals("Auto Pareto: waiting for problem kanji", SettingsStudyPlanTextCopy.autoWorkloadStatusText(null));
        assertEquals("Current: Frequency", SettingsStudyPlanTextCopy.newCardSortStatusText(RecordsBase.DEFAULT_NEW_CARD_SORT_MODE));
        assertEquals("Kani weakness", SettingsStudyPlanTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS));
        assertEquals("New card sort", SettingsStudyPlanTextCopy.newCardSortTitle());
        assertEquals("Choose how Kani admits and shows unseen new cards. Due reviews and learning repeats still keep their normal priority.", SettingsStudyPlanTextCopy.newCardSortBody());
        assertEquals("Save new card sort", SettingsStudyPlanTextCopy.saveNewCardSortLabel());
        assertEquals("Jiten ranks 1-20000", SettingsStudyPlanTextCopy.frequencyRangeStatusText(1, 20000));
        assertEquals("Desired retention: 95%", SettingsStudyPlanTextCopy.retentionStatusText(95));
        assertEquals("FSRS retention", SettingsStudyPlanTextCopy.fsrsRetentionTitle());
        assertEquals("Higher retention keeps intervals shorter. This changes Kani's internal FSRS intervals, not Anki's schedule.", SettingsStudyPlanTextCopy.fsrsRetentionBody());
        assertEquals("Use Jiten-rank retention ranges", SettingsStudyPlanTextCopy.useJitenRankRetentionRangesLabel());
        assertEquals("Optional: one inclusive Jiten rank range per line, such as 1-500=95%. Unmatched or unranked kanji use the global retention above.", SettingsStudyPlanTextCopy.jitenRankRetentionRangesBody());
        assertEquals("Use example ranges", SettingsStudyPlanTextCopy.useExampleRangesLabel());
        assertEquals("Save retention", SettingsStudyPlanTextCopy.saveRetentionLabel());
        assertEquals("95%", SettingsStudyPlanTextCopy.retentionPresetLabel(95));
        assertEquals("Study ladder", SettingsStudyPlanTextCopy.studyLadderTitle());
        assertEquals("On", SettingsStudyPlanTextCopy.ladderToggleLabel(true));
        assertEquals("Off", SettingsStudyPlanTextCopy.ladderToggleLabel(false));
        assertEquals("Write kanji off.", SettingsStudyPlanTextCopy.ladderRungToggleToast(RecordsBase.LadderRung.WRITE_KANJI, true));
        assertEquals("Enabled conditional rung", SettingsStudyPlanTextCopy.ladderRungSubtitle(
                RecordsBase.StudyLadderSettings.defaults().withRungEnabled(RecordsBase.LadderRung.SIMILAR_KANJI, true),
                RecordsBase.LadderRung.SIMILAR_KANJI
        ));
    }
}
