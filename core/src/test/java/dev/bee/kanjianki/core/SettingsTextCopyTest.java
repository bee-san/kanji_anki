package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public final class SettingsTextCopyTest {
    @Test
    public void importSummariesPreserveSourceAndMatchingCopy() {
        assertEquals("3 matching cards per kanji", SettingsTextCopy.matchingCardsSummary(settings(true, true, true, true, true, 3)));
        assertEquals("1 matching card per kanji", SettingsTextCopy.matchingCardsSummary(settings(false, true, false, false, false, 1)));
        assertEquals("active + suspended + tagged + weak + query; 3 matching cards per kanji", SettingsTextCopy.settingsImportSummary(settings(true, true, true, true, true, 3)));
        assertEquals("No sources", SettingsTextCopy.settingsImportSummary(settings(false, false, false, false, false, 2)));
        assertThrows(NullPointerException.class, () -> SettingsTextCopy.settingsImportSummary(null));
        assertThrows(NullPointerException.class, () -> SettingsTextCopy.matchingCardsSummary(null));
    }

    @Test
    public void settingsStatusSummariesPreserveAutomationCopy() {
        assertEquals("Blocked", SettingsTextCopy.settingsReminderSummary(true, true, "21:05"));
        assertEquals("21:05", SettingsTextCopy.settingsReminderSummary(true, false, "21:05"));
        assertEquals("Off", SettingsTextCopy.settingsReminderSummary(false, false, "21:05"));
        assertEquals("After first sync", SettingsTextCopy.settingsAutoSyncSummary(false, true, "07:30"));
        assertEquals("07:30", SettingsTextCopy.settingsAutoSyncSummary(true, true, "07:30"));
        assertEquals("Off", SettingsTextCopy.settingsAutoSyncSummary(true, false, "07:30"));
        assertEquals("Verified APK ready", SettingsTextCopy.settingsUpdateSummary(true, false));
        assertEquals("Automatic checks on", SettingsTextCopy.settingsUpdateSummary(false, true));
        assertEquals("Manual checks", SettingsTextCopy.settingsUpdateSummary(false, false));
        assertEquals(
                "4 suspended cards archived, 2 rare kanji added; active cards optional",
                SettingsTextCopy.syncStatusHeadline(true, "ignored", 4, 2)
        );
        assertEquals("Sync blocked: No provider", SettingsTextCopy.syncStatusHeadline(false, "No provider", 0, 0));
        assertEquals("Sync blocked: null", SettingsTextCopy.syncStatusHeadline(false, null, 0, 0));
        assertEquals("unknown version", SettingsTextCopy.versionText(null));
        assertEquals("unknown version", SettingsTextCopy.versionText("  "));
        assertEquals("0.4.33", SettingsTextCopy.versionText("v0.4.33"));
        assertEquals("release-v0.4.33", SettingsTextCopy.versionText("release-v0.4.33"));
        assertEquals("Starts after first successful sync", SettingsTextCopy.autoSyncStatus(false, true, "07:30"));
        assertEquals("On around 07:30", SettingsTextCopy.autoSyncStatus(true, true, "07:30"));
        assertEquals("Off", SettingsTextCopy.autoSyncStatus(true, false, "07:30"));
        assertEquals(
                "Manual sync once, then Kani will keep itself refreshed once per day.",
                SettingsTextCopy.autoSyncDetail(false, true, "", "", "")
        );
        assertEquals(
                "Scheduled once per local day. Android may batch the exact time.",
                SettingsTextCopy.autoSyncDetail(true, true, "", "", "")
        );
        assertEquals("Daily background sync is paused.", SettingsTextCopy.autoSyncDetail(true, false, "", "", "tomorrow"));
        assertEquals(
                "Last auto success yesterday. Last auto attempt today. Next scheduled tomorrow.",
                SettingsTextCopy.autoSyncDetail(true, true, "yesterday", "today", "tomorrow")
        );
        assertEquals(
                "Last auto success yesterday. Last auto attempt today.",
                SettingsTextCopy.autoSyncDetail(true, false, "yesterday", "today", "tomorrow")
        );
    }

    @Test
    public void workloadSummariesPreserveSettingsCopy() {
        assertEquals("Pareto: up to 5 items", SettingsTextCopy.workloadStatusText(20, 5));
        assertEquals("All kanji: up to 9 items", SettingsTextCopy.workloadStatusText(100, 9));
        assertEquals("Maximum: 1 item", SettingsTextCopy.maxItemsStatusText(1));
        assertEquals("Auto Pareto: waiting for problem kanji", SettingsTextCopy.autoWorkloadStatusText(null));
        assertEquals(
                "Auto Pareto: 2 items today",
                SettingsTextCopy.autoWorkloadStatusText(new RecordsSchedulerModels.AdaptiveLoadPlan(true, 20, 2, 1, Arrays.asList("裂", "語"), 0, false, "auto"))
        );
        assertEquals("Maximum: 1 item", SettingsTextCopy.maxItemsStatusText(0));
    }

    @Test
    public void newCardSortCopyPreservesModeLabelsAndStatus() {
        assertEquals("Current: Frequency", SettingsTextCopy.newCardSortStatusText(RecordsBase.DEFAULT_NEW_CARD_SORT_MODE));
        assertEquals("Anki difficulty", SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY));
        assertEquals("Retrievability risk", SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK));
        assertEquals("Kani weakness", SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS));
        assertEquals("Frequency", SettingsTextCopy.newCardSortLabel("unknown"));
        assertEquals("Frequency", SettingsTextCopy.newCardSortLabel(null));
    }

    @Test
    public void rangeRetentionAndLadderCopyPreserveSettingsLabels() {
        RecordsBase.StudyLadderSettings ladder = RecordsBase.StudyLadderSettings.defaults();

        assertEquals("Jiten ranks 1-20000", SettingsTextCopy.frequencyRangeStatusText(1, 20000));
        assertEquals("Desired retention: 95%", SettingsTextCopy.retentionStatusText(95));
        assertEquals("Write kanji", SettingsTextCopy.settingsLadderRungLabel(RecordsBase.LadderRung.WRITE_KANJI));
        assertEquals("Similar kanji", SettingsTextCopy.settingsLadderRungLabel(RecordsBase.LadderRung.SIMILAR_KANJI));
        assertEquals("Type the meaning", SettingsTextCopy.settingsLadderRungLabel(RecordsBase.LadderRung.TYPE_MEANING));
        assertEquals("Meaning -> kanji", SettingsTextCopy.settingsLadderRungLabel(RecordsBase.LadderRung.MEANING_KANJI));
        assertEquals("Kanji -> meaning", SettingsTextCopy.settingsLadderRungLabel(RecordsBase.LadderRung.KANJI_MEANING));
        assertEquals("Font -> meaning", SettingsTextCopy.settingsLadderRungLabel(RecordsBase.LadderRung.FONT_MEANING));
        assertEquals("Word -> reading", SettingsTextCopy.settingsLadderRungLabel(RecordsBase.LadderRung.WORD_READING));
        assertEquals("Enabled always available rung", SettingsTextCopy.ladderRungSubtitle(ladder, RecordsBase.LadderRung.WRITE_KANJI));
        assertEquals("Enabled conditional rung", SettingsTextCopy.ladderRungSubtitle(ladder, RecordsBase.LadderRung.SIMILAR_KANJI));
        assertThrows(NullPointerException.class, () -> SettingsTextCopy.settingsLadderRungLabel(null));
    }

    @Test
    public void reminderCopyPreservesPanelStatusAndTimeFormatting() {
        assertEquals("Blocked: notifications off", SettingsTextCopy.reminderStatus(true, true, "21:05"));
        assertEquals("Daily around 21:05", SettingsTextCopy.reminderStatus(true, false, "21:05"));
        assertEquals("Off", SettingsTextCopy.reminderStatus(false, false, "21:05"));
        assertEquals("21:05", SettingsTextCopy.reminderTime(21, 5));
        assertEquals("Reminder time: 21:05", SettingsTextCopy.reminderTimeButtonLabel(21, 5));
    }

    @Test
    public void studyAheadCopyPreservesLabelsAndValidationMessages() {
        assertEquals("Minutes (0-1440)", SettingsTextCopy.studyAheadMinutesLabel());
        assertEquals("0-1440", SettingsTextCopy.studyAheadMinutesRange());
        assertEquals("1440 minutes (24h)", SettingsTextCopy.studyAheadMaxDescription());
        assertEquals("Use a whole number of minutes (0-1440).", SettingsTextCopy.studyAheadWholeNumberErrorText());
        assertEquals("Use 0 to disable, or up to 1440 minutes (24h).", SettingsTextCopy.studyAheadOutOfRangeErrorText());
    }

    private static RecordsSyncModels.Settings settings(
            boolean active,
            boolean suspended,
            boolean tagged,
            boolean weak,
            boolean query,
            int matchingCards
    ) {
        RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();
        return new RecordsSyncModels.Settings(
                defaults.modelName,
                defaults.templateName,
                defaults.expressionField,
                defaults.readingField,
                defaults.meaningField,
                defaults.sentenceField,
                defaults.frequencyField,
                defaults.frequencySortField,
                defaults.matureDays,
                defaults.matureSupportThreshold,
                defaults.suspendedRankMin,
                defaults.suspendedRankMax,
                defaults.activeQueueCap,
                defaults.newPerDay,
                defaults.writingTriggerMissDays,
                defaults.recognitionPromotionPasses,
                defaults.realDueReviewsToMove,
                active,
                suspended,
                tagged,
                tagged ? Collections.singletonList("leeches") : Collections.emptyList(),
                weak,
                defaults.importWeakFsrsDifficultyThreshold,
                defaults.importWeakLapsesThreshold,
                matchingCards,
                query,
                query ? "deck:Kiku" : "",
                defaults.newCardSortMode,
                defaults.ladderPromotionIntervalDays,
                defaults.ladderDemotionFailStreak
        );
    }
}
