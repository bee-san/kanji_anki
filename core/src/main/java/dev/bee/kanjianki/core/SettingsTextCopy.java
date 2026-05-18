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
        if (blocked) {
            return "Blocked";
        }
        return enabled ? displayTime : "Off";
    }

    public static String settingsAutoSyncSummary(boolean configured, boolean enabled, String displayTime) {
        if (!configured) {
            return "After first sync";
        }
        return enabled ? displayTime : "Off";
    }

    public static String settingsUpdateSummary(boolean hasPendingUpdate, boolean enabled) {
        if (hasPendingUpdate) {
            return "Verified APK ready";
        }
        return enabled ? "Automatic checks on" : "Manual checks";
    }

    public static String syncStatusHeadline(boolean success, String errorMessage, int suspendedCards, int importedKanji) {
        if (!success) {
            return "Sync blocked: " + String.valueOf(errorMessage);
        }
        return String.format(Locale.ROOT, "%d suspended cards archived, %d rare kanji added; active cards optional", suspendedCards, importedKanji);
    }

    public static String versionText(String version) {
        if (version == null || version.trim().isEmpty()) {
            return "unknown version";
        }
        return version.replaceFirst("^v", "");
    }

    public static String settingsAnkiSourceTitle() {
        return "Anki source";
    }

    public static String settingsAnkiSourceBody() {
        return "What Kani reads from AnkiDroid, and which cards become practice.";
    }

    public static String settingsStudyBehaviorTitle() {
        return "Study behavior";
    }

    public static String settingsStudyBehaviorBody() {
        return "How much appears today, how quickly repeats return, and when cards move rungs.";
    }

    public static String settingsAutomationTitle() {
        return "Automation";
    }

    public static String settingsAutomationBody() {
        return "Background nudges, daily AnkiDroid refreshes, and app update checks.";
    }

    public static String settingsReferenceDataTitle() {
        return "Reference data";
    }

    public static String settingsReferenceDataBody() {
        return "Offline dictionaries, frequency ranks, stroke data, fonts, and attribution.";
    }

    public static String updatePageTitle() {
        return "GitHub updater";
    }

    public static String updatePageBody(String versionName) {
        return "Current version " + String.valueOf(versionName)
                + ". Checks GitHub Releases, verifies the APK, and asks Android to install it.";
    }

    public static String automaticUpdatesTitle() {
        return "Automatic updates";
    }

    public static String checkForUpdateLabel() {
        return "Check for update";
    }

    public static String autoUpdatePanelStatus(boolean enabled) {
        return enabled ? "On: checks about once a day" : "Off";
    }

    public static String autoUpdateLastCheckLine(String lastCheckText) {
        return "Last check: " + String.valueOf(lastCheckText);
    }

    public static String autoUpdateLastResultLine(String lastResult) {
        return "Last result: " + String.valueOf(lastResult);
    }

    public static String installPermissionLine(boolean canInstall) {
        return "Install permission: " + (canInstall ? "Ready" : "Missing");
    }

    public static String verifiedApkReadyLine(String version) {
        return "Verified APK ready: " + versionText(version);
    }

    public static String pendingUpdateFallback() {
        return "Android needs confirmation before Kani can replace itself.";
    }

    public static String installVerifiedUpdateLabel() {
        return "Install verified update";
    }

    public static String setupAppInstallsLabel() {
        return "Set up app installs";
    }

    public static String automaticUpdatesToggleLabel(boolean enabled) {
        return enabled ? "Turn off automatic updates" : "Turn on automatic updates";
    }

    public static String backToSettingsLabel() {
        return "Back to settings";
    }

    public static String settingsCockpitLabel() {
        return "Settings cockpit";
    }

    public static String settingsHeroBody() {
        return "Grouped by outcome: source data, study behavior, automation, and offline references. Each setting appears once, next to the thing it changes.";
    }

    public static String noteTypeStatusLabel() {
        return "Note type";
    }

    public static String importFiltersStatusLabel() {
        return "Import filters";
    }

    public static String importRanksStatusLabel() {
        return "Import ranks";
    }

    public static String reminderStatusLabel() {
        return "Reminder";
    }

    public static String dailySyncStatusLabel() {
        return "Daily sync";
    }

    public static String updatesStatusLabel() {
        return "Updates";
    }

    public static String matchingCardsStatusLabel() {
        return "Matching cards";
    }

    public static String importFiltersTitle() {
        return "Import filters";
    }

    public static String importFiltersBody() {
        return "Suspended AnkiDroid cards are the default source for Kani practice. Turn on active, tagged, or weak cards only when you want those sources included.";
    }

    public static String activeCardsLabel() {
        return "Active cards";
    }

    public static String suspendedCardsLabel() {
        return "Suspended cards";
    }

    public static String taggedCardsLabel() {
        return "Tagged cards";
    }

    public static String weakCardsLabel() {
        return "Weak cards";
    }

    public static String browserQueryLabel() {
        return "Browser query";
    }

    public static String ankiBrowserQueryHint() {
        return "deck:Japanese tag:kani";
    }

    public static String ankiBrowserQueryLabel() {
        return "Anki browser query";
    }

    public static String ankiNoteTagsHint() {
        return "tag1, tag2";
    }

    public static String ankiNoteTagsLabel() {
        return "Anki note tags";
    }

    public static String fsrsDifficultyLabel() {
        return "FSRS difficulty";
    }

    public static String lapsesLabel() {
        return "Lapses";
    }

    public static String minimumMatchingCardsLabel() {
        return "Minimum matching cards per kanji";
    }

    public static String saveImportFiltersLabel() {
        return "Save import filters";
    }

    public static String browserQueryRequiredToast() {
        return "Enter an Anki browser query or turn off Browser query.";
    }

    public static String importSourceRequiredToast() {
        return "Turn on at least one import source.";
    }

    public static String importFiltersSavedToast() {
        return "Import filters saved. Sync again to rebuild practice.";
    }

    public static String presetsTitle() {
        return "Presets";
    }

    public static String importPresetSavedToast() {
        return "Import preset saved. Sync again to rebuild practice.";
    }

    public static String numericImportThresholdsToast() {
        return "Use numeric import thresholds.";
    }

    public static String importThresholdRangeToast() {
        return "Use difficulty 1-10, lapses 1-100, and cards 1-1000.";
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

    public static String dailyWorkloadTitle() {
        return "Daily workload";
    }

    public static String automaticWorkloadBody() {
        return "Kani automatically chooses where today's problem-kanji priority curve drops off. This changes how much it admits today, not Anki's schedule.";
    }

    public static String saveMaximumLabel() {
        return "Save maximum";
    }

    public static String manualWorkloadLabel() {
        return "Use manual workload";
    }

    public static String manualWorkloadBody() {
        return "Manual workload overrides the automatic Pareto drop-off. This changes how much Kani admits today, not Anki's schedule.";
    }

    public static String[] workloadScaleLabels() {
        return new String[]{"Very little", "Pareto", "Balanced", "More", "All kanji"};
    }

    public static String saveWorkloadLabel() {
        return "Save workload";
    }

    public static String automaticParetoLabel() {
        return "Use automatic Pareto";
    }

    public static String learningStepsTitle() {
        return "Learning steps";
    }

    public static String learningStepsBody() {
        return "New cards and review misses can come back quickly for practice. These repeats do not change Kani's SRS after the first answer.";
    }

    public static String reviewMissesLabel() {
        return "Review misses";
    }

    public static String ankiDefaultLabel() {
        return "Anki default";
    }

    public static String sameLearningStepsLabel() {
        return "Both 1m 10m";
    }

    public static String saveLearningStepsLabel() {
        return "Save learning steps";
    }

    public static String learningStepsSavedToast() {
        return "Learning steps saved.";
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

    public static String autoSyncStatus(boolean configured, boolean enabled, String displayTime) {
        if (!configured) {
            return "Starts after first successful sync";
        }
        if (enabled) {
            return "On around " + displayTime;
        }
        return "Off";
    }

    public static String autoSyncDetail(
            boolean configured,
            boolean enabled,
            String lastSuccessText,
            String lastAttemptText,
            String nextRunText
    ) {
        if (!configured) {
            return "Manual sync once, then Kani will keep itself refreshed once per day.";
        }
        List<String> details = new ArrayList<>();
        addDetail(details, "Last auto success ", lastSuccessText);
        addDetail(details, "Last auto attempt ", lastAttemptText);
        if (enabled) {
            addDetail(details, "Next scheduled ", nextRunText);
        }
        if (details.isEmpty()) {
            return enabled
                    ? "Scheduled once per local day. Android may batch the exact time."
                    : "Daily background sync is paused.";
        }
        return String.join(". ", details) + ".";
    }

    public static String workloadStatusText(int percent, int maxItems) {
        int snapped = AdaptiveLoadPlanner.snapWorkloadPercent(percent);
        int normalizedMax = AdaptiveLoadPlanner.normalizeMaxItems(maxItems);
        String label = AdaptiveLoadPlanner.workloadLabel(snapped);
        if (snapped >= 100) {
            return label + ": up to " + normalizedMax + " items";
        }
        return label + ": up to " + Math.min(AdaptiveLoadPlanner.targetCeiling(snapped), normalizedMax) + " items";
    }

    public static String maxItemsStatusText(int maxItems) {
        return "Maximum: " + StudyTextCopy.countText(AdaptiveLoadPlanner.normalizeMaxItems(maxItems), "item", "items");
    }

    public static String autoWorkloadStatusText(RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        if (plan == null || plan.target <= 0) {
            return "Auto Pareto: waiting for problem kanji";
        }
        return "Auto Pareto: " + StudyTextCopy.countText(plan.target, "item", "items") + " today";
    }

    public static String newCardSortStatusText(String mode) {
        return "Current: " + newCardSortLabel(mode);
    }

    public static String newCardSortLabel(String mode) {
        return switch (RecordsSyncModels.Settings.normalizeNewCardSortMode(mode)) {
            case RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY -> "Anki difficulty";
            case RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK -> "Retrievability risk";
            case RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS -> "Kani weakness";
            default -> "Frequency";
        };
    }

    public static String frequencyRangeStatusText(int minRank, int maxRank) {
        return String.format(Locale.ROOT, "Jiten ranks %d-%d", minRank, maxRank);
    }

    public static String retentionStatusText(int retentionPercent) {
        return "Desired retention: " + retentionPercent + "%";
    }

    public static String studyLadderTitle() {
        return "Study ladder";
    }

    public static String studyLadderBody() {
        return "Turn rungs off or move them up and down. At least one always-available rung stays on.";
    }

    public static String ladderToggleLabel(boolean enabled) {
        return enabled ? "On" : "Off";
    }

    public static String moveUpLabel() {
        return "Up";
    }

    public static String moveDownLabel() {
        return "Down";
    }

    public static String restoreDefaultLadderLabel() {
        return "Restore default ladder";
    }

    public static String studyLadderRestoredToast() {
        return "Study ladder restored.";
    }

    public static String keepAlwaysAvailableRungToast() {
        return "Keep at least one always-available rung on.";
    }

    public static String ladderRungToggleToast(RecordsBase.LadderRung rung, boolean wasEnabled) {
        return settingsLadderRungLabel(rung) + (wasEnabled ? " off." : " on.");
    }

    public static String ladderRungSubtitle(RecordsBase.StudyLadderSettings ladder, RecordsBase.LadderRung rung) {
        String status = ladder.isEnabled(rung) ? "Enabled" : "Disabled";
        String kind = rung == RecordsBase.LadderRung.SIMILAR_KANJI ? "conditional" : "always available";
        return status + " " + kind + " rung";
    }

    public static String settingsLadderRungLabel(RecordsBase.LadderRung rung) {
        return switch (rung) {
            case WRITE_KANJI -> "Write kanji";
            case SIMILAR_KANJI -> "Similar kanji";
            case TYPE_MEANING -> "Type the meaning";
            case MEANING_KANJI -> "Meaning -> kanji";
            case KANJI_MEANING -> "Kanji -> meaning";
            case FONT_MEANING -> "Font -> meaning";
            case WORD_READING -> "Word -> reading";
        };
    }

    public static String reminderStatus(boolean enabled, boolean blocked, String displayTime) {
        if (blocked) {
            return "Blocked: notifications off";
        }
        if (enabled) {
            return "Daily around " + displayTime;
        }
        return "Off";
    }

    public static String reminderTime(int hour, int minute) {
        return TimeOfDaySettingsPolicy.displayTime(hour, minute);
    }

    public static String reminderTimeButtonLabel(int hour, int minute) {
        return "Reminder time: " + TimeOfDaySettingsPolicy.displayTime(hour, minute);
    }

    public static String studyAheadMinutesLabel() {
        return String.format(Locale.ROOT, "Minutes (%s)", studyAheadMinutesRange());
    }

    public static String studyAheadMinutesRange() {
        return String.format(Locale.ROOT, "%d-%d", SettingsInputRules.DEFAULT_STUDY_AHEAD_MINUTES, SettingsInputRules.MAX_STUDY_AHEAD_MINUTES);
    }

    public static String studyAheadWholeNumberErrorText() {
        return String.format(Locale.ROOT, "Use a whole number of minutes (%s).", studyAheadMinutesRange());
    }

    public static String studyAheadOutOfRangeErrorText() {
        return String.format(Locale.ROOT, "Use %d to disable, or up to %s.", SettingsInputRules.DEFAULT_STUDY_AHEAD_MINUTES, studyAheadMaxDescription());
    }

    public static String studyAheadMaxDescription() {
        int maxMinutes = SettingsInputRules.MAX_STUDY_AHEAD_MINUTES;
        if (maxMinutes % 60 == 0) {
            return String.format(Locale.ROOT, "%d minutes (%dh)", maxMinutes, maxMinutes / 60);
        }
        return String.format(Locale.ROOT, "%d minutes", maxMinutes);
    }

    private static void addDetail(List<String> details, String prefix, String value) {
        if (value != null && !value.isEmpty()) {
            details.add(prefix + value);
        }
    }
}
