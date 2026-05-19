package dev.bee.kanjianki;

import dev.bee.kanjianki.core.AutoSyncSettingsTogglePolicy;
import dev.bee.kanjianki.core.LearningStepsSettingsPolicy;
import dev.bee.kanjianki.core.NewCardSortSettingsPolicy;
import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RetentionSettingsPolicy;
import dev.bee.kanjianki.core.ReminderSettingsSavePolicy;
import dev.bee.kanjianki.core.SettingsImportPreset;
import dev.bee.kanjianki.core.StudyAheadSettingsPolicy;
import dev.bee.kanjianki.core.StudyLadderThresholdPolicy;
import dev.bee.kanjianki.core.WorkloadSettingsPolicy;
import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.sync.SyncSettings;
import dev.bee.kanjianki.updatecore.AutoUpdateSettingsTogglePolicy;

final class SettingsWriteActions {
    private SettingsWriteActions() {
    }

    static void saveLadderThresholds(StudyLadderThresholdPolicy.SaveResult request, IntSettingWriter writer) {
        if (request == null || !request.valid) {
            return;
        }
        writer.putIntSetting(SyncSettings.LADDER_PROMOTION_INTERVAL_DAYS_SETTING_KEY, request.promotionDays);
        writer.putIntSetting(SyncSettings.LADDER_DEMOTION_FAIL_STREAK_SETTING_KEY, request.failStreak);
        writer.putIntSetting(SyncSettings.WRITING_TRIGGER_MISS_DAYS_SETTING_KEY, request.failStreak);
        writer.putIntSetting(SyncSettings.REAL_DUE_REVIEWS_TO_MOVE_SETTING_KEY, request.failStreak);
    }

    static void saveFrequencyRange(int minRank, int maxRank, IntSettingWriter writer) {
        writer.putIntSetting("suspended_rank_min", minRank);
        writer.putIntSetting("suspended_rank_max", maxRank);
    }

    static void saveNoteTypeFields(NoteTypeFieldWriteRequest request, StringSettingWriter writer) {
        writer.putStringSetting(SyncSettings.NOTE_TYPE_SETTING_KEY, request.noteType());
        writer.putStringSetting(SyncSettings.EXPRESSION_FIELD_SETTING_KEY, request.expressionField());
        writer.putStringSetting(SyncSettings.READING_FIELD_SETTING_KEY, request.readingField());
        writer.putStringSetting(SyncSettings.MEANING_FIELD_SETTING_KEY, request.meaningField());
        writer.putStringSetting(SyncSettings.SENTENCE_FIELD_SETTING_KEY, request.sentenceField());
        writer.putStringSetting(SyncSettings.FREQUENCY_FIELD_SETTING_KEY, request.frequencyField());
        writer.putStringSetting(SyncSettings.FREQUENCY_SORT_FIELD_SETTING_KEY, request.frequencySortField());
    }

    static void saveNewCardSort(NewCardSortSettingsPolicy.SaveRequest request, StringSettingWriter writer) {
        writer.putStringSetting(SyncSettings.NEW_CARD_SORT_MODE_SETTING_KEY, request.mode);
    }

    static void saveLearningSteps(LearningStepsSettingsPolicy.SaveResult request, LearningStepSettingsWriter writer) {
        if (request == null || !request.valid) {
            return;
        }
        writer.saveLearningStepSettings(request.settings);
    }

    static void saveStudyLadder(RecordsBase.StudyLadderSettings settings, StudyLadderSettingsWriter writer) {
        writer.saveStudyLadderSettings(settings);
    }

    static void saveWorkload(WorkloadSettingsPolicy.SaveRequest request, WorkloadSettingsWriter writer) {
        if (request.mode != null) {
            writer.saveAdaptiveLoadMode(request.mode);
        }
        if (request.workloadPercent != null) {
            writer.saveAdaptiveLoadWorkPercent(request.workloadPercent);
        }
        if (request.maxItems != null) {
            writer.saveAdaptiveLoadMaxItems(request.maxItems);
        }
    }

    static void saveStudyAhead(StudyAheadSettingsPolicy.SaveResult request, StudyAheadSettingsWriter writer) {
        if (request == null || !request.valid) {
            return;
        }
        writer.saveStudyAheadMinutes(request.minutes);
    }

    static void saveRetention(RetentionSettingsPolicy.SaveResult request, SchedulerParametersWriter writer) {
        if (request == null || !request.valid) {
            return;
        }
        writer.saveSchedulerParameters(request.parameters);
    }

    static void moveStudyLadderRung(
            RecordsBase.StudyLadderSettings current,
            RecordsBase.LadderRung rung,
            int delta,
            StudyLadderSettingsWriter writer
    ) {
        saveStudyLadder(current.moveRung(rung, delta), writer);
    }

    static void restoreDefaultStudyLadder(StudyLadderSettingsWriter writer) {
        saveStudyLadder(RecordsBase.StudyLadderSettings.defaults(), writer);
    }

    static LocalStore.ReminderSettings reminderSettings(ReminderSettingsSavePolicy.ReminderFields fields) {
        return new LocalStore.ReminderSettings(fields.enabled(), fields.hour(), fields.minute());
    }

    static LocalStore.ReminderSettings saveReminder(
            ReminderSettingsSavePolicy.ReminderFields fields,
            ReminderSettingsWriter writer
    ) {
        LocalStore.ReminderSettings settings = reminderSettings(fields);
        writer.saveReminderSettings(settings);
        return settings;
    }

    static void setAutoSyncEnabled(AutoSyncSettingsTogglePolicy.ToggleResult result, AutoSyncSettingsWriter writer) {
        writer.setAutoSyncEnabled(result.enabled());
    }

    static void setAutoUpdateEnabled(AutoUpdateSettingsTogglePolicy.ToggleResult result, AutoUpdateSettingsWriter writer) {
        writer.saveAutoUpdateEnabled(result.enabled());
    }

    static void applyImportPreset(SettingsImportPreset preset, SettingWriter writer) {
        saveImportFilters(
                new ImportFilterWriteRequest(
                        preset.activeCards(),
                        preset.suspendedCards(),
                        preset.taggedCards(),
                        preset.tags(),
                        preset.weakCards(),
                        preset.weakDifficulty(),
                        preset.weakLapses(),
                        preset.minMatchingCards(),
                        preset.browserQueryCards(),
                        preset.browserQuery()
                ),
                writer
        );
    }

    static void saveImportFilters(ImportFilterWriteRequest request, SettingWriter writer) {
        writer.putIntSetting(SyncSettings.IMPORT_ACTIVE_CARDS_SETTING_KEY, SettingsImportPreset.boolFlag(request.activeCards()));
        writer.putIntSetting(SyncSettings.IMPORT_SUSPENDED_CARDS_SETTING_KEY, SettingsImportPreset.boolFlag(request.suspendedCards()));
        writer.putIntSetting(SyncSettings.IMPORT_TAGGED_CARDS_SETTING_KEY, SettingsImportPreset.boolFlag(request.taggedCards()));
        writer.putStringSetting(SyncSettings.IMPORT_TAGS_SETTING_KEY, request.tags());
        writer.putIntSetting(SyncSettings.IMPORT_WEAK_CARDS_SETTING_KEY, SettingsImportPreset.boolFlag(request.weakCards()));
        writer.putDoubleSetting(SyncSettings.IMPORT_WEAK_FSRS_DIFFICULTY_SETTING_KEY, request.weakDifficulty());
        writer.putIntSetting(SyncSettings.IMPORT_WEAK_LAPSES_SETTING_KEY, request.weakLapses());
        writer.putIntSetting(SyncSettings.IMPORT_MIN_MATCHING_CARDS_SETTING_KEY, request.minMatchingCards());
        writer.putIntSetting(SyncSettings.IMPORT_BROWSER_QUERY_CARDS_SETTING_KEY, SettingsImportPreset.boolFlag(request.browserQueryCards()));
        writer.putStringSetting(SyncSettings.IMPORT_BROWSER_QUERY_SETTING_KEY, request.browserQuery());
    }

    interface IntSettingWriter {
        void putIntSetting(String key, int value);
    }

    interface StringSettingWriter {
        void putStringSetting(String key, String value);
    }

    interface DoubleSettingWriter {
        void putDoubleSetting(String key, double value);
    }

    interface LearningStepSettingsWriter {
        void saveLearningStepSettings(RecordsSchedulerModels.LearningStepSettings settings);
    }

    interface StudyLadderSettingsWriter {
        void saveStudyLadderSettings(RecordsBase.StudyLadderSettings settings);
    }

    interface WorkloadSettingsWriter {
        void saveAdaptiveLoadMode(String mode);

        void saveAdaptiveLoadWorkPercent(int workloadPercent);

        void saveAdaptiveLoadMaxItems(int maxItems);
    }

    interface StudyAheadSettingsWriter {
        void saveStudyAheadMinutes(int minutes);
    }

    interface SchedulerParametersWriter {
        void saveSchedulerParameters(RecordsSchedulerModels.SchedulerParameters parameters);
    }

    interface ReminderSettingsWriter {
        void saveReminderSettings(LocalStore.ReminderSettings settings);
    }

    interface AutoSyncSettingsWriter {
        void setAutoSyncEnabled(boolean enabled);
    }

    interface AutoUpdateSettingsWriter {
        void saveAutoUpdateEnabled(boolean enabled);
    }

    interface SettingWriter extends IntSettingWriter {
        void putStringSetting(String key, String value);

        void putDoubleSetting(String key, double value);
    }

    record ImportFilterWriteRequest(
            boolean activeCards,
            boolean suspendedCards,
            boolean taggedCards,
            String tags,
            boolean weakCards,
            double weakDifficulty,
            int weakLapses,
            int minMatchingCards,
            boolean browserQueryCards,
            String browserQuery
    ) {
    }

    record NoteTypeFieldWriteRequest(
            String noteType,
            String expressionField,
            String readingField,
            String meaningField,
            String sentenceField,
            String frequencyField,
            String frequencySortField
    ) {
    }
}
