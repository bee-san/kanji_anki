package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class SettingsTextCopy {
    private static final String SOURCE_ACTIVE = "active";
    private static final String SOURCE_SUSPENDED = "suspended";

    private SettingsTextCopy() {
    }

    public static String settingsImportSummary(RecordsSyncModels.Settings settings) {
        RecordsSyncModels.Settings safeSettings = Objects.requireNonNull(settings, "settings");
        List<String> sources = new ArrayList<>();
        if (safeSettings.importActiveCards) {
            sources.add(SOURCE_ACTIVE);
        }
        if (safeSettings.importSuspendedCards) {
            sources.add(SOURCE_SUSPENDED);
        }
        if (safeSettings.importTaggedCardsEnabled()) {
            sources.add("tagged");
        }
        if (safeSettings.importWeakCards) {
            sources.add("weak");
        }
        if (safeSettings.browserQueryImportEnabled()) {
            sources.add("query");
        }
        if (sources.isEmpty()) {
            return "No sources";
        }
        return String.join(" + ", sources) + "; " + matchingCardsSummary(safeSettings);
    }

    public static String matchingCardsSummary(RecordsSyncModels.Settings settings) {
        RecordsSyncModels.Settings safeSettings = Objects.requireNonNull(settings, "settings");
        int count = safeSettings.importMinMatchingCardsPerKanji;
        return count + (count == 1 ? " matching card per kanji" : " matching cards per kanji");
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
        if (!success) {
            return "Sync blocked: " + String.valueOf(errorMessage);
        }
        return String.format(Locale.ROOT, "%d suspended cards archived, %d rare kanji added; active cards optional", suspendedCards, importedKanji);
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
        return "Frequency range";
    }

    public static String frequencyRangeBody() {
        return "Suspended cards are imported only when the kanji has a known Jiten rank inside this range. Lower ranks are more common. Default: 100-3000.";
    }

    public static String minRankLabel() {
        return "Min rank";
    }

    public static String maxRankLabel() {
        return "Max rank";
    }

    public static String minimumRankLabel() {
        return "Minimum rank";
    }

    public static String maximumRankLabel() {
        return "Maximum rank";
    }

    public static String saveFrequencyRangeLabel() {
        return "Save frequency range";
    }

    public static String numericRanksToast() {
        return "Enter numeric ranks.";
    }

    public static String rankRangeToast() {
        return "Use ranks from 1 to 20000.";
    }

    public static String frequencyRangeSavedToast() {
        return "Frequency range saved. Sync again to rebuild practice.";
    }

    public static String offlineDataLicensesTitle() {
        return "Offline data & licenses";
    }

    public static String offlineDataLicensesBody() {
        return "One reference page covers KANJIDIC2, Jiten rank data, KanjiVG stroke order, and bundled font attribution.";
    }

    public static String openDataLicensesLabel() {
        return "Open data licenses";
    }

    public static String dataLicensesTitle() {
        return "Data licenses";
    }

    public static String dataLicensesBody() {
        return "Dictionary and stroke-order data bundled for offline study.";
    }

    public static String dictionaryDataTitle() {
        return "Dictionary data";
    }

    public static String strokeDataTitle() {
        return "Stroke data";
    }

    public static String fontsTitle() {
        return "Fonts";
    }

    public static String noteTypeFieldsTitle() {
        return "Note type & clue fields";
    }

    public static String noteTypeUsingText(String modelName) {
        return "Using " + String.valueOf(modelName);
    }

    public static String noteTypeFieldsBody() {
        return "Default: Kiku. This single card owns the note type and all field mapping so clue configuration is not repeated elsewhere.";
    }

    public static String requiredFieldsTitle() {
        return "Required fields";
    }

    public static String requiredFieldsBody() {
        return "Expression = kanji source, ExpressionReading = reading, MainDefinition = meaning, Sentence = context, Frequency/FreqSort = metadata.";
    }

    public static String expressionFieldLabel() {
        return "Expression field";
    }

    public static String readingFieldLabel() {
        return "Reading field";
    }

    public static String meaningFieldLabel() {
        return "Meaning field";
    }

    public static String sentenceFieldLabel() {
        return "Sentence field";
    }

    public static String frequencyFieldLabel() {
        return "Frequency field";
    }

    public static String frequencySortFieldLabel() {
        return "Frequency sort field";
    }

    public static String chooseFromAnkiDroidLabel() {
        return "Choose from AnkiDroid";
    }

    public static String useKikuLabel() {
        return "Use Kiku";
    }

    public static String saveNoteTypeLabel() {
        return "Save note type";
    }

    public static String noteTypeRequiredToast() {
        return "Enter a note type name.";
    }

    public static String expressionFieldRequiredToast() {
        return "Choose the field that contains kanji.";
    }

    public static String noteTypeSavedToast() {
        return "Note type saved. Sync again to rebuild practice.";
    }

    public static String newCardSortTitle() {
        return "New card sort";
    }

    public static String newCardSortBody() {
        return "Choose how Kani admits and shows unseen new cards. Due reviews and learning repeats still keep their normal priority.";
    }

    public static String saveNewCardSortLabel() {
        return "Save new card sort";
    }

    public static String fsrsRetentionTitle() {
        return "FSRS retention";
    }

    public static String fsrsRetentionBody() {
        return "Higher retention keeps intervals shorter. This changes Kani's internal FSRS intervals, not Anki's schedule.";
    }

    public static String useJitenRankRetentionRangesLabel() {
        return "Use Jiten-rank retention ranges";
    }

    public static String jitenRankRetentionRangesBody() {
        return "Optional: one inclusive Jiten rank range per line, such as 1-500=95%. Unmatched or unranked kanji use the global retention above.";
    }

    public static String useExampleRangesLabel() {
        return "Use example ranges";
    }

    public static String saveRetentionLabel() {
        return "Save retention";
    }

    public static String retentionPresetLabel(int value) {
        return value + "%";
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
        return "Study ahead";
    }

    public static String studyAheadBody() {
        return "Pull cards becoming due within this many minutes into the queue. Set 0 to disable. Learning step delays still apply normally (just like Anki).";
    }

    public static String saveStudyAheadLabel() {
        return "Save study ahead";
    }

    public static String studyAheadSavedToast() {
        return "Study ahead saved.";
    }

    public static String ladderThresholdsTitle() {
        return "Ladder thresholds";
    }

    public static String ladderThresholdsBody() {
        return "Recognition rungs climb when a real FSRS-due pass schedules the next review beyond the day threshold. Learning-step repeats stay practice-only.";
    }

    public static String fsrsDaysToGoUpLabel() {
        return "FSRS days to go up";
    }

    public static String failsToGoDownLabel() {
        return "Fails to go down";
    }

    public static String useDefaultLadderThresholdsLabel() {
        return "Use 21 and 3";
    }

    public static String saveLadderThresholdsLabel() {
        return "Save ladder thresholds";
    }

    public static String ladderThresholdsSavedToast() {
        return "Ladder thresholds saved.";
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
        return SettingsAutomationTextCopy.studyAheadMinutesLabel();
    }

    public static String studyAheadMinutesRange() {
        return SettingsAutomationTextCopy.studyAheadMinutesRange();
    }

    public static String studyAheadWholeNumberErrorText() {
        return SettingsAutomationTextCopy.studyAheadWholeNumberErrorText();
    }

    public static String studyAheadOutOfRangeErrorText() {
        return SettingsAutomationTextCopy.studyAheadOutOfRangeErrorText();
    }

    public static String studyAheadMaxDescription() {
        return SettingsAutomationTextCopy.studyAheadMaxDescription();
    }
}
