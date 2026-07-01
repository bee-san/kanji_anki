package dev.bee.kanjianki.core

object SettingsTextCopy {

    @JvmStatic
    fun settingsImportSummary(settings: RecordsSyncModels.Settings?): String = SettingsSummaryTextCopy.settingsImportSummary(settings)

    @JvmStatic
    fun matchingCardsSummary(settings: RecordsSyncModels.Settings?): String = SettingsSummaryTextCopy.matchingCardsSummary(settings)

    @JvmStatic
    fun settingsReminderSummary(enabled: Boolean, blocked: Boolean, displayTime: String?): String? = SettingsAutomationTextCopy.settingsReminderSummary(enabled, blocked, displayTime)

    @JvmStatic
    fun settingsAutoSyncSummary(configured: Boolean, enabled: Boolean, displayTime: String?): String? = SettingsAutomationTextCopy.settingsAutoSyncSummary(configured, enabled, displayTime)

    @JvmStatic
    fun settingsUpdateSummary(hasPendingUpdate: Boolean, enabled: Boolean): String = SettingsAutomationTextCopy.settingsUpdateSummary(hasPendingUpdate, enabled)

    @JvmStatic
    fun syncStatusHeadline(success: Boolean, errorMessage: String?, suspendedCards: Int, importedKanji: Int): String = SettingsSummaryTextCopy.syncStatusHeadline(success, errorMessage, suspendedCards, importedKanji)

    @JvmStatic
    fun versionText(version: String?): String = SettingsAutomationTextCopy.versionText(version)

    @JvmStatic
    fun settingsTitle(): String = SettingsSectionTextCopy.settingsTitle()

    @JvmStatic
    fun settingsAnkiSourceTitle(): String = SettingsSectionTextCopy.settingsAnkiSourceTitle()

    @JvmStatic
    fun settingsAnkiSourceBody(): String = SettingsSectionTextCopy.settingsAnkiSourceBody()

    @JvmStatic
    fun settingsStudyBehaviorTitle(): String = SettingsSectionTextCopy.settingsStudyBehaviorTitle()

    @JvmStatic
    fun settingsStudyBehaviorBody(): String = SettingsSectionTextCopy.settingsStudyBehaviorBody()

    @JvmStatic
    fun settingsAutomationTitle(): String = SettingsSectionTextCopy.settingsAutomationTitle()

    @JvmStatic
    fun settingsAutomationBody(): String = SettingsSectionTextCopy.settingsAutomationBody()

    @JvmStatic
    fun settingsAppearanceTitle(): String = SettingsSectionTextCopy.settingsAppearanceTitle()

    @JvmStatic
    fun settingsAppearanceBody(): String = SettingsSectionTextCopy.settingsAppearanceBody()

    @JvmStatic
    fun settingsReferenceDataTitle(): String = SettingsSectionTextCopy.settingsReferenceDataTitle()

    @JvmStatic
    fun settingsReferenceDataBody(): String = SettingsSectionTextCopy.settingsReferenceDataBody()

    @JvmStatic
    fun timingDiagnosticsTitle(): String = SettingsSectionTextCopy.timingDiagnosticsTitle()

    @JvmStatic
    fun timingDiagnosticsBody(): String = SettingsSectionTextCopy.timingDiagnosticsBody()

    @JvmStatic
    fun timingDiagnosticsReportTitle(): String = SettingsSectionTextCopy.timingDiagnosticsReportTitle()

    @JvmStatic
    fun timingDiagnosticsPrewarmTitle(): String = SettingsSectionTextCopy.timingDiagnosticsPrewarmTitle()

    @JvmStatic
    fun timingDiagnosticsPrewarmBody(): String = SettingsSectionTextCopy.timingDiagnosticsPrewarmBody()

    @JvmStatic
    fun timingDiagnosticsResetTitle(): String = SettingsSectionTextCopy.timingDiagnosticsResetTitle()

    @JvmStatic
    fun timingDiagnosticsResetBody(): String = SettingsSectionTextCopy.timingDiagnosticsResetBody()

    @JvmStatic
    fun timingDiagnosticsCopyLabel(): String = SettingsSectionTextCopy.timingDiagnosticsCopyLabel()

    @JvmStatic
    fun timingDiagnosticsResetLabel(): String = SettingsSectionTextCopy.timingDiagnosticsResetLabel()

    @JvmStatic
    fun timingDiagnosticsPrewarmLabel(): String = SettingsSectionTextCopy.timingDiagnosticsPrewarmLabel()

    @JvmStatic
    fun timingDiagnosticsCopiedToast(): String = SettingsSectionTextCopy.timingDiagnosticsCopiedToast()

    @JvmStatic
    fun timingDiagnosticsResetToast(): String = SettingsSectionTextCopy.timingDiagnosticsResetToast()

    @JvmStatic
    fun timingDiagnosticsPrewarmToast(): String = SettingsSectionTextCopy.timingDiagnosticsPrewarmToast()

    @JvmStatic
    fun updatePageTitle(): String = SettingsAutomationTextCopy.updatePageTitle()

    @JvmStatic
    fun updatePageBody(versionName: String?): String = SettingsAutomationTextCopy.updatePageBody(versionName)

    @JvmStatic
    fun automaticUpdatesTitle(): String = SettingsAutomationTextCopy.automaticUpdatesTitle()

    @JvmStatic
    fun checkForUpdateLabel(): String = SettingsAutomationTextCopy.checkForUpdateLabel()

    @JvmStatic
    fun autoUpdatePanelStatus(enabled: Boolean): String = SettingsAutomationTextCopy.autoUpdatePanelStatus(enabled)

    @JvmStatic
    fun autoUpdateLastCheckLine(lastCheckText: String?): String = SettingsAutomationTextCopy.autoUpdateLastCheckLine(lastCheckText)

    @JvmStatic
    fun autoUpdateLastResultLine(lastResult: String?): String = SettingsAutomationTextCopy.autoUpdateLastResultLine(lastResult)

    @JvmStatic
    fun installPermissionLine(canInstall: Boolean): String = SettingsAutomationTextCopy.installPermissionLine(canInstall)

    @JvmStatic
    fun verifiedApkReadyLine(version: String?): String = SettingsAutomationTextCopy.verifiedApkReadyLine(version)

    @JvmStatic
    fun pendingUpdateFallback(): String = SettingsAutomationTextCopy.pendingUpdateFallback()

    @JvmStatic
    fun pendingUpdateFallback(canInstall: Boolean): String = SettingsAutomationTextCopy.pendingUpdateFallback(canInstall)

    @JvmStatic
    fun installVerifiedUpdateLabel(): String = SettingsAutomationTextCopy.installVerifiedUpdateLabel()

    @JvmStatic
    fun setupAppInstallsLabel(): String = SettingsAutomationTextCopy.setupAppInstallsLabel()

    @JvmStatic
    fun automaticUpdatesToggleLabel(enabled: Boolean): String = SettingsAutomationTextCopy.automaticUpdatesToggleLabel(enabled)

    @JvmStatic
    fun backToSettingsLabel(): String = SettingsAutomationTextCopy.backToSettingsLabel()

    fun sectionOpenDescription(title: String): String = SettingsSectionTextCopy.sectionOpenDescription(title)

    @JvmStatic
    fun settingsCockpitLabel(): String = SettingsSectionTextCopy.settingsCockpitLabel()

    @JvmStatic
    fun settingsHeroBody(): String = SettingsSectionTextCopy.settingsHeroBody()

    @JvmStatic
    fun noteTypeStatusLabel(): String = SettingsSectionTextCopy.noteTypeStatusLabel()

    @JvmStatic
    fun importFiltersStatusLabel(): String = SettingsSectionTextCopy.importFiltersStatusLabel()

    @JvmStatic
    fun importRanksStatusLabel(): String = SettingsSectionTextCopy.importRanksStatusLabel()

    @JvmStatic
    fun reminderStatusLabel(): String = SettingsSectionTextCopy.reminderStatusLabel()

    @JvmStatic
    fun dailySyncStatusLabel(): String = SettingsSectionTextCopy.dailySyncStatusLabel()

    @JvmStatic
    fun updatesStatusLabel(): String = SettingsSectionTextCopy.updatesStatusLabel()

    @JvmStatic
    fun matchingCardsStatusLabel(): String = SettingsSectionTextCopy.matchingCardsStatusLabel()

    @JvmStatic
    fun statusPillDescription(label: String, value: String): String = SettingsSectionTextCopy.statusPillDescription(label, value)

    @JvmStatic
    fun categoryToggleDescription(expanded: Boolean, title: String): String = SettingsSectionTextCopy.categoryToggleDescription(expanded, title)

    @JvmStatic
    fun categoryStateDescription(expanded: Boolean): String = SettingsSectionTextCopy.categoryStateDescription(expanded)

    @JvmStatic
    fun settingsCategoryPanelCount(panels: Int): String = SettingsSectionTextCopy.settingsCategoryPanelCount(panels)

    @JvmStatic
    fun importFiltersTitle(): String = SettingsImportFiltersTextCopy.importFiltersTitle()

    @JvmStatic
    fun importFiltersBody(): String = SettingsImportFiltersTextCopy.importFiltersBody()

    @JvmStatic
    fun activeCardsLabel(): String = SettingsImportFiltersTextCopy.activeCardsLabel()

    @JvmStatic
    fun suspendedCardsLabel(): String = SettingsImportFiltersTextCopy.suspendedCardsLabel()

    @JvmStatic
    fun taggedCardsLabel(): String = SettingsImportFiltersTextCopy.taggedCardsLabel()

    @JvmStatic
    fun weakCardsLabel(): String = SettingsImportFiltersTextCopy.weakCardsLabel()

    @JvmStatic
    fun browserQueryLabel(): String = SettingsImportFiltersTextCopy.browserQueryLabel()

    @JvmStatic
    fun ankiBrowserQueryHint(): String = SettingsImportFiltersTextCopy.ankiBrowserQueryHint()

    @JvmStatic
    fun ankiBrowserQueryLabel(): String = SettingsImportFiltersTextCopy.ankiBrowserQueryLabel()

    @JvmStatic
    fun ankiBrowserQueryHelperText(): String = SettingsImportFiltersTextCopy.ankiBrowserQueryHelperText()

    @JvmStatic
    fun ankiNoteTagsHint(): String = SettingsImportFiltersTextCopy.ankiNoteTagsHint()

    @JvmStatic
    fun ankiNoteTagsLabel(): String = SettingsImportFiltersTextCopy.ankiNoteTagsLabel()

    @JvmStatic
    fun fsrsDifficultyLabel(): String = SettingsImportFiltersTextCopy.fsrsDifficultyLabel()

    @JvmStatic
    fun lapsesLabel(): String = SettingsImportFiltersTextCopy.lapsesLabel()

    @JvmStatic
    fun minimumMatchingCardsLabel(): String = SettingsImportFiltersTextCopy.minimumMatchingCardsLabel()

    @JvmStatic
    fun saveImportFiltersLabel(): String = SettingsImportFiltersTextCopy.saveImportFiltersLabel()

    @JvmStatic
    fun browserQueryRequiredToast(): String = SettingsImportFiltersTextCopy.browserQueryRequiredToast()

    @JvmStatic
    fun importSourceRequiredToast(): String = SettingsImportFiltersTextCopy.importSourceRequiredToast()

    @JvmStatic
    fun importFiltersSavedToast(): String = SettingsImportFiltersTextCopy.importFiltersSavedToast()

    @JvmStatic
    fun presetsTitle(): String = SettingsImportFiltersTextCopy.presetsTitle()

    @JvmStatic
    fun importPresetSavedToast(): String = SettingsImportFiltersTextCopy.importPresetSavedToast()

    @JvmStatic
    fun numericImportThresholdsToast(): String = SettingsImportFiltersTextCopy.numericImportThresholdsToast()

    @JvmStatic
    fun importThresholdRangeToast(): String = SettingsImportFiltersTextCopy.importThresholdRangeToast()

    @JvmStatic
    fun frequencyRangeTitle(): String = SettingsReferenceDataTextCopy.frequencyRangeTitle()

    @JvmStatic
    fun frequencyRangeBody(): String = SettingsReferenceDataTextCopy.frequencyRangeBody()

    @JvmStatic
    fun minRankLabel(): String = SettingsReferenceDataTextCopy.minRankLabel()

    @JvmStatic
    fun maxRankLabel(): String = SettingsReferenceDataTextCopy.maxRankLabel()

    @JvmStatic
    fun minimumRankLabel(): String = SettingsReferenceDataTextCopy.minimumRankLabel()

    @JvmStatic
    fun maximumRankLabel(): String = SettingsReferenceDataTextCopy.maximumRankLabel()

    @JvmStatic
    fun saveFrequencyRangeLabel(): String = SettingsReferenceDataTextCopy.saveFrequencyRangeLabel()

    @JvmStatic
    fun numericRanksToast(): String = SettingsReferenceDataTextCopy.numericRanksToast()

    @JvmStatic
    fun rankRangeToast(): String = SettingsReferenceDataTextCopy.rankRangeToast()

    @JvmStatic
    fun frequencyRangeSavedToast(): String = SettingsReferenceDataTextCopy.frequencyRangeSavedToast()

    @JvmStatic
    fun offlineDataLicensesTitle(): String = SettingsReferenceDataTextCopy.offlineDataLicensesTitle()

    @JvmStatic
    fun offlineDataLicensesBody(): String = SettingsReferenceDataTextCopy.offlineDataLicensesBody()

    @JvmStatic
    fun openDataLicensesLabel(): String = SettingsReferenceDataTextCopy.openDataLicensesLabel()

    @JvmStatic
    fun dataLicensesTitle(): String = SettingsReferenceDataTextCopy.dataLicensesTitle()

    @JvmStatic
    fun dataLicensesBody(): String = SettingsReferenceDataTextCopy.dataLicensesBody()

    @JvmStatic
    fun dictionaryDataTitle(): String = SettingsReferenceDataTextCopy.dictionaryDataTitle()

    @JvmStatic
    fun strokeDataTitle(): String = SettingsReferenceDataTextCopy.strokeDataTitle()

    @JvmStatic
    fun fontsTitle(): String = SettingsReferenceDataTextCopy.fontsTitle()

    @JvmStatic
    fun noteTypeFieldsTitle(): String = SettingsNoteTypeTextCopy.noteTypeFieldsTitle()

    @JvmStatic
    fun noteTypeUsingText(modelName: String?): String = SettingsNoteTypeTextCopy.noteTypeUsingText(modelName)

    @JvmStatic
    fun noteTypeFieldsBody(): String = SettingsNoteTypeTextCopy.noteTypeFieldsBody()

    @JvmStatic
    fun requiredFieldsTitle(): String = SettingsNoteTypeTextCopy.requiredFieldsTitle()

    @JvmStatic
    fun requiredFieldsBody(): String = SettingsNoteTypeTextCopy.requiredFieldsBody()

    @JvmStatic
    fun expressionFieldLabel(): String = SettingsNoteTypeTextCopy.expressionFieldLabel()

    @JvmStatic
    fun readingFieldLabel(): String = SettingsNoteTypeTextCopy.readingFieldLabel()

    @JvmStatic
    fun meaningFieldLabel(): String = SettingsNoteTypeTextCopy.meaningFieldLabel()

    @JvmStatic
    fun sentenceFieldLabel(): String = SettingsNoteTypeTextCopy.sentenceFieldLabel()

    @JvmStatic
    fun frequencyFieldLabel(): String = SettingsNoteTypeTextCopy.frequencyFieldLabel()

    @JvmStatic
    fun frequencySortFieldLabel(): String = SettingsNoteTypeTextCopy.frequencySortFieldLabel()

    @JvmStatic
    fun chooseFromAnkiDroidLabel(): String = SettingsNoteTypeTextCopy.chooseFromAnkiDroidLabel()

    @JvmStatic
    fun useKikuLabel(): String = SettingsNoteTypeTextCopy.useKikuLabel()

    @JvmStatic
    fun saveNoteTypeLabel(): String = SettingsNoteTypeTextCopy.saveNoteTypeLabel()

    @JvmStatic
    fun noteTypeRequiredToast(): String = SettingsNoteTypeTextCopy.noteTypeRequiredToast()

    @JvmStatic
    fun expressionFieldRequiredToast(): String = SettingsNoteTypeTextCopy.expressionFieldRequiredToast()

    @JvmStatic
    fun noteTypeSavedToast(): String = SettingsNoteTypeTextCopy.noteTypeSavedToast()

    @JvmStatic
    fun newCardSortTitle(): String = SettingsStudyPlanTextCopy.newCardSortTitle()

    @JvmStatic
    fun newCardSortBody(): String = SettingsStudyPlanTextCopy.newCardSortBody()

    @JvmStatic
    fun saveNewCardSortLabel(): String = SettingsStudyPlanTextCopy.saveNewCardSortLabel()

    @JvmStatic
    fun newCardSortConfusablePreviewWarning(examples: List<String>): String = SettingsStudyPlanTextCopy.newCardSortConfusablePreviewWarning(examples)

    @JvmStatic
    fun newCardSortDescription(mode: String?): String = SettingsStudyPlanTextCopy.newCardSortDescription(mode)

    @JvmStatic
    fun fsrsRetentionTitle(): String = SettingsStudyPlanTextCopy.fsrsRetentionTitle()

    @JvmStatic
    fun fsrsRetentionBody(): String = SettingsStudyPlanTextCopy.fsrsRetentionBody()

    @JvmStatic
    fun useJitenRankRetentionRangesLabel(): String = SettingsStudyPlanTextCopy.useJitenRankRetentionRangesLabel()

    @JvmStatic
    fun jitenRankRetentionRangesBody(): String = SettingsStudyPlanTextCopy.jitenRankRetentionRangesBody()

    @JvmStatic
    fun useExampleRangesLabel(): String = SettingsStudyPlanTextCopy.useExampleRangesLabel()

    @JvmStatic
    fun saveRetentionLabel(): String = SettingsStudyPlanTextCopy.saveRetentionLabel()

    @JvmStatic
    fun retentionPresetLabel(value: Int): String = SettingsStudyPlanTextCopy.retentionPresetLabel(value)

    @JvmStatic
    fun deckLimitsTitle(): String = SettingsStudyPlanTextCopy.deckLimitsTitle()

    @JvmStatic
    fun deckLimitsBody(): String = SettingsStudyPlanTextCopy.deckLimitsBody()

    @JvmStatic
    fun newCardsPerDayLabel(): String = SettingsStudyPlanTextCopy.newCardsPerDayLabel()

    @JvmStatic
    fun saveDeckLimitsLabel(): String = SettingsStudyPlanTextCopy.saveDeckLimitsLabel()

    @JvmStatic
    fun dailyWorkloadTitle(): String = SettingsStudyPlanTextCopy.dailyWorkloadTitle()

    @JvmStatic
    fun automaticWorkloadBody(): String = SettingsStudyPlanTextCopy.automaticWorkloadBody()

    @JvmStatic
    fun saveMaximumLabel(): String = SettingsStudyPlanTextCopy.saveMaximumLabel()

    @JvmStatic
    fun manualWorkloadLabel(): String = SettingsStudyPlanTextCopy.manualWorkloadLabel()

    @JvmStatic
    fun manualWorkloadBody(): String = SettingsStudyPlanTextCopy.manualWorkloadBody()

    @JvmStatic
    fun workloadPercentSliderDescription(): String = SettingsStudyPlanTextCopy.workloadPercentSliderDescription()

    @JvmStatic
    fun maxItemsSliderDescription(): String = SettingsStudyPlanTextCopy.maxItemsSliderDescription()

    @JvmStatic
    fun workloadScaleLabels(): Array<String> = SettingsStudyPlanTextCopy.workloadScaleLabels()

    @JvmStatic
    fun saveWorkloadLabel(): String = SettingsStudyPlanTextCopy.saveWorkloadLabel()

    @JvmStatic
    fun automaticParetoLabel(): String = SettingsStudyPlanTextCopy.automaticParetoLabel()

    @JvmStatic
    fun learningStepsTitle(): String = SettingsLearningTextCopy.learningStepsTitle()

    @JvmStatic
    fun learningStepsBody(): String = SettingsLearningTextCopy.learningStepsBody()

    @JvmStatic
    fun reviewMissesLabel(): String = SettingsLearningTextCopy.reviewMissesLabel()

    @JvmStatic
    fun ankiDefaultLabel(): String = SettingsLearningTextCopy.ankiDefaultLabel()

    @JvmStatic
    fun sameLearningStepsLabel(): String = SettingsLearningTextCopy.sameLearningStepsLabel()

    @JvmStatic
    fun saveLearningStepsLabel(): String = SettingsLearningTextCopy.saveLearningStepsLabel()

    @JvmStatic
    fun learningStepsSavedToast(): String = SettingsLearningTextCopy.learningStepsSavedToast()

    @JvmStatic
    fun studyAheadTitle(): String = SettingsStudyAheadTextCopy.studyAheadTitle()

    @JvmStatic
    fun studyAheadBody(): String = SettingsStudyAheadTextCopy.studyAheadBody()

    @JvmStatic
    fun saveStudyAheadLabel(): String = SettingsStudyAheadTextCopy.saveStudyAheadLabel()

    @JvmStatic
    fun studyAheadSavedToast(): String = SettingsStudyAheadTextCopy.studyAheadSavedToast()

    @JvmStatic
    fun ladderThresholdsTitle(): String = SettingsLadderThresholdTextCopy.ladderThresholdsTitle()

    @JvmStatic
    fun ladderThresholdsBody(): String = SettingsLadderThresholdTextCopy.ladderThresholdsBody()

    @JvmStatic
    fun fsrsDaysToGoUpLabel(): String = SettingsLadderThresholdTextCopy.fsrsDaysToGoUpLabel()

    @JvmStatic
    fun failsToGoDownLabel(): String = SettingsLadderThresholdTextCopy.failsToGoDownLabel()

    @JvmStatic
    fun useDefaultLadderThresholdsLabel(): String = SettingsLadderThresholdTextCopy.useDefaultLadderThresholdsLabel()

    @JvmStatic
    fun saveLadderThresholdsLabel(): String = SettingsLadderThresholdTextCopy.saveLadderThresholdsLabel()

    @JvmStatic
    fun ladderThresholdsSavedToast(): String = SettingsLadderThresholdTextCopy.ladderThresholdsSavedToast()

    @JvmStatic
    fun dailyAnkiSyncTitle(): String = SettingsAutomationTextCopy.dailyAnkiSyncTitle()

    @JvmStatic
    fun turnOffDailySyncLabel(): String = SettingsAutomationTextCopy.turnOffDailySyncLabel()

    @JvmStatic
    fun turnOnDailySyncLabel(): String = SettingsAutomationTextCopy.turnOnDailySyncLabel()

    @JvmStatic
    fun appUpdatesTitle(): String = SettingsAutomationTextCopy.appUpdatesTitle()

    @JvmStatic
    fun openUpdaterLabel(): String = SettingsAutomationTextCopy.openUpdaterLabel()

    @JvmStatic
    fun autoSyncStatus(configured: Boolean, enabled: Boolean, displayTime: String?): String = SettingsAutomationTextCopy.autoSyncStatus(configured, enabled, displayTime)

    @JvmStatic
    fun autoSyncDetail(configured: Boolean, enabled: Boolean, lastSuccessText: String, lastAttemptText: String, nextRunText: String): String = SettingsAutomationTextCopy.autoSyncDetail(configured, enabled, lastSuccessText, lastAttemptText, nextRunText)

    @JvmStatic
    fun workloadStatusText(percent: Int, maxItems: Int): String = SettingsStudyPlanTextCopy.workloadStatusText(percent, maxItems)

    @JvmStatic
    fun maxItemsStatusText(maxItems: Int): String = SettingsStudyPlanTextCopy.maxItemsStatusText(maxItems)

    @JvmStatic
    fun autoWorkloadStatusText(plan: RecordsSchedulerModels.AdaptiveLoadPlan?): String = SettingsStudyPlanTextCopy.autoWorkloadStatusText(plan)

    @JvmStatic
    fun newCardSortStatusText(mode: String?): String = SettingsStudyPlanTextCopy.newCardSortStatusText(mode)

    @JvmStatic
    fun newCardSortLabel(mode: String?): String = SettingsStudyPlanTextCopy.newCardSortLabel(mode)

    @JvmStatic
    fun frequencyRangeStatusText(minRank: Int, maxRank: Int): String = SettingsStudyPlanTextCopy.frequencyRangeStatusText(minRank, maxRank)

    @JvmStatic
    fun retentionStatusText(retentionPercent: Int): String = SettingsStudyPlanTextCopy.retentionStatusText(retentionPercent)

    @JvmStatic
    fun studyLadderTitle(): String = SettingsStudyPlanTextCopy.studyLadderTitle()

    @JvmStatic
    fun studyLadderBody(): String = SettingsStudyPlanTextCopy.studyLadderBody()

    @JvmStatic
    fun ladderToggleLabel(enabled: Boolean): String = SettingsStudyPlanTextCopy.ladderToggleLabel(enabled)

    @JvmStatic
    fun moveUpLabel(): String = SettingsStudyPlanTextCopy.moveUpLabel()

    @JvmStatic
    fun moveDownLabel(): String = SettingsStudyPlanTextCopy.moveDownLabel()

    @JvmStatic
    fun restoreDefaultLadderLabel(): String = SettingsStudyPlanTextCopy.restoreDefaultLadderLabel()

    @JvmStatic
    fun studyLadderRestoredToast(): String = SettingsStudyPlanTextCopy.studyLadderRestoredToast()

    @JvmStatic
    fun keepAlwaysAvailableRungToast(): String = SettingsStudyPlanTextCopy.keepAlwaysAvailableRungToast()

    @JvmStatic
    fun ladderRungToggleToast(rung: RecordsBase.LadderRung, wasEnabled: Boolean): String = SettingsStudyPlanTextCopy.ladderRungToggleToast(rung, wasEnabled)

    @JvmStatic
    fun ladderRungSubtitle(ladder: RecordsBase.StudyLadderSettings, rung: RecordsBase.LadderRung): String = SettingsStudyPlanTextCopy.ladderRungSubtitle(ladder, rung)

    @JvmStatic
    fun settingsLadderRungLabel(rung: RecordsBase.LadderRung): String = SettingsStudyPlanTextCopy.settingsLadderRungLabel(rung)

    @JvmStatic
    fun reminderStatus(enabled: Boolean, blocked: Boolean, displayTime: String?): String = SettingsAutomationTextCopy.reminderStatus(enabled, blocked, displayTime)

    @JvmStatic
    fun dailyReminderTitle(): String = SettingsAutomationTextCopy.dailyReminderTitle()

    @JvmStatic
    fun dailyReminderBody(): String = SettingsAutomationTextCopy.dailyReminderBody()

    @JvmStatic
    fun morningReminderPresetLabel(): String = SettingsAutomationTextCopy.morningReminderPresetLabel()

    @JvmStatic
    fun lunchReminderPresetLabel(): String = SettingsAutomationTextCopy.lunchReminderPresetLabel()

    @JvmStatic
    fun eveningReminderPresetLabel(): String = SettingsAutomationTextCopy.eveningReminderPresetLabel()

    @JvmStatic
    fun nightReminderPresetLabel(): String = SettingsAutomationTextCopy.nightReminderPresetLabel()

    @JvmStatic
    fun saveReminderLabel(): String = SettingsAutomationTextCopy.saveReminderLabel()

    @JvmStatic
    fun enableReminderLabel(): String = SettingsAutomationTextCopy.enableReminderLabel()

    @JvmStatic
    fun turnOffReminderLabel(): String = SettingsAutomationTextCopy.turnOffReminderLabel()

    @JvmStatic
    fun notificationsBlockedBody(): String = SettingsAutomationTextCopy.notificationsBlockedBody()

    @JvmStatic
    fun openNotificationSettingsLabel(): String = SettingsAutomationTextCopy.openNotificationSettingsLabel()

    @JvmStatic
    fun notificationPermissionBody(): String = SettingsAutomationTextCopy.notificationPermissionBody()

    @JvmStatic
    fun reminderTime(hour: Int, minute: Int): String = SettingsAutomationTextCopy.reminderTime(hour, minute)

    @JvmStatic
    fun reminderTimeButtonLabel(hour: Int, minute: Int): String = SettingsAutomationTextCopy.reminderTimeButtonLabel(hour, minute)

    @JvmStatic
    fun reminderPresetButtonLabel(label: String?, hour: Int, minute: Int): String = SettingsAutomationTextCopy.reminderPresetButtonLabel(label, hour, minute)

    @JvmStatic
    fun studyAheadMinutesLabel(): String = SettingsStudyAheadTextCopy.studyAheadMinutesLabel()

    @JvmStatic
    fun studyAheadMinutesRange(): String = SettingsStudyAheadTextCopy.studyAheadMinutesRange()

    @JvmStatic
    fun studyAheadWholeNumberErrorText(): String = SettingsStudyAheadTextCopy.studyAheadWholeNumberErrorText()

    @JvmStatic
    fun studyAheadOutOfRangeErrorText(): String = SettingsStudyAheadTextCopy.studyAheadOutOfRangeErrorText()

    @JvmStatic
    fun studyAheadMaxDescription(): String = SettingsStudyAheadTextCopy.studyAheadMaxDescription()
}
