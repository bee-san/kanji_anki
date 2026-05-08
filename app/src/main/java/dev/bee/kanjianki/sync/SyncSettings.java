package dev.bee.kanjianki.sync;

import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.data.LocalStore;

public final class SyncSettings {
    public static final String NOTE_TYPE_SETTING_KEY = "note_type_name";

    private SyncSettings() {
    }

    public static Records.Settings fromStore(LocalStore store) {
        Records.Settings defaults = Records.Settings.kikuDefaults();
        String modelName = store == null
                ? defaults.modelName
                : nonBlank(store.getStringSetting(NOTE_TYPE_SETTING_KEY, defaults.modelName), defaults.modelName);
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
                defaults.expressionField,
                defaults.readingField,
                defaults.meaningField,
                defaults.sentenceField,
                defaults.frequencyField,
                defaults.frequencySortField,
                defaults.matureDays,
                defaults.matureSupportThreshold,
                minRank,
                maxRank,
                defaults.activeQueueCap,
                defaults.newPerDay,
                defaults.writingTriggerMissDays
        );
    }

    private static String nonBlank(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }
}
