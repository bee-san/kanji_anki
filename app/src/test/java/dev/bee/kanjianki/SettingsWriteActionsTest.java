package dev.bee.kanjianki;

import dev.bee.kanjianki.core.SettingsImportPreset;
import dev.bee.kanjianki.core.NewCardSortSettingsPolicy;
import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.LearningStepsSettingsPolicy;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RetentionSettingsPolicy;
import dev.bee.kanjianki.core.ReminderSettingsSavePolicy;
import dev.bee.kanjianki.core.AutoSyncSettingsTogglePolicy;
import dev.bee.kanjianki.core.StudyAheadSettingsPolicy;
import dev.bee.kanjianki.core.StudyLadderThresholdPolicy;
import dev.bee.kanjianki.core.WorkloadSettingsPolicy;
import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.sync.SyncSettings;
import dev.bee.kanjianki.updatecore.AutoUpdateSettingsTogglePolicy;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class SettingsWriteActionsTest {
    @Test
    public void saveLadderThresholdsWritesPrimaryAndCompatibilityKeys() {
        Map<String, Integer> settings = new LinkedHashMap<>();

        SettingsWriteActions.saveLadderThresholds(
                StudyLadderThresholdPolicy.saveRequest("21", "3"),
                settings::put
        );

        assertEquals(4, settings.size());
        assertEquals(Integer.valueOf(21), settings.get(SyncSettings.LADDER_PROMOTION_INTERVAL_DAYS_SETTING_KEY));
        assertEquals(Integer.valueOf(3), settings.get(SyncSettings.LADDER_DEMOTION_FAIL_STREAK_SETTING_KEY));
        assertEquals(Integer.valueOf(3), settings.get(SyncSettings.WRITING_TRIGGER_MISS_DAYS_SETTING_KEY));
        assertEquals(Integer.valueOf(3), settings.get(SyncSettings.REAL_DUE_REVIEWS_TO_MOVE_SETTING_KEY));
    }

    @Test
    public void saveLadderThresholdsIgnoresInvalidRequests() {
        Map<String, Integer> settings = new LinkedHashMap<>();

        SettingsWriteActions.saveLadderThresholds(
                StudyLadderThresholdPolicy.saveRequest("0", "3"),
                settings::put
        );

        assertTrue(settings.isEmpty());
    }

    @Test
    public void saveFrequencyRangeWritesLegacyRankKeys() {
        Map<String, Integer> settings = new LinkedHashMap<>();

        SettingsWriteActions.saveFrequencyRange(250, 3000, settings::put);

        assertEquals(2, settings.size());
        assertEquals(Integer.valueOf(250), settings.get("suspended_rank_min"));
        assertEquals(Integer.valueOf(3000), settings.get("suspended_rank_max"));
    }

    @Test
    public void saveNoteTypeFieldsWritesAllFieldMappingKeys() {
        Map<String, String> settings = new LinkedHashMap<>();

        SettingsWriteActions.saveNoteTypeFields(
                new SettingsWriteActions.NoteTypeFieldWriteRequest(
                        "Kiku",
                        "Expression",
                        "Reading",
                        "Meaning",
                        "Sentence",
                        "Frequency",
                        "FrequencySort"
                ),
                settings::put
        );

        assertEquals(7, settings.size());
        assertEquals("Kiku", settings.get(SyncSettings.NOTE_TYPE_SETTING_KEY));
        assertEquals("Expression", settings.get(SyncSettings.EXPRESSION_FIELD_SETTING_KEY));
        assertEquals("Reading", settings.get(SyncSettings.READING_FIELD_SETTING_KEY));
        assertEquals("Meaning", settings.get(SyncSettings.MEANING_FIELD_SETTING_KEY));
        assertEquals("Sentence", settings.get(SyncSettings.SENTENCE_FIELD_SETTING_KEY));
        assertEquals("Frequency", settings.get(SyncSettings.FREQUENCY_FIELD_SETTING_KEY));
        assertEquals("FrequencySort", settings.get(SyncSettings.FREQUENCY_SORT_FIELD_SETTING_KEY));
    }

    @Test
    public void saveNewCardSortWritesNormalizedModeKey() {
        Map<String, String> settings = new LinkedHashMap<>();

        SettingsWriteActions.saveNewCardSort(
                NewCardSortSettingsPolicy.saveRequest(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS),
                settings::put
        );

        assertEquals(1, settings.size());
        assertEquals(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS, settings.get(SyncSettings.NEW_CARD_SORT_MODE_SETTING_KEY));
    }

    @Test
    public void saveLearningStepsWritesValidParsedSettingsOnly() {
        RecordingLearningStepWriter writer = new RecordingLearningStepWriter();

        SettingsWriteActions.saveLearningSteps(
                LearningStepsSettingsPolicy.saveRequest("1m 10m", "5m"),
                writer
        );
        SettingsWriteActions.saveLearningSteps(
                LearningStepsSettingsPolicy.saveRequest("bad", "5m"),
                writer
        );

        assertEquals("1m, 10m", writer.settings.newStepsText());
        assertEquals("5m", writer.settings.reviewStepsText());
    }

    @Test
    public void studyLadderActionsWriteMovedRestoredAndProvidedSettings() {
        RecordingStudyLadderWriter writer = new RecordingStudyLadderWriter();
        RecordsBase.StudyLadderSettings current = RecordsBase.StudyLadderSettings.defaults();

        SettingsWriteActions.moveStudyLadderRung(current, RecordsBase.LadderRung.WORD_READING, -6, writer);
        assertEquals(current.moveRung(RecordsBase.LadderRung.WORD_READING, -6).orderText(), writer.settings.orderText());

        SettingsWriteActions.restoreDefaultStudyLadder(writer);
        assertEquals(RecordsBase.StudyLadderSettings.defaults().orderText(), writer.settings.orderText());

        RecordsBase.StudyLadderSettings disabled = current.withRungEnabled(RecordsBase.LadderRung.SIMILAR_KANJI, false);
        SettingsWriteActions.saveStudyLadder(disabled, writer);
        assertEquals(disabled.enabledText(), writer.settings.enabledText());
    }

    @Test
    public void saveWorkloadWritesAllAdaptiveLoadFields() {
        RecordingWorkloadWriter writer = new RecordingWorkloadWriter();

        SettingsWriteActions.saveWorkload(WorkloadSettingsPolicy.saveManualWorkload(84, 6), writer);

        assertEquals(AdaptiveLoadPlanner.MODE_MANUAL, writer.mode);
        assertEquals(85, writer.workloadPercent);
        assertEquals(6, writer.maxItems);
    }

    @Test
    public void saveStudyAheadWritesValidMinutesOnly() {
        RecordingStudyAheadWriter writer = new RecordingStudyAheadWriter();

        SettingsWriteActions.saveStudyAhead(StudyAheadSettingsPolicy.saveRequest("25"), writer);
        SettingsWriteActions.saveStudyAhead(StudyAheadSettingsPolicy.saveRequest("-1"), writer);

        assertEquals(25, writer.minutes);
        assertEquals(1, writer.writes);
    }

    @Test
    public void saveRetentionWritesValidSchedulerParametersOnly() {
        RecordingSchedulerParametersWriter writer = new RecordingSchedulerParametersWriter();
        RecordsSchedulerModels.SchedulerParameters latest = RecordsSchedulerModels.SchedulerParameters.defaults();

        SettingsWriteActions.saveRetention(
                RetentionSettingsPolicy.saveRequest(95, false, "1-500=95%", latest),
                writer
        );
        SettingsWriteActions.saveRetention(
                RetentionSettingsPolicy.saveRequest(95, true, "500-1=95%", latest),
                writer
        );

        assertEquals(1, writer.writes);
        assertEquals(0.95, writer.parameters.targetRetention, 0.0);
    }

    @Test
    public void saveReminderBuildsAndWritesNormalizedFields() {
        RecordingReminderWriter writer = new RecordingReminderWriter();
        ReminderSettingsSavePolicy.ReminderFields fields = ReminderSettingsSavePolicy.fields(true, 30, -4);

        LocalStore.ReminderSettings reminder = SettingsWriteActions.saveReminder(fields, writer);

        assertTrue(reminder.enabled);
        assertEquals(23, reminder.hour);
        assertEquals(0, reminder.minute);
        assertEquals(reminder, writer.settings);
    }

    @Test
    public void setAutoSyncEnabledWritesToggleResultFlag() {
        RecordingAutoSyncWriter writer = new RecordingAutoSyncWriter();

        SettingsWriteActions.setAutoSyncEnabled(AutoSyncSettingsTogglePolicy.enable(), writer);
        assertTrue(writer.enabled);

        SettingsWriteActions.setAutoSyncEnabled(AutoSyncSettingsTogglePolicy.disable(), writer);
        assertEquals(false, writer.enabled);
    }

    @Test
    public void setAutoUpdateEnabledWritesToggleResultFlag() {
        RecordingAutoUpdateWriter writer = new RecordingAutoUpdateWriter();

        SettingsWriteActions.setAutoUpdateEnabled(AutoUpdateSettingsTogglePolicy.toggle(false), writer);
        assertTrue(writer.enabled);

        SettingsWriteActions.setAutoUpdateEnabled(AutoUpdateSettingsTogglePolicy.toggle(true), writer);
        assertEquals(false, writer.enabled);
    }

    @Test
    public void applyImportPresetWritesEveryImportSetting() {
        RecordingSettingsWriter writer = new RecordingSettingsWriter();
        SettingsImportPreset preset = new SettingsImportPreset(
                "Custom",
                true,
                false,
                true,
                "kani leech",
                true,
                8.5,
                4,
                2,
                true,
                "deck:Mining"
        );

        SettingsWriteActions.applyImportPreset(preset, writer);

        assertEquals(10, writer.settings.size());
        assertEquals(1, writer.settings.get(SyncSettings.IMPORT_ACTIVE_CARDS_SETTING_KEY));
        assertEquals(0, writer.settings.get(SyncSettings.IMPORT_SUSPENDED_CARDS_SETTING_KEY));
        assertEquals(1, writer.settings.get(SyncSettings.IMPORT_TAGGED_CARDS_SETTING_KEY));
        assertEquals("kani leech", writer.settings.get(SyncSettings.IMPORT_TAGS_SETTING_KEY));
        assertEquals(1, writer.settings.get(SyncSettings.IMPORT_WEAK_CARDS_SETTING_KEY));
        assertEquals(8.5, (Double) writer.settings.get(SyncSettings.IMPORT_WEAK_FSRS_DIFFICULTY_SETTING_KEY), 0.0);
        assertEquals(4, writer.settings.get(SyncSettings.IMPORT_WEAK_LAPSES_SETTING_KEY));
        assertEquals(2, writer.settings.get(SyncSettings.IMPORT_MIN_MATCHING_CARDS_SETTING_KEY));
        assertEquals(1, writer.settings.get(SyncSettings.IMPORT_BROWSER_QUERY_CARDS_SETTING_KEY));
        assertEquals("deck:Mining", writer.settings.get(SyncSettings.IMPORT_BROWSER_QUERY_SETTING_KEY));
    }

    @Test
    public void saveImportFiltersWritesSelectedValuesWithPresetEncoding() {
        RecordingSettingsWriter writer = new RecordingSettingsWriter();

        SettingsWriteActions.saveImportFilters(
                new SettingsWriteActions.ImportFilterWriteRequest(
                        false,
                        true,
                        true,
                        "kani leech",
                        false,
                        6.5,
                        5,
                        3,
                        true,
                        "rated:30:1"
                ),
                writer
        );

        assertEquals(10, writer.settings.size());
        assertEquals(0, writer.settings.get(SyncSettings.IMPORT_ACTIVE_CARDS_SETTING_KEY));
        assertEquals(1, writer.settings.get(SyncSettings.IMPORT_SUSPENDED_CARDS_SETTING_KEY));
        assertEquals(1, writer.settings.get(SyncSettings.IMPORT_TAGGED_CARDS_SETTING_KEY));
        assertEquals("kani leech", writer.settings.get(SyncSettings.IMPORT_TAGS_SETTING_KEY));
        assertEquals(0, writer.settings.get(SyncSettings.IMPORT_WEAK_CARDS_SETTING_KEY));
        assertEquals(6.5, (Double) writer.settings.get(SyncSettings.IMPORT_WEAK_FSRS_DIFFICULTY_SETTING_KEY), 0.0);
        assertEquals(5, writer.settings.get(SyncSettings.IMPORT_WEAK_LAPSES_SETTING_KEY));
        assertEquals(3, writer.settings.get(SyncSettings.IMPORT_MIN_MATCHING_CARDS_SETTING_KEY));
        assertEquals(1, writer.settings.get(SyncSettings.IMPORT_BROWSER_QUERY_CARDS_SETTING_KEY));
        assertEquals("rated:30:1", writer.settings.get(SyncSettings.IMPORT_BROWSER_QUERY_SETTING_KEY));
    }

    private static final class RecordingSettingsWriter implements SettingsWriteActions.SettingWriter {
        final Map<String, Object> settings = new LinkedHashMap<>();

        @Override
        public void putIntSetting(String key, int value) {
            settings.put(key, value);
        }

        @Override
        public void putStringSetting(String key, String value) {
            settings.put(key, value);
        }

        @Override
        public void putDoubleSetting(String key, double value) {
            settings.put(key, value);
        }
    }

    private static final class RecordingLearningStepWriter implements SettingsWriteActions.LearningStepSettingsWriter {
        RecordsSchedulerModels.LearningStepSettings settings;

        @Override
        public void saveLearningStepSettings(RecordsSchedulerModels.LearningStepSettings settings) {
            this.settings = settings;
        }
    }

    private static final class RecordingStudyLadderWriter implements SettingsWriteActions.StudyLadderSettingsWriter {
        RecordsBase.StudyLadderSettings settings;

        @Override
        public void saveStudyLadderSettings(RecordsBase.StudyLadderSettings settings) {
            this.settings = settings;
        }
    }

    private static final class RecordingWorkloadWriter implements SettingsWriteActions.WorkloadSettingsWriter {
        String mode;
        int workloadPercent;
        int maxItems;

        @Override
        public void saveAdaptiveLoadMode(String mode) {
            this.mode = mode;
        }

        @Override
        public void saveAdaptiveLoadWorkPercent(int workloadPercent) {
            this.workloadPercent = workloadPercent;
        }

        @Override
        public void saveAdaptiveLoadMaxItems(int maxItems) {
            this.maxItems = maxItems;
        }
    }

    private static final class RecordingStudyAheadWriter implements SettingsWriteActions.StudyAheadSettingsWriter {
        int minutes;
        int writes;

        @Override
        public void saveStudyAheadMinutes(int minutes) {
            this.minutes = minutes;
            writes++;
        }
    }

    private static final class RecordingSchedulerParametersWriter implements SettingsWriteActions.SchedulerParametersWriter {
        RecordsSchedulerModels.SchedulerParameters parameters;
        int writes;

        @Override
        public void saveSchedulerParameters(RecordsSchedulerModels.SchedulerParameters parameters) {
            this.parameters = parameters;
            writes++;
        }
    }

    private static final class RecordingReminderWriter implements SettingsWriteActions.ReminderSettingsWriter {
        LocalStore.ReminderSettings settings;

        @Override
        public void saveReminderSettings(LocalStore.ReminderSettings settings) {
            this.settings = settings;
        }
    }

    private static final class RecordingAutoSyncWriter implements SettingsWriteActions.AutoSyncSettingsWriter {
        boolean enabled;

        @Override
        public void setAutoSyncEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    private static final class RecordingAutoUpdateWriter implements SettingsWriteActions.AutoUpdateSettingsWriter {
        boolean enabled;

        @Override
        public void saveAutoUpdateEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
