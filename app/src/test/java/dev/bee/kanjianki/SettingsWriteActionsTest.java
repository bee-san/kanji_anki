package dev.bee.kanjianki;

import dev.bee.kanjianki.core.SettingsImportPreset;
import dev.bee.kanjianki.core.NewCardSortSettingsPolicy;
import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.StudyLadderThresholdPolicy;
import dev.bee.kanjianki.sync.SyncSettings;

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
}
