package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test


class SettingsTextCopyTest {
    @Test
    fun importSummariesPreserveSourceAndMatchingCopy() {
        assertEquals("3 matching cards per kanji", SettingsTextCopy.matchingCardsSummary(settings(true, true, true, true, true, 3)))
        assertEquals("1 matching card per kanji", SettingsTextCopy.matchingCardsSummary(settings(false, true, false, false, false, 1)))
        assertEquals("active + suspended + tagged + weak + query; 3 matching cards per kanji", SettingsTextCopy.settingsImportSummary(settings(true, true, true, true, true, 3)))
        assertEquals("No sources", SettingsTextCopy.settingsImportSummary(settings(false, false, false, false, false, 2)))
        assertThrows(NullPointerException::class.java) { SettingsTextCopy.settingsImportSummary(null) }
        assertThrows(NullPointerException::class.java) { SettingsTextCopy.matchingCardsSummary(null) }
    }

    @Test
    fun settingsStatusSummariesPreserveAutomationCopy() {
        assertEquals(
                listOf(
                        "Blocked",
                        "21:05",
                        "Off",
                        "After first sync",
                        "07:30",
                        "Off",
                        "Verified APK ready",
                        "Automatic checks on",
                        "Manual checks",
                        "4 suspended cards archived, 2 rare kanji added; active cards remain optional",
                        "Sync blocked: No provider",
                        "Sync blocked: null",
                        "unknown version",
                        "unknown version",
                        "0.4.33",
                        "release-v0.4.33",
                        "Import from Anki",
                        "AnkiDroid note type, filters, and frequency.",
                        "Study behavior",
                        "Learning steps, FSRS retention, workload, sorting, ahead limits, and ladder thresholds.",
                        "Automation",
                        "Daily sync, reminders, and update checks that run Kani in the background.",
                        "Display & data",
                        "Offline dictionaries, stroke data, fonts, and attribution."
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
        )
        assertEquals(
                listOf(
                        "GitHub updater",
                        "Version 1.2.3. Checks GitHub Releases, then verifies the APK.",
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
                        "Settings overview",
                        "Grouped by import, study, automation, and display & data.",
                        "Anki note type",
                        "Import filters",
                        "Frequency range",
                        "Daily reminder",
                        "Daily Anki sync",
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
                        "Matching cards",
                        "Reminder: Off",
                        "Collapse Study behavior",
                        "Expand Automation",
                        "1 card",
                        "2 cards",
                        "Starts after first successful sync",
                        "On around 07:30",
                        "Off",
                        "Sync once manually; Kani refreshes daily after that.",
                        "Scheduled daily; Android may batch the time.",
                        "Daily background sync is paused.",
                        "Last auto success yesterday. Last auto attempt today. Next scheduled tomorrow.",
                        "Last auto success yesterday. Last auto attempt today."
                ),
                listOf(
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

        assertTrue(SettingsTextCopy.notificationsBlockedBody().contains("cannot appear"))
        assertTrue(SettingsTextCopy.notificationPermissionBody().contains("permission"))
        assertTrue(SettingsTextCopy.keepAlwaysAvailableRungToast().contains("always-available rung"))
    }

    @Test
    fun importAndFrequencyPanelCopyPreservesLabelsAndToasts() {
        assertEquals(
                listOf(
                        "Import filters",
                        "Suspended cards default. Add active, tagged, or weak only when needed; Kani won't add leech tags.",
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
                        "Enter a query or turn off Browser query.",
                        "Turn on at least one import source.",
                        "Import filters saved. Sync again to rebuild practice.",
                        "Presets",
                        "Import preset saved. Sync again to rebuild practice.",
                        "Use numeric import thresholds.",
                        "Use difficulty 1-10, lapses 1-100, and cards 1-1000.",
                        "Frequency range",
                        "Import suspended cards only inside this Jiten rank range. Default 100-3000.",
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
                        "Use ranks from 1 to 20000.",
                        "Frequency range saved. Sync again to rebuild practice.",
                        "Offline data & licenses",
                        "View KANJIDIC2, Jiten, KanjiVG, and font credits.",
                        "Open data licenses",
                        "Data licenses",
                        "Dictionary and stroke data bundled for offline use.",
                        "Dictionary data",
                        "Stroke data",
                        "Fonts",
                        "Note type & clue fields",
                        "Using Kiku",
                        "Default: Kiku. One card owns the note type and clue mapping.",
                        "Required fields"
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
                        "Expression source, Reading=reading, Meaning=meaning, Sentence=context, Frequency/FreqSort=metadata.",
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
        assertEquals("Pareto: up to 5 items", SettingsTextCopy.workloadStatusText(20, 5))
        assertEquals("All kanji: up to 9 items", SettingsTextCopy.workloadStatusText(100, 9))
        assertEquals("Maximum: 1 item", SettingsTextCopy.maxItemsStatusText(1))
        assertEquals("Auto Pareto: waiting for problem kanji", SettingsTextCopy.autoWorkloadStatusText(null))
        assertEquals(
                "Auto Pareto: 2 items today",
                SettingsTextCopy.autoWorkloadStatusText(RecordsSchedulerModels.AdaptiveLoadPlan(true, 20, 2, 1, listOf("裂", "語"), 0, false, "auto"))
        )
        assertEquals("Maximum: 1 item", SettingsTextCopy.maxItemsStatusText(0))
        assertEquals("Daily workload", SettingsTextCopy.dailyWorkloadTitle())
        assertEquals(
                "Kani picks today's problem-kanji count; Anki due dates stay unchanged.",
                SettingsTextCopy.automaticWorkloadBody()
        )
        assertEquals("Save maximum", SettingsTextCopy.saveMaximumLabel())
        assertEquals("Use manual workload", SettingsTextCopy.manualWorkloadLabel())
        assertEquals(
                "Set today's problem-kanji count; Anki due dates stay unchanged.",
                SettingsTextCopy.manualWorkloadBody()
        )
        assertEquals(listOf("Very little", "Pareto", "Balanced", "More", "All kanji"), SettingsTextCopy.workloadScaleLabels().toList())
        assertEquals("Save workload", SettingsTextCopy.saveWorkloadLabel())
        assertEquals("Use automatic Pareto", SettingsTextCopy.automaticParetoLabel())
        assertEquals("Learning steps", SettingsTextCopy.learningStepsTitle())
        assertEquals(
                "New cards and relearning use short steps. Those repeats are practice only.",
                SettingsTextCopy.learningStepsBody()
        )
        assertEquals("Relearning", SettingsTextCopy.reviewMissesLabel())
        assertEquals("Anki default", SettingsTextCopy.ankiDefaultLabel())
        assertEquals("Use new-card steps", SettingsTextCopy.sameLearningStepsLabel())
        assertEquals("Save learning steps", SettingsTextCopy.saveLearningStepsLabel())
        assertEquals("Learning steps saved.", SettingsTextCopy.learningStepsSavedToast())
        assertEquals("Study ahead", SettingsTextCopy.studyAheadTitle())
        assertEquals(
                "Pull due reviews ahead. 0 disables it; learning/relearning delays still apply.",
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
        assertEquals("Harder Anki cards first.", SettingsTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY))
        assertEquals("Cards most likely to be forgotten first.", SettingsTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK))
        assertEquals("Kanji with weaker Kani history first.", SettingsTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS))
        assertEquals(
                "Mixes Kani weakness, Anki risk, missed examples, and frequency.",
                SettingsTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY)
        )
        assertEquals("Frequency", SettingsTextCopy.newCardSortLabel("unknown"))
        assertEquals("Frequency", SettingsTextCopy.newCardSortLabel(null))
        assertEquals("New card sort", SettingsTextCopy.newCardSortTitle())
        assertEquals(
                "Choose how new cards enter study; due reviews and repeats stay first.",
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
                        "FSRS retention",
                        "Kani FSRS stays local. Anki due dates stay unchanged.",
                        "Use Jiten-rank retention ranges",
                        "Optional: one Jiten rank range per line, like 1-500=95%. Other kanji use global retention.",
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
                        "Always available rung enabled",
                        "Conditional rung enabled",
                        "Study ladder",
                        "Turn rungs on or off, or move them. Keep one always-available rung on."
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
                        "Restore default ladder",
                        "Study ladder restored.",
                        "Keep at least one always-available rung on.",
                        "Write kanji off.",
                        "Write kanji on.",
                        "Ladder thresholds",
                        "Only due reviews move the ladder. Learning and relearning repeats are practice only.",
                        "Promotion interval days",
                        "Demotion fail streak",
                        "Use default ladder thresholds",
                        "Save ladder thresholds",
                        "Ladder thresholds saved."
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
                "Daily nudge for active problem kanji; Android may batch it.",
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
                "Android notifications are off, so this reminder cannot appear yet.",
                SettingsTextCopy.notificationsBlockedBody()
        )
        assertEquals("Open notification settings", SettingsTextCopy.openNotificationSettingsLabel())
        assertEquals(
                "Android asks for notification permission before turning this on.",
                SettingsTextCopy.notificationPermissionBody()
        )
        assertEquals("21:05", SettingsTextCopy.reminderTime(21, 5))
        assertEquals("Reminder time: 21:05", SettingsTextCopy.reminderTimeButtonLabel(21, 5))
        assertEquals("Night 21:05", SettingsTextCopy.reminderPresetButtonLabel("Night", 21, 5))
        assertEquals("Daily Anki sync", SettingsTextCopy.dailyAnkiSyncTitle())
        assertEquals("Turn off daily sync", SettingsTextCopy.turnOffDailySyncLabel())
        assertEquals("Turn on daily sync", SettingsTextCopy.turnOnDailySyncLabel())
        assertEquals("App updates", SettingsTextCopy.appUpdatesTitle())
        assertEquals("Open updater", SettingsTextCopy.openUpdaterLabel())
    }

    @Test
    fun studyAheadCopyPreservesLabelsAndValidationMessages() {
        assertEquals("Minutes (0-1440)", SettingsTextCopy.studyAheadMinutesLabel())
        assertEquals("0-1440", SettingsTextCopy.studyAheadMinutesRange())
        assertEquals("1440 minutes (24h)", SettingsTextCopy.studyAheadMaxDescription())
        assertEquals("Use a whole number of minutes (0-1440).", SettingsTextCopy.studyAheadWholeNumberErrorText())
        assertEquals("Use 0 to disable, or up to 1440 minutes (24h).", SettingsTextCopy.studyAheadOutOfRangeErrorText())
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
