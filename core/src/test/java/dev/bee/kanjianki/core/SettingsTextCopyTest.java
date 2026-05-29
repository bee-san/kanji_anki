package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

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
        assertEquals(
                Arrays.asList(
                        "Blocked",
                        "21:05",
                        "Off",
                        "After first sync",
                        "07:30",
                        "Off",
                        "Verified APK ready",
                        "Automatic checks on",
                        "Manual checks",
                        "4 suspended cards archived, 2 rare kanji added; active cards optional",
                        "Sync blocked: No provider",
                        "Sync blocked: null",
                        "unknown version",
                        "unknown version",
                        "0.4.33",
                        "release-v0.4.33",
                        "Anki source",
                        "What Kani reads from AnkiDroid, and which cards become practice.",
                        "Study behavior",
                        "How much appears today, how quickly repeats return, and when cards move rungs.",
                        "Automation",
                        "Background nudges, daily AnkiDroid refreshes, and app update checks.",
                        "Reference data",
                        "Offline dictionaries, frequency ranks, stroke data, fonts, and attribution."
                ),
                Arrays.asList(
                        SettingsTextCopy.settingsReminderSummary(true, true, "21:05"),
                        SettingsTextCopy.settingsReminderSummary(true, false, "21:05"),
                        SettingsTextCopy.settingsReminderSummary(false, false, "21:05"),
                        SettingsTextCopy.settingsAutoSyncSummary(false, true, "07:30"),
                        SettingsTextCopy.settingsAutoSyncSummary(true, true, "07:30"),
                        SettingsTextCopy.settingsAutoSyncSummary(true, false, "07:30"),
                        SettingsTextCopy.settingsUpdateSummary(true, false),
                        SettingsTextCopy.settingsUpdateSummary(false, true),
                        SettingsTextCopy.settingsUpdateSummary(false, false),
                        SettingsTextCopy.syncStatusHeadline(true, "ignored", 4, 2),
                        SettingsTextCopy.syncStatusHeadline(false, "No provider", 0, 0),
                        SettingsTextCopy.syncStatusHeadline(false, null, 0, 0),
                        SettingsTextCopy.versionText(null),
                        SettingsTextCopy.versionText("  "),
                        SettingsTextCopy.versionText("v0.4.33"),
                        SettingsTextCopy.versionText("release-v0.4.33"),
                        SettingsTextCopy.settingsAnkiSourceTitle(),
                        SettingsTextCopy.settingsAnkiSourceBody(),
                        SettingsTextCopy.settingsStudyBehaviorTitle(),
                        SettingsTextCopy.settingsStudyBehaviorBody(),
                        SettingsTextCopy.settingsAutomationTitle(),
                        SettingsTextCopy.settingsAutomationBody(),
                        SettingsTextCopy.settingsReferenceDataTitle(),
                        SettingsTextCopy.settingsReferenceDataBody()
                )
        );
        assertEquals(
                Arrays.asList(
                        "GitHub updater",
                        "Current version 1.2.3. Checks GitHub Releases, verifies the APK, and asks Android to install it.",
                        "Automatic updates",
                        "Check for update",
                        "On: checks about once a day",
                        "Off",
                        "Last check: not yet",
                        "Last result: none",
                        "Install permission: Ready",
                        "Install permission: Missing",
                        "Verified APK ready: 0.4.33",
                        "Android needs confirmation before Kani can replace itself.",
                        "Install verified update",
                        "Set up app installs",
                        "Turn off automatic updates",
                        "Turn on automatic updates",
                        "Back to settings",
                        "Settings cockpit",
                        "Grouped by outcome: source data, study behavior, automation, and offline references. Each setting appears once, next to the thing it changes.",
                        "Note type",
                        "Import filters",
                        "Import ranks",
                        "Reminder",
                        "Daily sync",
                        "Updates"
                ),
                Arrays.asList(
                        SettingsTextCopy.updatePageTitle(),
                        SettingsTextCopy.updatePageBody("1.2.3"),
                        SettingsTextCopy.automaticUpdatesTitle(),
                        SettingsTextCopy.checkForUpdateLabel(),
                        SettingsTextCopy.autoUpdatePanelStatus(true),
                        SettingsTextCopy.autoUpdatePanelStatus(false),
                        SettingsTextCopy.autoUpdateLastCheckLine("not yet"),
                        SettingsTextCopy.autoUpdateLastResultLine("none"),
                        SettingsTextCopy.installPermissionLine(true),
                        SettingsTextCopy.installPermissionLine(false),
                        SettingsTextCopy.verifiedApkReadyLine("v0.4.33"),
                        SettingsTextCopy.pendingUpdateFallback(),
                        SettingsTextCopy.installVerifiedUpdateLabel(),
                        SettingsTextCopy.setupAppInstallsLabel(),
                        SettingsTextCopy.automaticUpdatesToggleLabel(true),
                        SettingsTextCopy.automaticUpdatesToggleLabel(false),
                        SettingsTextCopy.backToSettingsLabel(),
                        SettingsTextCopy.settingsCockpitLabel(),
                        SettingsTextCopy.settingsHeroBody(),
                        SettingsTextCopy.noteTypeStatusLabel(),
                        SettingsTextCopy.importFiltersStatusLabel(),
                        SettingsTextCopy.importRanksStatusLabel(),
                        SettingsTextCopy.reminderStatusLabel(),
                        SettingsTextCopy.dailySyncStatusLabel(),
                        SettingsTextCopy.updatesStatusLabel()
                )
        );
        assertEquals(
                Arrays.asList(
                        "Matching cards",
                        "Reminder: Off",
                        "Collapse Study behavior",
                        "Expand Automation",
                        "1 card",
                        "2 cards",
                        "Starts after first successful sync",
                        "On around 07:30",
                        "Off",
                        "Manual sync once, then Kani will keep itself refreshed once per day.",
                        "Scheduled once per local day. Android may batch the exact time.",
                        "Daily background sync is paused.",
                        "Last auto success yesterday. Last auto attempt today. Next scheduled tomorrow.",
                        "Last auto success yesterday. Last auto attempt today."
                ),
                Arrays.asList(
                        SettingsTextCopy.matchingCardsStatusLabel(),
                        SettingsTextCopy.statusPillDescription("Reminder", "Off"),
                        SettingsTextCopy.categoryToggleDescription(true, "Study behavior"),
                        SettingsTextCopy.categoryToggleDescription(false, "Automation"),
                        SettingsTextCopy.settingsCategoryPanelCount(1),
                        SettingsTextCopy.settingsCategoryPanelCount(2),
                        SettingsTextCopy.autoSyncStatus(false, true, "07:30"),
                        SettingsTextCopy.autoSyncStatus(true, true, "07:30"),
                        SettingsTextCopy.autoSyncStatus(true, false, "07:30"),
                        SettingsTextCopy.autoSyncDetail(false, true, "", "", ""),
                        SettingsTextCopy.autoSyncDetail(true, true, "", "", ""),
                        SettingsTextCopy.autoSyncDetail(true, false, "", "", "tomorrow"),
                        SettingsTextCopy.autoSyncDetail(true, true, "yesterday", "today", "tomorrow"),
                        SettingsTextCopy.autoSyncDetail(true, false, "yesterday", "today", "tomorrow")
                )
        );
    }

    @Test
    public void importAndFrequencyPanelCopyPreservesLabelsAndToasts() {
        assertEquals(
                Arrays.asList(
                        "Import filters",
                        "Suspended AnkiDroid cards are the default source for Kani practice. Turn on active, tagged, or weak cards only when you want those sources included.",
                        "Active cards",
                        "Suspended cards",
                        "Tagged cards",
                        "Weak cards",
                        "Browser query",
                        "deck:Japanese tag:kani",
                        "Anki browser query",
                        "tag1, tag2",
                        "Anki note tags",
                        "FSRS difficulty",
                        "Lapses",
                        "Minimum matching cards per kanji",
                        "Save import filters",
                        "Enter an Anki browser query or turn off Browser query.",
                        "Turn on at least one import source.",
                        "Import filters saved. Sync again to rebuild practice.",
                        "Presets",
                        "Import preset saved. Sync again to rebuild practice.",
                        "Use numeric import thresholds.",
                        "Use difficulty 1-10, lapses 1-100, and cards 1-1000.",
                        "Frequency range",
                        "Suspended cards are imported only when the kanji has a known Jiten rank inside this range. Lower ranks are more common. Default: 100-3000."
                ),
                Arrays.asList(
                        SettingsTextCopy.importFiltersTitle(),
                        SettingsTextCopy.importFiltersBody(),
                        SettingsTextCopy.activeCardsLabel(),
                        SettingsTextCopy.suspendedCardsLabel(),
                        SettingsTextCopy.taggedCardsLabel(),
                        SettingsTextCopy.weakCardsLabel(),
                        SettingsTextCopy.browserQueryLabel(),
                        SettingsTextCopy.ankiBrowserQueryHint(),
                        SettingsTextCopy.ankiBrowserQueryLabel(),
                        SettingsTextCopy.ankiNoteTagsHint(),
                        SettingsTextCopy.ankiNoteTagsLabel(),
                        SettingsTextCopy.fsrsDifficultyLabel(),
                        SettingsTextCopy.lapsesLabel(),
                        SettingsTextCopy.minimumMatchingCardsLabel(),
                        SettingsTextCopy.saveImportFiltersLabel(),
                        SettingsTextCopy.browserQueryRequiredToast(),
                        SettingsTextCopy.importSourceRequiredToast(),
                        SettingsTextCopy.importFiltersSavedToast(),
                        SettingsTextCopy.presetsTitle(),
                        SettingsTextCopy.importPresetSavedToast(),
                        SettingsTextCopy.numericImportThresholdsToast(),
                        SettingsTextCopy.importThresholdRangeToast(),
                        SettingsTextCopy.frequencyRangeTitle(),
                        SettingsTextCopy.frequencyRangeBody()
                )
        );
        assertEquals(
                Arrays.asList(
                        "Min rank",
                        "Max rank",
                        "Minimum rank",
                        "Maximum rank",
                        "Save frequency range",
                        "Enter numeric ranks.",
                        "Use ranks from 1 to 20000.",
                        "Frequency range saved. Sync again to rebuild practice.",
                        "Offline data & licenses",
                        "One reference page covers KANJIDIC2, Jiten rank data, KanjiVG stroke order, and bundled font attribution.",
                        "Open data licenses",
                        "Data licenses",
                        "Dictionary and stroke-order data bundled for offline study.",
                        "Dictionary data",
                        "Stroke data",
                        "Fonts",
                        "Note type & clue fields",
                        "Using Kiku",
                        "Default: Kiku. This single card owns the note type and all field mapping so clue configuration is not repeated elsewhere.",
                        "Required fields"
                ),
                Arrays.asList(
                        SettingsTextCopy.minRankLabel(),
                        SettingsTextCopy.maxRankLabel(),
                        SettingsTextCopy.minimumRankLabel(),
                        SettingsTextCopy.maximumRankLabel(),
                        SettingsTextCopy.saveFrequencyRangeLabel(),
                        SettingsTextCopy.numericRanksToast(),
                        SettingsTextCopy.rankRangeToast(),
                        SettingsTextCopy.frequencyRangeSavedToast(),
                        SettingsTextCopy.offlineDataLicensesTitle(),
                        SettingsTextCopy.offlineDataLicensesBody(),
                        SettingsTextCopy.openDataLicensesLabel(),
                        SettingsTextCopy.dataLicensesTitle(),
                        SettingsTextCopy.dataLicensesBody(),
                        SettingsTextCopy.dictionaryDataTitle(),
                        SettingsTextCopy.strokeDataTitle(),
                        SettingsTextCopy.fontsTitle(),
                        SettingsTextCopy.noteTypeFieldsTitle(),
                        SettingsTextCopy.noteTypeUsingText("Kiku"),
                        SettingsTextCopy.noteTypeFieldsBody(),
                        SettingsTextCopy.requiredFieldsTitle()
                )
        );
        assertEquals(
                Arrays.asList(
                        "Expression = kanji source, ExpressionReading = reading, MainDefinition = meaning, Sentence = context, Frequency/FreqSort = metadata.",
                        "Expression field",
                        "Reading field",
                        "Meaning field",
                        "Sentence field",
                        "Frequency field",
                        "Frequency sort field",
                        "Choose from AnkiDroid",
                        "Use Kiku",
                        "Save note type",
                        "Enter a note type name.",
                        "Choose the field that contains kanji.",
                        "Note type saved. Sync again to rebuild practice."
                ),
                Arrays.asList(
                        SettingsTextCopy.requiredFieldsBody(),
                        SettingsTextCopy.expressionFieldLabel(),
                        SettingsTextCopy.readingFieldLabel(),
                        SettingsTextCopy.meaningFieldLabel(),
                        SettingsTextCopy.sentenceFieldLabel(),
                        SettingsTextCopy.frequencyFieldLabel(),
                        SettingsTextCopy.frequencySortFieldLabel(),
                        SettingsTextCopy.chooseFromAnkiDroidLabel(),
                        SettingsTextCopy.useKikuLabel(),
                        SettingsTextCopy.saveNoteTypeLabel(),
                        SettingsTextCopy.noteTypeRequiredToast(),
                        SettingsTextCopy.expressionFieldRequiredToast(),
                        SettingsTextCopy.noteTypeSavedToast()
                )
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
        assertEquals("Balanced priority", SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY));
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

        assertEquals(
                Arrays.asList(
                        "Jiten ranks 1-20000",
                        "Desired retention: 95%",
                        "FSRS retention",
                        "Higher retention keeps intervals shorter. This changes Kani's internal FSRS intervals, not Anki's schedule.",
                        "Use Jiten-rank retention ranges",
                        "Optional: one inclusive Jiten rank range per line, such as 1-500=95%. Unmatched or unranked kanji use the global retention above.",
                        "Use example ranges",
                        "Save retention",
                        "95%",
                        "Write kanji",
                        "Similar kanji",
                        "Type the meaning",
                        "Meaning -> kanji",
                        "Kanji -> meaning",
                        "Font -> meaning",
                        "Word -> reading",
                        "Enabled always available rung",
                        "Enabled conditional rung",
                        "Study ladder",
                        "Turn rungs off or move them up and down. At least one always-available rung stays on."
                ),
                Arrays.asList(
                        SettingsTextCopy.frequencyRangeStatusText(1, 20000),
                        SettingsTextCopy.retentionStatusText(95),
                        SettingsTextCopy.fsrsRetentionTitle(),
                        SettingsTextCopy.fsrsRetentionBody(),
                        SettingsTextCopy.useJitenRankRetentionRangesLabel(),
                        SettingsTextCopy.jitenRankRetentionRangesBody(),
                        SettingsTextCopy.useExampleRangesLabel(),
                        SettingsTextCopy.saveRetentionLabel(),
                        SettingsTextCopy.retentionPresetLabel(95),
                        SettingsTextCopy.settingsLadderRungLabel(RecordsBase.LadderRung.WRITE_KANJI),
                        SettingsTextCopy.settingsLadderRungLabel(RecordsBase.LadderRung.SIMILAR_KANJI),
                        SettingsTextCopy.settingsLadderRungLabel(RecordsBase.LadderRung.TYPE_MEANING),
                        SettingsTextCopy.settingsLadderRungLabel(RecordsBase.LadderRung.MEANING_KANJI),
                        SettingsTextCopy.settingsLadderRungLabel(RecordsBase.LadderRung.KANJI_MEANING),
                        SettingsTextCopy.settingsLadderRungLabel(RecordsBase.LadderRung.FONT_MEANING),
                        SettingsTextCopy.settingsLadderRungLabel(RecordsBase.LadderRung.WORD_READING),
                        SettingsTextCopy.ladderRungSubtitle(ladder, RecordsBase.LadderRung.WRITE_KANJI),
                        SettingsTextCopy.ladderRungSubtitle(ladder, RecordsBase.LadderRung.SIMILAR_KANJI),
                        SettingsTextCopy.studyLadderTitle(),
                        SettingsTextCopy.studyLadderBody()
                )
        );
        assertEquals(
                Arrays.asList(
                        "On",
                        "Off",
                        "Up",
                        "Down",
                        "Restore default ladder",
                        "Study ladder restored.",
                        "Keep at least one always-available rung on.",
                        "Write kanji off.",
                        "Write kanji on.",
                        "Ladder thresholds",
                        "Recognition rungs climb when a real FSRS-due pass schedules the next review beyond the day threshold. Learning-step repeats stay practice-only.",
                        "FSRS days to go up",
                        "Fails to go down",
                        String.format(
                                Locale.ROOT,
                                "Use %d and %d",
                                RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS,
                                RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK
                        ),
                        "Save ladder thresholds",
                        "Ladder thresholds saved."
                ),
                Arrays.asList(
                        SettingsTextCopy.ladderToggleLabel(true),
                        SettingsTextCopy.ladderToggleLabel(false),
                        SettingsTextCopy.moveUpLabel(),
                        SettingsTextCopy.moveDownLabel(),
                        SettingsTextCopy.restoreDefaultLadderLabel(),
                        SettingsTextCopy.studyLadderRestoredToast(),
                        SettingsTextCopy.keepAlwaysAvailableRungToast(),
                        SettingsTextCopy.ladderRungToggleToast(RecordsBase.LadderRung.WRITE_KANJI, true),
                        SettingsTextCopy.ladderRungToggleToast(RecordsBase.LadderRung.WRITE_KANJI, false),
                        SettingsTextCopy.ladderThresholdsTitle(),
                        SettingsTextCopy.ladderThresholdsBody(),
                        SettingsTextCopy.fsrsDaysToGoUpLabel(),
                        SettingsTextCopy.failsToGoDownLabel(),
                        SettingsTextCopy.useDefaultLadderThresholdsLabel(),
                        SettingsTextCopy.saveLadderThresholdsLabel(),
                        SettingsTextCopy.ladderThresholdsSavedToast()
                )
        );
        assertThrows(NullPointerException.class, () -> SettingsTextCopy.settingsLadderRungLabel(null));
    }

    @Test
    public void reminderCopyPreservesPanelStatusAndTimeFormatting() {
        assertEquals("Daily reminder", SettingsTextCopy.dailyReminderTitle());
        assertEquals(
                "Kani can nudge you once a day to study active problem kanji. Reminder timing is approximate because Android may batch background work.",
                SettingsTextCopy.dailyReminderBody()
        );
        assertEquals("Blocked: notifications off", SettingsTextCopy.reminderStatus(true, true, "21:05"));
        assertEquals("Daily around 21:05", SettingsTextCopy.reminderStatus(true, false, "21:05"));
        assertEquals("Off", SettingsTextCopy.reminderStatus(false, false, "21:05"));
        assertEquals("Morning", SettingsTextCopy.morningReminderPresetLabel());
        assertEquals("Lunch", SettingsTextCopy.lunchReminderPresetLabel());
        assertEquals("Evening", SettingsTextCopy.eveningReminderPresetLabel());
        assertEquals("Night", SettingsTextCopy.nightReminderPresetLabel());
        assertEquals("Save reminder", SettingsTextCopy.saveReminderLabel());
        assertEquals("Enable reminder", SettingsTextCopy.enableReminderLabel());
        assertEquals("Turn off reminder", SettingsTextCopy.turnOffReminderLabel());
        assertEquals(
                "Android notifications are off for Kani, so this reminder cannot appear yet.",
                SettingsTextCopy.notificationsBlockedBody()
        );
        assertEquals("Open notification settings", SettingsTextCopy.openNotificationSettingsLabel());
        assertEquals(
                "Android will ask for notification permission before turning this on.",
                SettingsTextCopy.notificationPermissionBody()
        );
        assertEquals("21:05", SettingsTextCopy.reminderTime(21, 5));
        assertEquals("Reminder time: 21:05", SettingsTextCopy.reminderTimeButtonLabel(21, 5));
        assertEquals("Night 21:05", SettingsTextCopy.reminderPresetButtonLabel("Night", 21, 5));
        assertEquals("Daily Anki sync", SettingsTextCopy.dailyAnkiSyncTitle());
        assertEquals("Turn off daily sync", SettingsTextCopy.turnOffDailySyncLabel());
        assertEquals("Turn on daily sync", SettingsTextCopy.turnOnDailySyncLabel());
        assertEquals("App updates", SettingsTextCopy.appUpdatesTitle());
        assertEquals("Open updater", SettingsTextCopy.openUpdaterLabel());
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
