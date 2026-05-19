package dev.bee.kanjianki.core;

public final class SettingsTextCopy {
    private SettingsTextCopy() {
    }

    public static String settingsImportSummary(RecordsSyncModels.Settings settings) {
        return SettingsSummaryTextCopy.settingsImportSummary(settings);
    }

    public static String matchingCardsSummary(RecordsSyncModels.Settings settings) {
        return SettingsSummaryTextCopy.matchingCardsSummary(settings);
    }

    public static String settingsReminderSummary(boolean enabled, boolean blocked, String displayTime) {
        return SettingsAutomationTextCopy.settingsReminderSummary(enabled, blocked, displayTime);
    }

    public static String settingsAutoSyncSummary(boolean configured, boolean enabled, String displayTime) {
        return SettingsAutomationTextCopy.settingsAutoSyncSummary(configured, enabled, displayTime);
    }

    public static String settingsUpdateSummary(boolean hasPendingUpdate, boolean enabled) {
        return SettingsAutomationTextCopy.settingsUpdateSummary(hasPendingUpdate, enabled);
    }

    public static String syncStatusHeadline(boolean success, String errorMessage, int suspendedCards, int importedKanji) {
        return SettingsSummaryTextCopy.syncStatusHeadline(success, errorMessage, suspendedCards, importedKanji);
    }

    public static String versionText(String version) {
        return SettingsAutomationTextCopy.versionText(version);
    }

    public static String settingsAnkiSourceTitle() {
        return SettingsSectionTextCopy.settingsAnkiSourceTitle();
    }

    public static String settingsAnkiSourceBody() {
        return SettingsSectionTextCopy.settingsAnkiSourceBody();
    }

    public static String settingsStudyBehaviorTitle() {
        return SettingsSectionTextCopy.settingsStudyBehaviorTitle();
    }

    public static String settingsStudyBehaviorBody() {
        return SettingsSectionTextCopy.settingsStudyBehaviorBody();
    }

    public static String settingsAutomationTitle() {
        return SettingsSectionTextCopy.settingsAutomationTitle();
    }

    public static String settingsAutomationBody() {
        return SettingsSectionTextCopy.settingsAutomationBody();
    }

    public static String settingsReferenceDataTitle() {
        return SettingsSectionTextCopy.settingsReferenceDataTitle();
    }

    public static String settingsReferenceDataBody() {
        return SettingsSectionTextCopy.settingsReferenceDataBody();
    }

    public static String updatePageTitle() {
        return SettingsAutomationTextCopy.updatePageTitle();
    }

    public static String updatePageBody(String versionName) {
        return SettingsAutomationTextCopy.updatePageBody(versionName);
    }

    public static String automaticUpdatesTitle() {
        return SettingsAutomationTextCopy.automaticUpdatesTitle();
    }

    public static String checkForUpdateLabel() {
        return SettingsAutomationTextCopy.checkForUpdateLabel();
    }

    public static String autoUpdatePanelStatus(boolean enabled) {
        return SettingsAutomationTextCopy.autoUpdatePanelStatus(enabled);
    }

    public static String autoUpdateLastCheckLine(String lastCheckText) {
        return SettingsAutomationTextCopy.autoUpdateLastCheckLine(lastCheckText);
    }

    public static String autoUpdateLastResultLine(String lastResult) {
        return SettingsAutomationTextCopy.autoUpdateLastResultLine(lastResult);
    }

    public static String installPermissionLine(boolean canInstall) {
        return SettingsAutomationTextCopy.installPermissionLine(canInstall);
    }

    public static String verifiedApkReadyLine(String version) {
        return SettingsAutomationTextCopy.verifiedApkReadyLine(version);
    }

    public static String pendingUpdateFallback() {
        return SettingsAutomationTextCopy.pendingUpdateFallback();
    }

    public static String installVerifiedUpdateLabel() {
        return SettingsAutomationTextCopy.installVerifiedUpdateLabel();
    }

    public static String setupAppInstallsLabel() {
        return SettingsAutomationTextCopy.setupAppInstallsLabel();
    }

    public static String automaticUpdatesToggleLabel(boolean enabled) {
        return SettingsAutomationTextCopy.automaticUpdatesToggleLabel(enabled);
    }

    public static String backToSettingsLabel() {
        return SettingsAutomationTextCopy.backToSettingsLabel();
    }

    public static String settingsCockpitLabel() {
        return SettingsSectionTextCopy.settingsCockpitLabel();
    }

    public static String settingsHeroBody() {
        return SettingsSectionTextCopy.settingsHeroBody();
    }

    public static String noteTypeStatusLabel() {
        return SettingsSectionTextCopy.noteTypeStatusLabel();
    }

    public static String importFiltersStatusLabel() {
        return SettingsSectionTextCopy.importFiltersStatusLabel();
    }

    public static String importRanksStatusLabel() {
        return SettingsSectionTextCopy.importRanksStatusLabel();
    }

    public static String reminderStatusLabel() {
        return SettingsSectionTextCopy.reminderStatusLabel();
    }

    public static String dailySyncStatusLabel() {
        return SettingsSectionTextCopy.dailySyncStatusLabel();
    }

    public static String updatesStatusLabel() {
        return SettingsSectionTextCopy.updatesStatusLabel();
    }

    public static String matchingCardsStatusLabel() {
        return SettingsSectionTextCopy.matchingCardsStatusLabel();
    }

    public static String statusPillDescription(String label, String value) {
        return SettingsSectionTextCopy.statusPillDescription(label, value);
    }

    public static String categoryToggleDescription(boolean expanded, String title) {
        return SettingsSectionTextCopy.categoryToggleDescription(expanded, title);
    }

    public static String settingsCategoryPanelCount(int panels) {
        return SettingsSectionTextCopy.settingsCategoryPanelCount(panels);
    }

    public static String importFiltersTitle() {
        return SettingsImportFiltersTextCopy.importFiltersTitle();
    }

    public static String importFiltersBody() {
        return SettingsImportFiltersTextCopy.importFiltersBody();
    }

    public static String activeCardsLabel() {
        return SettingsImportFiltersTextCopy.activeCardsLabel();
    }

    public static String suspendedCardsLabel() {
        return SettingsImportFiltersTextCopy.suspendedCardsLabel();
    }

    public static String taggedCardsLabel() {
        return SettingsImportFiltersTextCopy.taggedCardsLabel();
    }

    public static String weakCardsLabel() {
        return SettingsImportFiltersTextCopy.weakCardsLabel();
    }

    public static String browserQueryLabel() {
        return SettingsImportFiltersTextCopy.browserQueryLabel();
    }

    public static String ankiBrowserQueryHint() {
        return SettingsImportFiltersTextCopy.ankiBrowserQueryHint();
    }

    public static String ankiBrowserQueryLabel() {
        return SettingsImportFiltersTextCopy.ankiBrowserQueryLabel();
    }

    public static String ankiNoteTagsHint() {
        return SettingsImportFiltersTextCopy.ankiNoteTagsHint();
    }

    public static String ankiNoteTagsLabel() {
        return SettingsImportFiltersTextCopy.ankiNoteTagsLabel();
    }

    public static String fsrsDifficultyLabel() {
        return SettingsImportFiltersTextCopy.fsrsDifficultyLabel();
    }

    public static String lapsesLabel() {
        return SettingsImportFiltersTextCopy.lapsesLabel();
    }

    public static String minimumMatchingCardsLabel() {
        return SettingsImportFiltersTextCopy.minimumMatchingCardsLabel();
    }

    public static String saveImportFiltersLabel() {
        return SettingsImportFiltersTextCopy.saveImportFiltersLabel();
    }

    public static String browserQueryRequiredToast() {
        return SettingsImportFiltersTextCopy.browserQueryRequiredToast();
    }

    public static String importSourceRequiredToast() {
        return SettingsImportFiltersTextCopy.importSourceRequiredToast();
    }

    public static String importFiltersSavedToast() {
        return SettingsImportFiltersTextCopy.importFiltersSavedToast();
    }

    public static String presetsTitle() {
        return SettingsImportFiltersTextCopy.presetsTitle();
    }

    public static String importPresetSavedToast() {
        return SettingsImportFiltersTextCopy.importPresetSavedToast();
    }

    public static String numericImportThresholdsToast() {
        return SettingsImportFiltersTextCopy.numericImportThresholdsToast();
    }

    public static String importThresholdRangeToast() {
        return SettingsImportFiltersTextCopy.importThresholdRangeToast();
    }

    public static String frequencyRangeTitle() {
        return SettingsReferenceDataTextCopy.frequencyRangeTitle();
    }

    public static String frequencyRangeBody() {
        return SettingsReferenceDataTextCopy.frequencyRangeBody();
    }

    public static String minRankLabel() {
        return SettingsReferenceDataTextCopy.minRankLabel();
    }

    public static String maxRankLabel() {
        return SettingsReferenceDataTextCopy.maxRankLabel();
    }

    public static String minimumRankLabel() {
        return SettingsReferenceDataTextCopy.minimumRankLabel();
    }

    public static String maximumRankLabel() {
        return SettingsReferenceDataTextCopy.maximumRankLabel();
    }

    public static String saveFrequencyRangeLabel() {
        return SettingsReferenceDataTextCopy.saveFrequencyRangeLabel();
    }

    public static String numericRanksToast() {
        return SettingsReferenceDataTextCopy.numericRanksToast();
    }

    public static String rankRangeToast() {
        return SettingsReferenceDataTextCopy.rankRangeToast();
    }

    public static String frequencyRangeSavedToast() {
        return SettingsReferenceDataTextCopy.frequencyRangeSavedToast();
    }

    public static String offlineDataLicensesTitle() {
        return SettingsReferenceDataTextCopy.offlineDataLicensesTitle();
    }

    public static String offlineDataLicensesBody() {
        return SettingsReferenceDataTextCopy.offlineDataLicensesBody();
    }

    public static String openDataLicensesLabel() {
        return SettingsReferenceDataTextCopy.openDataLicensesLabel();
    }

    public static String dataLicensesTitle() {
        return SettingsReferenceDataTextCopy.dataLicensesTitle();
    }

    public static String dataLicensesBody() {
        return SettingsReferenceDataTextCopy.dataLicensesBody();
    }

    public static String dictionaryDataTitle() {
        return SettingsReferenceDataTextCopy.dictionaryDataTitle();
    }

    public static String strokeDataTitle() {
        return SettingsReferenceDataTextCopy.strokeDataTitle();
    }

    public static String fontsTitle() {
        return SettingsReferenceDataTextCopy.fontsTitle();
    }

    public static String noteTypeFieldsTitle() {
        return SettingsNoteTypeTextCopy.noteTypeFieldsTitle();
    }

    public static String noteTypeUsingText(String modelName) {
        return SettingsNoteTypeTextCopy.noteTypeUsingText(modelName);
    }

    public static String noteTypeFieldsBody() {
        return SettingsNoteTypeTextCopy.noteTypeFieldsBody();
    }

    public static String requiredFieldsTitle() {
        return SettingsNoteTypeTextCopy.requiredFieldsTitle();
    }

    public static String requiredFieldsBody() {
        return SettingsNoteTypeTextCopy.requiredFieldsBody();
    }

    public static String expressionFieldLabel() {
        return SettingsNoteTypeTextCopy.expressionFieldLabel();
    }

    public static String readingFieldLabel() {
        return SettingsNoteTypeTextCopy.readingFieldLabel();
    }

    public static String meaningFieldLabel() {
        return SettingsNoteTypeTextCopy.meaningFieldLabel();
    }

    public static String sentenceFieldLabel() {
        return SettingsNoteTypeTextCopy.sentenceFieldLabel();
    }

    public static String frequencyFieldLabel() {
        return SettingsNoteTypeTextCopy.frequencyFieldLabel();
    }

    public static String frequencySortFieldLabel() {
        return SettingsNoteTypeTextCopy.frequencySortFieldLabel();
    }

    public static String chooseFromAnkiDroidLabel() {
        return SettingsNoteTypeTextCopy.chooseFromAnkiDroidLabel();
    }

    public static String useKikuLabel() {
        return SettingsNoteTypeTextCopy.useKikuLabel();
    }

    public static String saveNoteTypeLabel() {
        return SettingsNoteTypeTextCopy.saveNoteTypeLabel();
    }

    public static String noteTypeRequiredToast() {
        return SettingsNoteTypeTextCopy.noteTypeRequiredToast();
    }

    public static String expressionFieldRequiredToast() {
        return SettingsNoteTypeTextCopy.expressionFieldRequiredToast();
    }

    public static String noteTypeSavedToast() {
        return SettingsNoteTypeTextCopy.noteTypeSavedToast();
    }

    public static String newCardSortTitle() {
        return SettingsStudyPlanTextCopy.newCardSortTitle();
    }

    public static String newCardSortBody() {
        return SettingsStudyPlanTextCopy.newCardSortBody();
    }

    public static String saveNewCardSortLabel() {
        return SettingsStudyPlanTextCopy.saveNewCardSortLabel();
    }

    public static String fsrsRetentionTitle() {
        return SettingsStudyPlanTextCopy.fsrsRetentionTitle();
    }

    public static String fsrsRetentionBody() {
        return SettingsStudyPlanTextCopy.fsrsRetentionBody();
    }

    public static String useJitenRankRetentionRangesLabel() {
        return SettingsStudyPlanTextCopy.useJitenRankRetentionRangesLabel();
    }

    public static String jitenRankRetentionRangesBody() {
        return SettingsStudyPlanTextCopy.jitenRankRetentionRangesBody();
    }

    public static String useExampleRangesLabel() {
        return SettingsStudyPlanTextCopy.useExampleRangesLabel();
    }

    public static String saveRetentionLabel() {
        return SettingsStudyPlanTextCopy.saveRetentionLabel();
    }

    public static String retentionPresetLabel(int value) {
        return SettingsStudyPlanTextCopy.retentionPresetLabel(value);
    }

    public static String dailyWorkloadTitle() {
        return SettingsStudyPlanTextCopy.dailyWorkloadTitle();
    }

    public static String automaticWorkloadBody() {
        return SettingsStudyPlanTextCopy.automaticWorkloadBody();
    }

    public static String saveMaximumLabel() {
        return SettingsStudyPlanTextCopy.saveMaximumLabel();
    }

    public static String manualWorkloadLabel() {
        return SettingsStudyPlanTextCopy.manualWorkloadLabel();
    }

    public static String manualWorkloadBody() {
        return SettingsStudyPlanTextCopy.manualWorkloadBody();
    }

    public static String[] workloadScaleLabels() {
        return SettingsStudyPlanTextCopy.workloadScaleLabels();
    }

    public static String saveWorkloadLabel() {
        return SettingsStudyPlanTextCopy.saveWorkloadLabel();
    }

    public static String automaticParetoLabel() {
        return SettingsStudyPlanTextCopy.automaticParetoLabel();
    }

    public static String learningStepsTitle() {
        return SettingsLearningTextCopy.learningStepsTitle();
    }

    public static String learningStepsBody() {
        return SettingsLearningTextCopy.learningStepsBody();
    }

    public static String reviewMissesLabel() {
        return SettingsLearningTextCopy.reviewMissesLabel();
    }

    public static String ankiDefaultLabel() {
        return SettingsLearningTextCopy.ankiDefaultLabel();
    }

    public static String sameLearningStepsLabel() {
        return SettingsLearningTextCopy.sameLearningStepsLabel();
    }

    public static String saveLearningStepsLabel() {
        return SettingsLearningTextCopy.saveLearningStepsLabel();
    }

    public static String learningStepsSavedToast() {
        return SettingsLearningTextCopy.learningStepsSavedToast();
    }

    public static String studyAheadTitle() {
        return SettingsStudyAheadTextCopy.studyAheadTitle();
    }

    public static String studyAheadBody() {
        return SettingsStudyAheadTextCopy.studyAheadBody();
    }

    public static String saveStudyAheadLabel() {
        return SettingsStudyAheadTextCopy.saveStudyAheadLabel();
    }

    public static String studyAheadSavedToast() {
        return SettingsStudyAheadTextCopy.studyAheadSavedToast();
    }

    public static String ladderThresholdsTitle() {
        return SettingsLadderThresholdTextCopy.ladderThresholdsTitle();
    }

    public static String ladderThresholdsBody() {
        return SettingsLadderThresholdTextCopy.ladderThresholdsBody();
    }

    public static String fsrsDaysToGoUpLabel() {
        return SettingsLadderThresholdTextCopy.fsrsDaysToGoUpLabel();
    }

    public static String failsToGoDownLabel() {
        return SettingsLadderThresholdTextCopy.failsToGoDownLabel();
    }

    public static String useDefaultLadderThresholdsLabel() {
        return SettingsLadderThresholdTextCopy.useDefaultLadderThresholdsLabel();
    }

    public static String saveLadderThresholdsLabel() {
        return SettingsLadderThresholdTextCopy.saveLadderThresholdsLabel();
    }

    public static String ladderThresholdsSavedToast() {
        return SettingsLadderThresholdTextCopy.ladderThresholdsSavedToast();
    }

    public static String dailyAnkiSyncTitle() {
        return SettingsAutomationTextCopy.dailyAnkiSyncTitle();
    }

    public static String turnOffDailySyncLabel() {
        return SettingsAutomationTextCopy.turnOffDailySyncLabel();
    }

    public static String turnOnDailySyncLabel() {
        return SettingsAutomationTextCopy.turnOnDailySyncLabel();
    }

    public static String appUpdatesTitle() {
        return SettingsAutomationTextCopy.appUpdatesTitle();
    }

    public static String openUpdaterLabel() {
        return SettingsAutomationTextCopy.openUpdaterLabel();
    }

    public static String autoSyncStatus(boolean configured, boolean enabled, String displayTime) {
        return SettingsAutomationTextCopy.autoSyncStatus(configured, enabled, displayTime);
    }

    public static String autoSyncDetail(
            boolean configured,
            boolean enabled,
            String lastSuccessText,
            String lastAttemptText,
            String nextRunText
    ) {
        return SettingsAutomationTextCopy.autoSyncDetail(configured, enabled, lastSuccessText, lastAttemptText, nextRunText);
    }

    public static String workloadStatusText(int percent, int maxItems) {
        return SettingsStudyPlanTextCopy.workloadStatusText(percent, maxItems);
    }

    public static String maxItemsStatusText(int maxItems) {
        return SettingsStudyPlanTextCopy.maxItemsStatusText(maxItems);
    }

    public static String autoWorkloadStatusText(RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        return SettingsStudyPlanTextCopy.autoWorkloadStatusText(plan);
    }

    public static String newCardSortStatusText(String mode) {
        return SettingsStudyPlanTextCopy.newCardSortStatusText(mode);
    }

    public static String newCardSortLabel(String mode) {
        return SettingsStudyPlanTextCopy.newCardSortLabel(mode);
    }

    public static String frequencyRangeStatusText(int minRank, int maxRank) {
        return SettingsStudyPlanTextCopy.frequencyRangeStatusText(minRank, maxRank);
    }

    public static String retentionStatusText(int retentionPercent) {
        return SettingsStudyPlanTextCopy.retentionStatusText(retentionPercent);
    }

    public static String studyLadderTitle() {
        return SettingsStudyPlanTextCopy.studyLadderTitle();
    }

    public static String studyLadderBody() {
        return SettingsStudyPlanTextCopy.studyLadderBody();
    }

    public static String ladderToggleLabel(boolean enabled) {
        return SettingsStudyPlanTextCopy.ladderToggleLabel(enabled);
    }

    public static String moveUpLabel() {
        return SettingsStudyPlanTextCopy.moveUpLabel();
    }

    public static String moveDownLabel() {
        return SettingsStudyPlanTextCopy.moveDownLabel();
    }

    public static String restoreDefaultLadderLabel() {
        return SettingsStudyPlanTextCopy.restoreDefaultLadderLabel();
    }

    public static String studyLadderRestoredToast() {
        return SettingsStudyPlanTextCopy.studyLadderRestoredToast();
    }

    public static String keepAlwaysAvailableRungToast() {
        return SettingsStudyPlanTextCopy.keepAlwaysAvailableRungToast();
    }

    public static String ladderRungToggleToast(RecordsBase.LadderRung rung, boolean wasEnabled) {
        return SettingsStudyPlanTextCopy.ladderRungToggleToast(rung, wasEnabled);
    }

    public static String ladderRungSubtitle(RecordsBase.StudyLadderSettings ladder, RecordsBase.LadderRung rung) {
        return SettingsStudyPlanTextCopy.ladderRungSubtitle(ladder, rung);
    }

    public static String settingsLadderRungLabel(RecordsBase.LadderRung rung) {
        return SettingsStudyPlanTextCopy.settingsLadderRungLabel(rung);
    }

    public static String reminderStatus(boolean enabled, boolean blocked, String displayTime) {
        return SettingsAutomationTextCopy.reminderStatus(enabled, blocked, displayTime);
    }

    public static String dailyReminderTitle() {
        return SettingsAutomationTextCopy.dailyReminderTitle();
    }

    public static String dailyReminderBody() {
        return SettingsAutomationTextCopy.dailyReminderBody();
    }

    public static String morningReminderPresetLabel() {
        return SettingsAutomationTextCopy.morningReminderPresetLabel();
    }

    public static String lunchReminderPresetLabel() {
        return SettingsAutomationTextCopy.lunchReminderPresetLabel();
    }

    public static String eveningReminderPresetLabel() {
        return SettingsAutomationTextCopy.eveningReminderPresetLabel();
    }

    public static String nightReminderPresetLabel() {
        return SettingsAutomationTextCopy.nightReminderPresetLabel();
    }

    public static String saveReminderLabel() {
        return SettingsAutomationTextCopy.saveReminderLabel();
    }

    public static String enableReminderLabel() {
        return SettingsAutomationTextCopy.enableReminderLabel();
    }

    public static String turnOffReminderLabel() {
        return SettingsAutomationTextCopy.turnOffReminderLabel();
    }

    public static String notificationsBlockedBody() {
        return SettingsAutomationTextCopy.notificationsBlockedBody();
    }

    public static String openNotificationSettingsLabel() {
        return SettingsAutomationTextCopy.openNotificationSettingsLabel();
    }

    public static String notificationPermissionBody() {
        return SettingsAutomationTextCopy.notificationPermissionBody();
    }

    public static String reminderTime(int hour, int minute) {
        return SettingsAutomationTextCopy.reminderTime(hour, minute);
    }

    public static String reminderTimeButtonLabel(int hour, int minute) {
        return SettingsAutomationTextCopy.reminderTimeButtonLabel(hour, minute);
    }

    public static String reminderPresetButtonLabel(String label, int hour, int minute) {
        return SettingsAutomationTextCopy.reminderPresetButtonLabel(label, hour, minute);
    }

    public static String studyAheadMinutesLabel() {
        return SettingsStudyAheadTextCopy.studyAheadMinutesLabel();
    }

    public static String studyAheadMinutesRange() {
        return SettingsStudyAheadTextCopy.studyAheadMinutesRange();
    }

    public static String studyAheadWholeNumberErrorText() {
        return SettingsStudyAheadTextCopy.studyAheadWholeNumberErrorText();
    }

    public static String studyAheadOutOfRangeErrorText() {
        return SettingsStudyAheadTextCopy.studyAheadOutOfRangeErrorText();
    }

    public static String studyAheadMaxDescription() {
        return SettingsStudyAheadTextCopy.studyAheadMaxDescription();
    }
}
