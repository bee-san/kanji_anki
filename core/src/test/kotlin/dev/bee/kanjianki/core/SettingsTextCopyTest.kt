package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test


class SettingsTextCopyTest {
    @Test
    fun importSummariesPreserveSourceAndMatchingCopy() {
        assertEquals("3+ cards per kanji", SettingsTextCopy.matchingCardsSummary(settings(true, true, true, true, true, 3)))
        assertEquals("1+ card per kanji", SettingsTextCopy.matchingCardsSummary(settings(false, true, false, false, false, 1)))
        assertEquals("active + suspended + tagged + weak + browser query; 3+ cards per kanji", SettingsTextCopy.settingsImportSummary(settings(true, true, true, true, true, 3)))
        assertEquals("Choose an import source", SettingsTextCopy.settingsImportSummary(settings(false, false, false, false, false, 2)))
        assertThrows(NullPointerException::class.java) { SettingsTextCopy.settingsImportSummary(null) }
        assertThrows(NullPointerException::class.java) { SettingsTextCopy.matchingCardsSummary(null) }
    }

    @Test
    fun settingsStatusSummariesPreserveAutomationCopy() {
        assertEquals(
                listOf(
                        "Notifications off",
                        "21:05",
                        "Off",
                        "After first sync",
                        "07:30",
                        "Off",
                        "Ready to install",
                        "On: daily checks",
                        "Off",
                        "4 suspended cards archived, 2 rare kanji added",
                        "Sync blocked: No provider",
                        "Sync blocked: unknown error",
                        "unknown version",
                        "unknown version",
                        "0.4.33",
                        "release-v0.4.33",
                        "Import & sync",
                        "Fields, filters, range, sync.",
                        "Study settings",
                        "New cards, timing, workload, ladder.",
                        "Automation",
                        "Reminders, sync, updates.",
                        "Display & data",
                        "Dictionaries, stroke data, fonts, credits.",
                ),
                listOf(
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
                        SettingsTextCopy.syncStatusHeadline(false, "unknown error", 0, 0),
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
        )
        assertEquals(
                listOf(
                        "App updates",
                        "Version 1.2.3. Checks releases and verifies the APK.",
                        "Automatic updates",
                        "Check for updates",
                        "On: daily checks",
                        "Off",
                        "Last check: not yet",
                        "Last result: none",
                        "Permission granted",
                        "Permission missing",
                        "Ready to install: 0.4.33",
                        "Android needs permission to install updates.",
                        "Install verified update",
                        "Allow app installs",
                        "Turn off updates",
                        "Turn on updates",
                        "Back to settings",
                        "Settings overview",
                        "Choose a section to edit.",
                        "Note type",
                        "Import filters",
                        "Suspended card range",
                        "Daily reminder",
                        "Daily sync",
                        "App updates"
                ),
                listOf(
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
        )
        assertEquals(
                listOf(
                        "Cards per kanji",
                        "Reminder: Off",
                        "Collapse Study settings",
                        "Expand Automation",
                        "1 card",
                        "2 cards",
                        "Starts after first sync",
                        "On around 07:30",
                        "Off",
                        "Sync once. Kani handles daily syncs.",
                        "Scheduled daily. Android may delay it.",
                        "Daily sync paused.",
                        "Last sync: yesterday. Last attempt: today. Next: tomorrow.",
                        "Last sync: yesterday. Last attempt: today.",
                ),
                listOf(
                        SettingsTextCopy.matchingCardsStatusLabel(),
                        SettingsTextCopy.statusPillDescription("Reminder", "Off"),
                        SettingsTextCopy.categoryToggleDescription(true, "Study settings"),
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
        )
    }

    @Test
    fun settingsPanelBodiesStayScannable() {
        for (body in listOf(
                SettingsTextCopy.settingsAnkiSourceBody(),
                SettingsTextCopy.settingsStudyBehaviorBody(),
                SettingsTextCopy.settingsAutomationBody(),
                SettingsTextCopy.settingsHeroBody(),
                SettingsTextCopy.importFiltersBody(),
                SettingsTextCopy.frequencyRangeBody(),
                SettingsTextCopy.offlineDataLicensesBody(),
                SettingsTextCopy.automaticWorkloadBody(),
                SettingsTextCopy.manualWorkloadBody(),
                SettingsTextCopy.studyLadderBody(),
                SettingsTextCopy.autoSyncDetail(false, true, "", "", ""),
                SettingsTextCopy.autoSyncDetail(true, true, "", "", ""),
                SettingsTextCopy.updatePageBody("1.2.3"),
                SettingsTextCopy.dailyReminderBody(),
                SettingsTextCopy.studyAheadBody(),
                SettingsTextCopy.ladderThresholdsBody()
        )) {
            assertTrue(body, body.length <= 100)
        }

        assertTrue(SettingsTextCopy.notificationsBlockedBody().contains("notifications"))
        assertTrue(SettingsTextCopy.notificationPermissionBody().contains("permission"))
        assertTrue(SettingsTextCopy.keepAlwaysAvailableRungToast().contains("always-available rung"))
    }

    @Test
    fun importAndFrequencyPanelCopyPreservesLabelsAndToasts() {
        assertEquals(
                listOf(
                        "Import filters",
                        "Pick sources, save, then sync. Leeches stay excluded.",
                        "Include active cards",
                        "Include suspended cards",
                        "Include tagged cards",
                        "Include weak cards",
                        "Include browser query results",
                        "deck:Japanese tag:kani",
                        "Browser query",
                        "Examples: is:suspended, rated:31:1, tag:kani.",
                        "tag1, tag2",
                        "Tags to include",
                        "Minimum FSRS difficulty",
                        "Minimum lapses",
                        "Matching cards per kanji",
                        "Save import filters",
                        "Add a browser query or turn it off.",
                        "Turn on at least one source.",
                        "Filters saved. Sync to refresh practice.",
                        "Presets",
                        "Preset saved. Sync to refresh practice.",
                        "Enter numeric thresholds.",
                        "Difficulty 1-10, lapses 1-100, cards 1-1000.",
                        "Frequency range",
                        "Suspended cards. Default: 100-3000.",
                ),
                listOf(
                        SettingsTextCopy.importFiltersTitle(),
                        SettingsTextCopy.importFiltersBody(),
                        SettingsTextCopy.activeCardsLabel(),
                        SettingsTextCopy.suspendedCardsLabel(),
                        SettingsTextCopy.taggedCardsLabel(),
                        SettingsTextCopy.weakCardsLabel(),
                        SettingsTextCopy.browserQueryLabel(),
                        SettingsTextCopy.ankiBrowserQueryHint(),
                        SettingsTextCopy.ankiBrowserQueryLabel(),
                        SettingsTextCopy.ankiBrowserQueryHelperText(),
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
        )
        assertEquals(
                listOf(
                        "Min rank",
                        "Max rank",
                        "Minimum rank",
                        "Maximum rank",
                        "Save frequency range",
                        "Enter numeric ranks.",
                        "Ranks must be 1-20000.",
                        "Range saved. Sync to rebuild practice.",
                        "Offline data licenses",
                        "KANJIDIC2, Jiten, KanjiVG, font credits.",
                        "Open data licenses",
                        "Data licenses",
                        "Bundled dictionary and stroke data.",
                        "Dictionary data",
                        "Stroke data",
                        "Fonts",
                        "Note type",
                        "Using Kiku",
                        "Default: Kiku. Map fields.",
                        "Fields"
                ),
                listOf(
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
        )
        assertEquals(
                listOf(
                        "Map expression, reading, meaning, sentence, frequency, sort.",
                        "Expression field",
                        "Reading field",
                        "Meaning field",
                        "Sentence field",
                        "Frequency field",
                        "Frequency sort field",
                        "Choose note type",
                        "Use Kiku",
                        "Save note type",
                        "Enter a note type name.",
                        "Choose the kanji field.",
                        "Saved. Sync to rebuild practice."
                ),
                listOf(
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
        )
    }

    @Test
    fun workloadSummariesPreserveSettingsCopy() {
        assertEquals("Focused: up to 5 items", SettingsTextCopy.workloadStatusText(20, 5))
        assertEquals("All kanji: up to 9 items", SettingsTextCopy.workloadStatusText(100, 9))
        assertEquals("Maximum: 1 item", SettingsTextCopy.maxItemsStatusText(1))
        assertEquals("Automatic workload: waiting for cards", SettingsTextCopy.autoWorkloadStatusText(null))
        assertEquals(
                "Automatic workload: 2 items today",
                SettingsTextCopy.autoWorkloadStatusText(RecordsSchedulerModels.AdaptiveLoadPlan(true, 20, 2, 1, listOf("裂", "語"), 0, false, "auto"))
        )
        assertEquals("Maximum: 1 item", SettingsTextCopy.maxItemsStatusText(0))
        assertEquals("Daily workload", SettingsTextCopy.dailyWorkloadTitle())
        assertEquals(
                "Kani picks today's workload. Anki due dates stay fixed.",
                SettingsTextCopy.automaticWorkloadBody()
        )
        assertEquals("Save workload", SettingsTextCopy.saveMaximumLabel())
        assertEquals("Set workload manually", SettingsTextCopy.manualWorkloadLabel())
        assertEquals(
                "Set today's workload. Anki due dates stay fixed.",
                SettingsTextCopy.manualWorkloadBody()
        )
        assertEquals(listOf("Very little", "Focused", "Balanced", "More", "All kanji"), SettingsTextCopy.workloadScaleLabels().toList())
        assertEquals("Save workload", SettingsTextCopy.saveWorkloadLabel())
        assertEquals("Use automatic workload", SettingsTextCopy.automaticParetoLabel())
        assertEquals("Learning steps", SettingsTextCopy.learningStepsTitle())
        assertEquals(
                "Set repeat waits. Due reviews still move the ladder.",
                SettingsTextCopy.learningStepsBody()
        )
        assertEquals("After missed reviews", SettingsTextCopy.reviewMissesLabel())
        assertEquals("Use Anki defaults", SettingsTextCopy.ankiDefaultLabel())
        assertEquals("Copy new-card steps", SettingsTextCopy.sameLearningStepsLabel())
        assertEquals("Save learning steps", SettingsTextCopy.saveLearningStepsLabel())
        assertEquals("Steps saved.", SettingsTextCopy.learningStepsSavedToast())
        assertEquals("Study ahead", SettingsTextCopy.studyAheadTitle())
        assertEquals(
                "Show soon-due reviews. Learning waits stay fixed.",
                SettingsTextCopy.studyAheadBody()
        )
        assertEquals("Save study ahead", SettingsTextCopy.saveStudyAheadLabel())
        assertEquals("Study ahead saved.", SettingsTextCopy.studyAheadSavedToast())
    }

    @Test
    fun newCardSortCopyPreservesModeLabelsAndStatus() {
        assertEquals("Current: Frequency", SettingsTextCopy.newCardSortStatusText(RecordsBase.DEFAULT_NEW_CARD_SORT_MODE))
        assertEquals("Anki difficulty", SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY))
        assertEquals("Retrievability risk", SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK))
        assertEquals("Kani weakness", SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS))
        assertEquals("Balanced priority", SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY))
        assertEquals("Jiten frequency first.", SettingsTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_FREQUENCY))
        assertEquals("Harder cards first.", SettingsTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY))
        assertEquals("Most-forgotten cards first.", SettingsTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK))
        assertEquals("Weaker Kani cards first.", SettingsTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS))
        assertEquals(
                "Balances weakness, risk, misses, and frequency.",
                SettingsTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY)
        )
        assertEquals("Frequency", SettingsTextCopy.newCardSortLabel("unknown"))
        assertEquals("Frequency", SettingsTextCopy.newCardSortLabel(null))
        assertEquals("New card sort", SettingsTextCopy.newCardSortTitle())
        assertEquals(
                "Pick new-card order. Due reviews and repeats stay first.",
                SettingsTextCopy.newCardSortBody()
        )
        assertEquals("Save new card sort", SettingsTextCopy.saveNewCardSortLabel())
    }

    @Test
    fun rangeRetentionAndLadderCopyPreserveSettingsLabels() {
        val ladder = RecordsBase.StudyLadderSettings.defaults()

        assertEquals(
                listOf(
                        "Jiten ranks 1-20000",
                        "Desired retention: 95%",
                        "Review retention",
                        "FSRS stays local. Anki due dates stay fixed.",
                        "Jiten-rank retention ranges",
                        "One range per line, e.g. 1-500=95%. Others use global retention.",
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
                        "Always available rung on",
                        "Conditional rung on",
                        "Study ladder",
                        "Order the rungs. Keep one enabled."
                ),
                listOf(
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
        )
        assertEquals(
                listOf(
                        "On",
                        "Off",
                        "Up",
                        "Down",
                        "Restore defaults",
                        "Ladder restored.",
                        "Keep one always-available rung on.",
                        "Write kanji turned off.",
                        "Write kanji turned on.",
                        "Ladder movement",
                        "Due reviews move cards. Repeats stay practice-only.",
                        "Days to move up",
                        "Fails to move down",
                        "Use default movement rules",
                        "Save movement rules",
                        "Movement rules saved."
                ),
                listOf(
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
        )
        assertThrows(NullPointerException::class.java) { SettingsTextCopy.settingsLadderRungLabel(null as RecordsBase.LadderRung) }
    }

    @Test
    fun reminderCopyPreservesPanelStatusAndTimeFormatting() {
        assertEquals("Daily reminder", SettingsTextCopy.dailyReminderTitle())
        assertEquals(
                "Android may delay reminders.",
                SettingsTextCopy.dailyReminderBody()
        )
        assertEquals("Blocked: notifications off", SettingsTextCopy.reminderStatus(true, true, "21:05"))
        assertEquals("Daily around 21:05", SettingsTextCopy.reminderStatus(true, false, "21:05"))
        assertEquals("Off", SettingsTextCopy.reminderStatus(false, false, "21:05"))
        assertEquals("Morning", SettingsTextCopy.morningReminderPresetLabel())
        assertEquals("Lunch", SettingsTextCopy.lunchReminderPresetLabel())
        assertEquals("Evening", SettingsTextCopy.eveningReminderPresetLabel())
        assertEquals("Night", SettingsTextCopy.nightReminderPresetLabel())
        assertEquals("Save reminder", SettingsTextCopy.saveReminderLabel())
        assertEquals("Enable reminder", SettingsTextCopy.enableReminderLabel())
        assertEquals("Turn off reminder", SettingsTextCopy.turnOffReminderLabel())
        assertEquals(
                "Turn on notifications to get this reminder.",
                SettingsTextCopy.notificationsBlockedBody()
        )
        assertEquals("Open notification settings", SettingsTextCopy.openNotificationSettingsLabel())
        assertEquals(
                "Grant notification permission.",
                SettingsTextCopy.notificationPermissionBody()
        )
        assertEquals("21:05", SettingsTextCopy.reminderTime(21, 5))
        assertEquals("Reminder time: 21:05", SettingsTextCopy.reminderTimeButtonLabel(21, 5))
        assertEquals("Night 21:05", SettingsTextCopy.reminderPresetButtonLabel("Night", 21, 5))
        assertEquals("Daily sync", SettingsTextCopy.dailyAnkiSyncTitle())
        assertEquals("Turn off daily sync", SettingsTextCopy.turnOffDailySyncLabel())
        assertEquals("Turn on daily sync", SettingsTextCopy.turnOnDailySyncLabel())
        assertEquals("App updates", SettingsTextCopy.appUpdatesTitle())
        assertEquals("Open updater", SettingsTextCopy.openUpdaterLabel())
    }

    @Test
    fun studyAheadCopyPreservesLabelsAndValidationMessages() {
        assertEquals("Minutes ahead (0-1440)", SettingsTextCopy.studyAheadMinutesLabel())
        assertEquals("0-1440", SettingsTextCopy.studyAheadMinutesRange())
        assertEquals("1440 minutes (24h)", SettingsTextCopy.studyAheadMaxDescription())
        assertEquals("Enter whole minutes from 0-1440.", SettingsTextCopy.studyAheadWholeNumberErrorText())
        assertEquals("Use 0-1440 minutes. 0 turns it off.", SettingsTextCopy.studyAheadOutOfRangeErrorText())
    }

    private fun settings(
        active: Boolean,
        suspended: Boolean,
        tagged: Boolean,
        weak: Boolean,
        query: Boolean,
        matchingCards: Int,
    ): RecordsSyncModels.Settings {
        val defaults = RecordsSyncModels.Settings.kikuDefaults()
        return RecordsSyncModels.Settings(
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
            if (tagged) listOf("leeches") else emptyList(),
            weak,
            defaults.importWeakFsrsDifficultyThreshold,
            defaults.importWeakLapsesThreshold,
            matchingCards,
            query,
            if (query) "deck:Kiku" else "",
            defaults.newCardSortMode,
            defaults.ladderPromotionIntervalDays,
            defaults.ladderDemotionFailStreak,
        )
    }
}
