package dev.bee.kanjianki.sync;

import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.data.LocalStore;

public final class SyncSettings {
    public static final String NOTE_TYPE_SETTING_KEY = "note_type_name";
    public static final String EXPRESSION_FIELD_SETTING_KEY = "expression_field";
    public static final String READING_FIELD_SETTING_KEY = "reading_field";
    public static final String MEANING_FIELD_SETTING_KEY = "meaning_field";
    public static final String SENTENCE_FIELD_SETTING_KEY = "sentence_field";
    public static final String FREQUENCY_FIELD_SETTING_KEY = "frequency_field";
    public static final String FREQUENCY_SORT_FIELD_SETTING_KEY = "frequency_sort_field";

    private SyncSettings() {
    }

    public static Records.Settings fromStore(LocalStore store) {
        Records.Settings defaults = Records.Settings.kikuDefaults();
        String modelName = store == null
                ? defaults.modelName
                : nonBlank(store.getStringSetting(NOTE_TYPE_SETTING_KEY, defaults.modelName), defaults.modelName);
        String expressionField = fieldSetting(store, EXPRESSION_FIELD_SETTING_KEY, defaults.expressionField, true);
        String readingField = fieldSetting(store, READING_FIELD_SETTING_KEY, defaults.readingField, false);
        String meaningField = fieldSetting(store, MEANING_FIELD_SETTING_KEY, defaults.meaningField, false);
        String sentenceField = fieldSetting(store, SENTENCE_FIELD_SETTING_KEY, defaults.sentenceField, false);
        String frequencyField = fieldSetting(store, FREQUENCY_FIELD_SETTING_KEY, defaults.frequencyField, false);
        String frequencySortField = fieldSetting(store, FREQUENCY_SORT_FIELD_SETTING_KEY, defaults.frequencySortField, false);
        int minRank = store == null
                ? defaults.suspendedRankMin
                : store.getIntSetting("suspended_rank_min", defaults.suspendedRankMin);
        int maxRank = store == null
                ? defaults.suspendedRankMax
                : store.getIntSetting(
                        "suspended_rank_max",
                        store.getIntSetting("suspended_rank_cutoff", defaults.suspendedRankMax)
                );
        return new Records.Settings(
                modelName,
                defaults.templateName,
                expressionField,
                readingField,
                meaningField,
                sentenceField,
                frequencyField,
                frequencySortField,
                defaults.matureDays,
                defaults.matureSupportThreshold,
                minRank,
                maxRank,
                defaults.activeQueueCap,
                defaults.newPerDay,
                defaults.writingTriggerMissDays
        );
    }

    private static String fieldSetting(LocalStore store, String key, String fallback, boolean required) {
        if (store == null) {
            return fallback;
        }
        String value = store.getStringSetting(key, null);
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return required && trimmed.isEmpty() ? fallback : trimmed;
    }

    private static String nonBlank(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }
}
