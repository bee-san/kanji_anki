package dev.bee.kanjianki;

import dev.bee.kanjianki.core.SettingsImportPreset;
import dev.bee.kanjianki.core.StudyLadderThresholdPolicy;
import dev.bee.kanjianki.sync.SyncSettings;

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

    static void applyImportPreset(SettingsImportPreset preset, SettingWriter writer) {
        writer.putIntSetting(SyncSettings.IMPORT_ACTIVE_CARDS_SETTING_KEY, SettingsImportPreset.boolFlag(preset.activeCards()));
        writer.putIntSetting(SyncSettings.IMPORT_SUSPENDED_CARDS_SETTING_KEY, SettingsImportPreset.boolFlag(preset.suspendedCards()));
        writer.putIntSetting(SyncSettings.IMPORT_TAGGED_CARDS_SETTING_KEY, SettingsImportPreset.boolFlag(preset.taggedCards()));
        writer.putStringSetting(SyncSettings.IMPORT_TAGS_SETTING_KEY, preset.tags());
        writer.putIntSetting(SyncSettings.IMPORT_WEAK_CARDS_SETTING_KEY, SettingsImportPreset.boolFlag(preset.weakCards()));
        writer.putDoubleSetting(SyncSettings.IMPORT_WEAK_FSRS_DIFFICULTY_SETTING_KEY, preset.weakDifficulty());
        writer.putIntSetting(SyncSettings.IMPORT_WEAK_LAPSES_SETTING_KEY, preset.weakLapses());
        writer.putIntSetting(SyncSettings.IMPORT_MIN_MATCHING_CARDS_SETTING_KEY, preset.minMatchingCards());
        writer.putIntSetting(SyncSettings.IMPORT_BROWSER_QUERY_CARDS_SETTING_KEY, SettingsImportPreset.boolFlag(preset.browserQueryCards()));
        writer.putStringSetting(SyncSettings.IMPORT_BROWSER_QUERY_SETTING_KEY, preset.browserQuery());
    }

    static void applyImportPreset(
            SettingsImportPreset preset,
            IntSettingWriter intWriter,
            StringSettingWriter stringWriter,
            DoubleSettingWriter doubleWriter
    ) {
        applyImportPreset(preset, new SettingWriter() {
            @Override
            public void putIntSetting(String key, int value) {
                intWriter.putIntSetting(key, value);
            }

            @Override
            public void putStringSetting(String key, String value) {
                stringWriter.putStringSetting(key, value);
            }

            @Override
            public void putDoubleSetting(String key, double value) {
                doubleWriter.putDoubleSetting(key, value);
            }
        });
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

    interface SettingWriter extends IntSettingWriter {
        void putStringSetting(String key, String value);

        void putDoubleSetting(String key, double value);
    }
}
