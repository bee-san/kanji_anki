package dev.bee.kanjianki;

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

    interface IntSettingWriter {
        void putIntSetting(String key, int value);
    }
}
