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
        assertEquals("Anki source", SettingsTextCopy.settingsAnkiSourceTitle());
        assertEquals(
                "What Kani reads from AnkiDroid, and which cards become practice.",
                SettingsTextCopy.settingsAnkiSourceBody()
        );
        assertEquals("Study behavior", SettingsTextCopy.settingsStudyBehaviorTitle());
        assertEquals(
                "How much appears today, how quickly repeats return, and when cards move rungs.",
                SettingsTextCopy.settingsStudyBehaviorBody()
        );
        assertEquals("Automation", SettingsTextCopy.settingsAutomationTitle());
        assertEquals(
                "Background nudges, daily AnkiDroid refreshes, and app update checks.",
                SettingsTextCopy.settingsAutomationBody()
        );
        assertEquals("Reference data", SettingsTextCopy.settingsReferenceDataTitle());
        assertEquals(
                "Offline dictionaries, frequency ranks, stroke data, fonts, and attribution.",
                SettingsTextCopy.settingsReferenceDataBody()
        );
        assertEquals("GitHub updater", SettingsTextCopy.updatePageTitle());
        assertEquals(
                "Current version 1.2.3. Checks GitHub Releases, verifies the APK, and asks Android to install it.",
                SettingsTextCopy.updatePageBody("1.2.3")
        );
        assertEquals("Automatic updates", SettingsTextCopy.automaticUpdatesTitle());
        assertEquals("Check for update", SettingsTextCopy.checkForUpdateLabel());
        assertEquals("On: checks about once a day", SettingsTextCopy.autoUpdatePanelStatus(true));
        assertEquals("Off", SettingsTextCopy.autoUpdatePanelStatus(false));
        assertEquals("Last check: not yet", SettingsTextCopy.autoUpdateLastCheckLine("not yet"));
        assertEquals("Last result: none", SettingsTextCopy.autoUpdateLastResultLine("none"));
        assertEquals("Install permission: Ready", SettingsTextCopy.installPermissionLine(true));
        assertEquals("Install permission: Missing", SettingsTextCopy.installPermissionLine(false));
        assertEquals("Verified APK ready: 0.4.33", SettingsTextCopy.verifiedApkReadyLine("v0.4.33"));
        assertEquals(
                "Android needs confirmation before Kani can replace itself.",
                SettingsTextCopy.pendingUpdateFallback()
        );
        assertEquals("Install verified update", SettingsTextCopy.installVerifiedUpdateLabel());
        assertEquals("Set up app installs", SettingsTextCopy.setupAppInstallsLabel());
        assertEquals("Turn off automatic updates", SettingsTextCopy.automaticUpdatesToggleLabel(true));
        assertEquals("Turn on automatic updates", SettingsTextCopy.automaticUpdatesToggleLabel(false));
        assertEquals("Back to settings", SettingsTextCopy.backToSettingsLabel());
        assertEquals("Settings cockpit", SettingsTextCopy.settingsCockpitLabel());
        assertEquals(
                "Grouped by outcome: source data, study behavior, automation, and offline references. Each setting appears once, next to the thing it changes.",
                SettingsTextCopy.settingsHeroBody()
        );
        assertEquals("Note type", SettingsTextCopy.noteTypeStatusLabel());
        assertEquals("Import filters", SettingsTextCopy.importFiltersStatusLabel());
        assertEquals("Import ranks", SettingsTextCopy.importRanksStatusLabel());
        assertEquals("Reminder", SettingsTextCopy.reminderStatusLabel());
        assertEquals("Daily sync", SettingsTextCopy.dailySyncStatusLabel());
        assertEquals("Updates", SettingsTextCopy.updatesStatusLabel());
        assertEquals("Matching cards", SettingsTextCopy.matchingCardsStatusLabel());
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
    public void importAndFrequencyPanelCopyPreservesLabelsAndToasts() {
        assertEquals("Import filters", SettingsTextCopy.importFiltersTitle());
        assertEquals(
                "Suspended AnkiDroid cards are the default source for Kani practice. Turn on active, tagged, or weak cards only when you want those sources included.",
                SettingsTextCopy.importFiltersBody()
        );
        assertEquals("Active cards", SettingsTextCopy.activeCardsLabel());
        assertEquals("Suspended cards", SettingsTextCopy.suspendedCardsLabel());
        assertEquals("Tagged cards", SettingsTextCopy.taggedCardsLabel());
        assertEquals("Weak cards", SettingsTextCopy.weakCardsLabel());
        assertEquals("Browser query", SettingsTextCopy.browserQueryLabel());
        assertEquals("deck:Japanese tag:kani", SettingsTextCopy.ankiBrowserQueryHint());
        assertEquals("Anki browser query", SettingsTextCopy.ankiBrowserQueryLabel());
        assertEquals("tag1, tag2", SettingsTextCopy.ankiNoteTagsHint());
        assertEquals("Anki note tags", SettingsTextCopy.ankiNoteTagsLabel());
        assertEquals("FSRS difficulty", SettingsTextCopy.fsrsDifficultyLabel());
        assertEquals("Lapses", SettingsTextCopy.lapsesLabel());
        assertEquals("Minimum matching cards per kanji", SettingsTextCopy.minimumMatchingCardsLabel());
        assertEquals("Save import filters", SettingsTextCopy.saveImportFiltersLabel());
        assertEquals("Enter an Anki browser query or turn off Browser query.", SettingsTextCopy.browserQueryRequiredToast());
        assertEquals("Turn on at least one import source.", SettingsTextCopy.importSourceRequiredToast());
        assertEquals("Import filters saved. Sync again to rebuild practice.", SettingsTextCopy.importFiltersSavedToast());
        assertEquals("Presets", SettingsTextCopy.presetsTitle());
        assertEquals("Import preset saved. Sync again to rebuild practice.", SettingsTextCopy.importPresetSavedToast());
        assertEquals("Use numeric import thresholds.", SettingsTextCopy.numericImportThresholdsToast());
        assertEquals("Use difficulty 1-10, lapses 1-100, and cards 1-1000.", SettingsTextCopy.importThresholdRangeToast());
        assertEquals("Frequency range", SettingsTextCopy.frequencyRangeTitle());
        assertEquals(
                "Suspended cards are imported only when the kanji has a known Jiten rank inside this range. Lower ranks are more common. Default: 100-3000.",
                SettingsTextCopy.frequencyRangeBody()
        );
        assertEquals("Min rank", SettingsTextCopy.minRankLabel());
        assertEquals("Max rank", SettingsTextCopy.maxRankLabel());
        assertEquals("Minimum rank", SettingsTextCopy.minimumRankLabel());
        assertEquals("Maximum rank", SettingsTextCopy.maximumRankLabel());
        assertEquals("Save frequency range", SettingsTextCopy.saveFrequencyRangeLabel());
        assertEquals("Enter numeric ranks.", SettingsTextCopy.numericRanksToast());
        assertEquals("Use ranks from 1 to 20000.", SettingsTextCopy.rankRangeToast());
        assertEquals("Frequency range saved. Sync again to rebuild practice.", SettingsTextCopy.frequencyRangeSavedToast());
        assertEquals("Offline data & licenses", SettingsTextCopy.offlineDataLicensesTitle());
        assertEquals(
                "One reference page covers KANJIDIC2, Jiten rank data, KanjiVG stroke order, and bundled font attribution.",
                SettingsTextCopy.offlineDataLicensesBody()
        );
        assertEquals("Open data licenses", SettingsTextCopy.openDataLicensesLabel());
        assertEquals("Data licenses", SettingsTextCopy.dataLicensesTitle());
        assertEquals("Dictionary and stroke-order data bundled for offline study.", SettingsTextCopy.dataLicensesBody());
        assertEquals("Dictionary data", SettingsTextCopy.dictionaryDataTitle());
        assertEquals("Stroke data", SettingsTextCopy.strokeDataTitle());
        assertEquals("Fonts", SettingsTextCopy.fontsTitle());
        assertEquals("Note type & clue fields", SettingsTextCopy.noteTypeFieldsTitle());
        assertEquals("Using Kiku", SettingsTextCopy.noteTypeUsingText("Kiku"));
        assertEquals(
                "Default: Kiku. This single card owns the note type and all field mapping so clue configuration is not repeated elsewhere.",
                SettingsTextCopy.noteTypeFieldsBody()
        );
        assertEquals("Required fields", SettingsTextCopy.requiredFieldsTitle());
        assertEquals(
                "Expression = kanji source, ExpressionReading = reading, MainDefinition = meaning, Sentence = context, Frequency/FreqSort = metadata.",
                SettingsTextCopy.requiredFieldsBody()
        );
        assertEquals("Expression field", SettingsTextCopy.expressionFieldLabel());
        assertEquals("Reading field", SettingsTextCopy.readingFieldLabel());
        assertEquals("Meaning field", SettingsTextCopy.meaningFieldLabel());
        assertEquals("Sentence field", SettingsTextCopy.sentenceFieldLabel());
        assertEquals("Frequency field", SettingsTextCopy.frequencyFieldLabel());
        assertEquals("Frequency sort field", SettingsTextCopy.frequencySortFieldLabel());
        assertEquals("Choose from AnkiDroid", SettingsTextCopy.chooseFromAnkiDroidLabel());
        assertEquals("Use Kiku", SettingsTextCopy.useKikuLabel());
        assertEquals("Save note type", SettingsTextCopy.saveNoteTypeLabel());
        assertEquals("Enter a note type name.", SettingsTextCopy.noteTypeRequiredToast());
        assertEquals("Choose the field that contains kanji.", SettingsTextCopy.expressionFieldRequiredToast());
        assertEquals("Note type saved. Sync again to rebuild practice.", SettingsTextCopy.noteTypeSavedToast());
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
        assertEquals("Daily workload", SettingsTextCopy.dailyWorkloadTitle());
        assertEquals(
                "Kani automatically chooses where today's problem-kanji priority curve drops off. This changes how much it admits today, not Anki's schedule.",
                SettingsTextCopy.automaticWorkloadBody()
        );
        assertEquals("Save maximum", SettingsTextCopy.saveMaximumLabel());
        assertEquals("Use manual workload", SettingsTextCopy.manualWorkloadLabel());
        assertEquals(
                "Manual workload overrides the automatic Pareto drop-off. This changes how much Kani admits today, not Anki's schedule.",
                SettingsTextCopy.manualWorkloadBody()
        );
        assertEquals(Arrays.asList("Very little", "Pareto", "Balanced", "More", "All kanji"), Arrays.asList(SettingsTextCopy.workloadScaleLabels()));
        assertEquals("Save workload", SettingsTextCopy.saveWorkloadLabel());
        assertEquals("Use automatic Pareto", SettingsTextCopy.automaticParetoLabel());
        assertEquals("Learning steps", SettingsTextCopy.learningStepsTitle());
        assertEquals(
                "New cards and review misses can come back quickly for practice. These repeats do not change Kani's SRS after the first answer.",
                SettingsTextCopy.learningStepsBody()
        );
        assertEquals("Review misses", SettingsTextCopy.reviewMissesLabel());
        assertEquals("Anki default", SettingsTextCopy.ankiDefaultLabel());
        assertEquals("Both 1m 10m", SettingsTextCopy.sameLearningStepsLabel());
        assertEquals("Save learning steps", SettingsTextCopy.saveLearningStepsLabel());
        assertEquals("Learning steps saved.", SettingsTextCopy.learningStepsSavedToast());
        assertEquals("Study ahead", SettingsTextCopy.studyAheadTitle());
        assertEquals(
                "Pull cards becoming due within this many minutes into the queue. Set 0 to disable. Learning step delays still apply normally (just like Anki).",
                SettingsTextCopy.studyAheadBody()
        );
        assertEquals("Save study ahead", SettingsTextCopy.saveStudyAheadLabel());
        assertEquals("Study ahead saved.", SettingsTextCopy.studyAheadSavedToast());
    }

    @Test
    public void newCardSortCopyPreservesModeLabelsAndStatus() {
        assertEquals("Current: Frequency", SettingsTextCopy.newCardSortStatusText(RecordsBase.DEFAULT_NEW_CARD_SORT_MODE));
        assertEquals("Anki difficulty", SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY));
        assertEquals("Retrievability risk", SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK));
        assertEquals("Kani weakness", SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS));
        assertEquals("Frequency", SettingsTextCopy.newCardSortLabel("unknown"));
        assertEquals("Frequency", SettingsTextCopy.newCardSortLabel(null));
        assertEquals("New card sort", SettingsTextCopy.newCardSortTitle());
        assertEquals(
                "Choose how Kani admits and shows unseen new cards. Due reviews and learning repeats still keep their normal priority.",
                SettingsTextCopy.newCardSortBody()
        );
        assertEquals("Save new card sort", SettingsTextCopy.saveNewCardSortLabel());
    }

    @Test
    public void rangeRetentionAndLadderCopyPreserveSettingsLabels() {
        RecordsBase.StudyLadderSettings ladder = RecordsBase.StudyLadderSettings.defaults();

        assertEquals("Jiten ranks 1-20000", SettingsTextCopy.frequencyRangeStatusText(1, 20000));
        assertEquals("Desired retention: 95%", SettingsTextCopy.retentionStatusText(95));
        assertEquals("FSRS retention", SettingsTextCopy.fsrsRetentionTitle());
        assertEquals(
                "Higher retention keeps intervals shorter. This changes Kani's internal FSRS intervals, not Anki's schedule.",
                SettingsTextCopy.fsrsRetentionBody()
        );
        assertEquals("Use Jiten-rank retention ranges", SettingsTextCopy.useJitenRankRetentionRangesLabel());
        assertEquals(
                "Optional: one inclusive Jiten rank range per line, such as 1-500=95%. Unmatched or unranked kanji use the global retention above.",
                SettingsTextCopy.jitenRankRetentionRangesBody()
        );
        assertEquals("Use example ranges", SettingsTextCopy.useExampleRangesLabel());
        assertEquals("Save retention", SettingsTextCopy.saveRetentionLabel());
        assertEquals("Write kanji", SettingsTextCopy.settingsLadderRungLabel(RecordsBase.LadderRung.WRITE_KANJI));
        assertEquals("Similar kanji", SettingsTextCopy.settingsLadderRungLabel(RecordsBase.LadderRung.SIMILAR_KANJI));
        assertEquals("Type the meaning", SettingsTextCopy.settingsLadderRungLabel(RecordsBase.LadderRung.TYPE_MEANING));
        assertEquals("Meaning -> kanji", SettingsTextCopy.settingsLadderRungLabel(RecordsBase.LadderRung.MEANING_KANJI));
        assertEquals("Kanji -> meaning", SettingsTextCopy.settingsLadderRungLabel(RecordsBase.LadderRung.KANJI_MEANING));
        assertEquals("Font -> meaning", SettingsTextCopy.settingsLadderRungLabel(RecordsBase.LadderRung.FONT_MEANING));
        assertEquals("Word -> reading", SettingsTextCopy.settingsLadderRungLabel(RecordsBase.LadderRung.WORD_READING));
        assertEquals("Enabled always available rung", SettingsTextCopy.ladderRungSubtitle(ladder, RecordsBase.LadderRung.WRITE_KANJI));
        assertEquals("Enabled conditional rung", SettingsTextCopy.ladderRungSubtitle(ladder, RecordsBase.LadderRung.SIMILAR_KANJI));
        assertEquals("Study ladder", SettingsTextCopy.studyLadderTitle());
        assertEquals(
                "Turn rungs off or move them up and down. At least one always-available rung stays on.",
                SettingsTextCopy.studyLadderBody()
        );
        assertEquals("On", SettingsTextCopy.ladderToggleLabel(true));
        assertEquals("Off", SettingsTextCopy.ladderToggleLabel(false));
        assertEquals("Up", SettingsTextCopy.moveUpLabel());
        assertEquals("Down", SettingsTextCopy.moveDownLabel());
        assertEquals("Restore default ladder", SettingsTextCopy.restoreDefaultLadderLabel());
        assertEquals("Study ladder restored.", SettingsTextCopy.studyLadderRestoredToast());
        assertEquals("Keep at least one always-available rung on.", SettingsTextCopy.keepAlwaysAvailableRungToast());
        assertEquals("Write kanji off.", SettingsTextCopy.ladderRungToggleToast(RecordsBase.LadderRung.WRITE_KANJI, true));
        assertEquals("Write kanji on.", SettingsTextCopy.ladderRungToggleToast(RecordsBase.LadderRung.WRITE_KANJI, false));
        assertEquals("Ladder thresholds", SettingsTextCopy.ladderThresholdsTitle());
        assertEquals(
                "Recognition rungs climb when a real FSRS-due pass schedules the next review beyond the day threshold. Learning-step repeats stay practice-only.",
                SettingsTextCopy.ladderThresholdsBody()
        );
        assertEquals("FSRS days to go up", SettingsTextCopy.fsrsDaysToGoUpLabel());
        assertEquals("Fails to go down", SettingsTextCopy.failsToGoDownLabel());
        assertEquals("Use 21 and 3", SettingsTextCopy.useDefaultLadderThresholdsLabel());
        assertEquals("Save ladder thresholds", SettingsTextCopy.saveLadderThresholdsLabel());
        assertEquals("Ladder thresholds saved.", SettingsTextCopy.ladderThresholdsSavedToast());
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
