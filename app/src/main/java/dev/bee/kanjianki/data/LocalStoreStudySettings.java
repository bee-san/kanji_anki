package dev.bee.kanjianki.data;

import android.database.sqlite.SQLiteDatabase;

import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.SettingsInputRules;
import dev.bee.kanjianki.core.TimeOfDaySettingsPolicy;
import dev.bee.kanjianki.updatecore.AutoUpdateStatusPolicy;

import java.util.List;

final class LocalStoreStudySettings {
    private static final String KEY_STUDY_LADDER_ORDER = "study_ladder_order";
    private static final String KEY_STUDY_LADDER_ENABLED = "study_ladder_enabled";

    private final LocalStoreStudy store;

    LocalStoreStudySettings(LocalStoreStudy store) {
        this.store = store;
    }

    int getIntSetting(String key, int fallback) {
        return store.settingsRepository().getInt(key, fallback);
    }

    long getLongSetting(String key, long fallback) {
        return store.settingsRepository().getLong(key, fallback);
    }

    String getStringSetting(String key, String fallback) {
        return store.settingsRepository().getString(key, fallback);
    }

    double getDoubleSetting(String key, double fallback) {
        return store.settingsRepository().getDouble(key, fallback);
    }

    void putIntSetting(String key, int value) {
        store.settingsRepository().putInt(key, value);
    }

    void putLongSetting(String key, long value) {
        store.settingsRepository().putLong(key, value);
    }

    void putStringSetting(String key, String value) {
        store.settingsRepository().putString(key, value);
    }

    void putDoubleSetting(String key, double value) {
        store.settingsRepository().putDouble(key, value);
    }

    int adaptiveLoadWorkPercent() {
        return AdaptiveLoadPlanner.snapWorkloadPercent(getIntSetting(
                AdaptiveLoadPlanner.SETTING_KEY,
                AdaptiveLoadPlanner.DEFAULT_WORKLOAD_PERCENT
        ));
    }

    void saveAdaptiveLoadWorkPercent(int percent) {
        putIntSetting(AdaptiveLoadPlanner.SETTING_KEY, AdaptiveLoadPlanner.snapWorkloadPercent(percent));
    }

    int studyAheadMinutes() {
        return SettingsInputRules.normalizeStudyAheadMinutes(getIntSetting(
                LocalStoreBase.SETTING_STUDY_AHEAD_MINUTES,
                SettingsInputRules.DEFAULT_STUDY_AHEAD_MINUTES
        ));
    }

    void saveStudyAheadMinutes(int minutes) {
        putIntSetting(LocalStoreBase.SETTING_STUDY_AHEAD_MINUTES, SettingsInputRules.normalizeStudyAheadMinutes(minutes));
    }

    RecordsBase.StudyLadderSettings studyLadderSettings() {
        return RecordsBase.StudyLadderSettings.fromStored(
                getStringSetting(KEY_STUDY_LADDER_ORDER, ""),
                getStringSetting(KEY_STUDY_LADDER_ENABLED, "")
        );
    }

    void saveStudyLadderSettings(RecordsBase.StudyLadderSettings settings) {
        RecordsBase.StudyLadderSettings normalized = settings == null ? RecordsBase.StudyLadderSettings.defaults() : settings;
        SQLiteDatabase db = store.getWritableDatabase();
        db.beginTransaction();
        try {
            putStringSetting(KEY_STUDY_LADDER_ORDER, normalized.orderText());
            putStringSetting(KEY_STUDY_LADDER_ENABLED, normalized.enabledText());
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    int adaptiveLoadMaxItems() {
        return AdaptiveLoadPlanner.normalizeMaxItems(getIntSetting(
                "adaptive_load_max_items",
                AdaptiveLoadPlanner.DEFAULT_MAX_ITEMS
        ));
    }

    void saveAdaptiveLoadMaxItems(int maxItems) {
        putIntSetting("adaptive_load_max_items", AdaptiveLoadPlanner.normalizeMaxItems(maxItems));
    }

    String adaptiveLoadMode() {
        return AdaptiveLoadPlanner.normalizeWorkloadMode(getStringSetting(
                AdaptiveLoadPlanner.MODE_SETTING_KEY,
                AdaptiveLoadPlanner.DEFAULT_WORKLOAD_MODE
        ));
    }

    void saveAdaptiveLoadMode(String mode) {
        putStringSetting(AdaptiveLoadPlanner.MODE_SETTING_KEY, AdaptiveLoadPlanner.normalizeWorkloadMode(mode));
    }

    LocalStoreBase.ReminderSettings reminderSettings() {
        return new LocalStoreBase.ReminderSettings(
                getIntSetting("reminder_enabled", 0) == 1,
                getIntSetting("reminder_hour", TimeOfDaySettingsPolicy.DEFAULT_REMINDER_HOUR),
                getIntSetting("reminder_minute", TimeOfDaySettingsPolicy.DEFAULT_REMINDER_MINUTE)
        ).normalized();
    }

    void saveReminderSettings(LocalStoreBase.ReminderSettings settings) {
        LocalStoreBase.ReminderSettings normalized = settings.normalized();
        SQLiteDatabase db = store.getWritableDatabase();
        db.beginTransaction();
        try {
            putIntSetting("reminder_enabled", normalized.enabled ? 1 : 0);
            putIntSetting("reminder_hour", normalized.hour);
            putIntSetting("reminder_minute", normalized.minute);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    LocalStoreBase.AutoSyncSettings autoSyncSettings() {
        return new LocalStoreBase.AutoSyncSettings(
                getIntSetting("auto_sync_configured", 0) == 1,
                getIntSetting("auto_sync_enabled", 0) == 1,
                getIntSetting("auto_sync_hour", TimeOfDaySettingsPolicy.DEFAULT_AUTO_SYNC_HOUR),
                getIntSetting("auto_sync_minute", TimeOfDaySettingsPolicy.DEFAULT_AUTO_SYNC_MINUTE),
                getLongSetting(LocalStoreBase.KEY_AUTO_SYNC_LAST_ATTEMPT_AT, 0L),
                getLongSetting(LocalStoreBase.KEY_AUTO_SYNC_LAST_SUCCESS_AT, 0L),
                getLongSetting(LocalStoreBase.KEY_AUTO_SYNC_NEXT_RUN_AT, 0L)
        ).normalized();
    }

    boolean activateAutoSyncAfterFirstSuccess() {
        LocalStoreBase.AutoSyncSettings current = autoSyncSettings();
        if (current.configured) {
            return false;
        }
        saveAutoSyncSettings(new LocalStoreBase.AutoSyncSettings(true, true, current.hour, current.minute, current.lastAttemptAt, current.lastSuccessAt, current.nextRunAt));
        return true;
    }

    void setAutoSyncEnabled(boolean enabled) {
        LocalStoreBase.AutoSyncSettings current = autoSyncSettings();
        saveAutoSyncSettings(new LocalStoreBase.AutoSyncSettings(true, enabled, current.hour, current.minute, current.lastAttemptAt, current.lastSuccessAt, current.nextRunAt));
    }

    void markAutoSyncScheduled(long nextRunAt) {
        putLongSetting(LocalStoreBase.KEY_AUTO_SYNC_NEXT_RUN_AT, nextRunAt);
    }

    void recordAutoSyncAttempt(long attemptedAt, boolean success) {
        SQLiteDatabase db = store.getWritableDatabase();
        db.beginTransaction();
        try {
            putLongSetting(LocalStoreBase.KEY_AUTO_SYNC_LAST_ATTEMPT_AT, attemptedAt);
            if (success) {
                putLongSetting(LocalStoreBase.KEY_AUTO_SYNC_LAST_SUCCESS_AT, attemptedAt);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    void saveAutoSyncSettings(LocalStoreBase.AutoSyncSettings settings) {
        LocalStoreBase.AutoSyncSettings normalized = settings.normalized();
        SQLiteDatabase db = store.getWritableDatabase();
        db.beginTransaction();
        try {
            putIntSetting("auto_sync_configured", normalized.configured ? 1 : 0);
            putIntSetting("auto_sync_enabled", normalized.enabled ? 1 : 0);
            putIntSetting("auto_sync_hour", normalized.hour);
            putIntSetting("auto_sync_minute", normalized.minute);
            putLongSetting(LocalStoreBase.KEY_AUTO_SYNC_LAST_ATTEMPT_AT, normalized.lastAttemptAt);
            putLongSetting(LocalStoreBase.KEY_AUTO_SYNC_LAST_SUCCESS_AT, normalized.lastSuccessAt);
            putLongSetting(LocalStoreBase.KEY_AUTO_SYNC_NEXT_RUN_AT, normalized.nextRunAt);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    LocalStoreBase.AutoUpdateStatus autoUpdateStatus() {
        return new LocalStoreBase.AutoUpdateStatus(
                getIntSetting(LocalStoreBase.KEY_AUTO_UPDATE_ENABLED, 1) == 1,
                getLongSetting(LocalStoreBase.KEY_AUTO_UPDATE_LAST_CHECK_AT, 0L),
                getStringSetting(LocalStoreBase.KEY_AUTO_UPDATE_LAST_RESULT, AutoUpdateStatusPolicy.DEFAULT_LAST_RESULT),
                getStringSetting(LocalStoreBase.KEY_AUTO_UPDATE_LAST_VERSION, ""),
                getStringSetting(LocalStoreBase.KEY_AUTO_UPDATE_PENDING_APK, ""),
                getStringSetting(LocalStoreBase.KEY_AUTO_UPDATE_PENDING_MESSAGE, "")
        );
    }

    void saveAutoUpdateEnabled(boolean enabled) {
        putIntSetting(LocalStoreBase.KEY_AUTO_UPDATE_ENABLED, enabled ? 1 : 0);
    }

    void recordAutoUpdateResult(long checkedAt, String result, String version, String pendingApkName, String pendingMessage) {
        SQLiteDatabase db = store.getWritableDatabase();
        db.beginTransaction();
        try {
            putLongSetting(LocalStoreBase.KEY_AUTO_UPDATE_LAST_CHECK_AT, checkedAt);
            putStringSetting(LocalStoreBase.KEY_AUTO_UPDATE_LAST_RESULT, AutoUpdateStatusPolicy.text(result));
            putStringSetting(LocalStoreBase.KEY_AUTO_UPDATE_LAST_VERSION, AutoUpdateStatusPolicy.text(version));
            putStringSetting(LocalStoreBase.KEY_AUTO_UPDATE_PENDING_APK, AutoUpdateStatusPolicy.text(pendingApkName));
            putStringSetting(LocalStoreBase.KEY_AUTO_UPDATE_PENDING_MESSAGE, AutoUpdateStatusPolicy.text(pendingMessage));
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    void clearPendingAutoUpdate(String result) {
        SQLiteDatabase db = store.getWritableDatabase();
        db.beginTransaction();
        try {
            putStringSetting(LocalStoreBase.KEY_AUTO_UPDATE_LAST_RESULT, AutoUpdateStatusPolicy.text(result));
            putStringSetting(LocalStoreBase.KEY_AUTO_UPDATE_PENDING_APK, "");
            putStringSetting(LocalStoreBase.KEY_AUTO_UPDATE_PENDING_MESSAGE, "");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    RecordsSchedulerModels.SchedulerParameters schedulerParameters() {
        RecordsSchedulerModels.SchedulerParameters defaults = RecordsSchedulerModels.SchedulerParameters.defaults();
        return new RecordsSchedulerModels.SchedulerParameters(
                getDoubleSetting("scheduler_target_retention", defaults.targetRetention),
                getDoubleSetting("scheduler_again_multiplier", defaults.againMultiplier),
                getDoubleSetting("scheduler_hard_multiplier", defaults.hardMultiplier),
                getDoubleSetting("scheduler_good_multiplier", defaults.goodMultiplier),
                getDoubleSetting("scheduler_easy_multiplier", defaults.easyMultiplier),
                getLongSetting("scheduler_last_adjusted_at", defaults.lastAdjustedAtMillis),
                getIntSetting("scheduler_last_adjustment_review_count", defaults.lastAdjustmentReviewCount)
        ).withFrequencyRetention(
                getIntSetting("scheduler_frequency_retention_enabled", defaults.frequencyRetentionEnabled ? 1 : 0) == 1,
                getStringSetting("scheduler_frequency_retention_ranges", defaults.frequencyRetentionRanges));
    }

    void saveSchedulerParameters(RecordsSchedulerModels.SchedulerParameters parameters) {
        SQLiteDatabase db = store.getWritableDatabase();
        db.beginTransaction();
        try {
            putDoubleSetting("scheduler_target_retention", parameters.targetRetention);
            putDoubleSetting("scheduler_again_multiplier", parameters.againMultiplier);
            putDoubleSetting("scheduler_hard_multiplier", parameters.hardMultiplier);
            putDoubleSetting("scheduler_good_multiplier", parameters.goodMultiplier);
            putDoubleSetting("scheduler_easy_multiplier", parameters.easyMultiplier);
            putLongSetting("scheduler_last_adjusted_at", parameters.lastAdjustedAtMillis);
            putIntSetting("scheduler_last_adjustment_review_count", parameters.lastAdjustmentReviewCount);
            putIntSetting("scheduler_frequency_retention_enabled", parameters.frequencyRetentionEnabled ? 1 : 0);
            putStringSetting("scheduler_frequency_retention_ranges", parameters.frequencyRetentionRanges);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    RecordsSchedulerModels.LearningStepSettings learningStepSettings() {
        RecordsSchedulerModels.LearningStepSettings defaults = RecordsSchedulerModels.LearningStepSettings.defaults();
        List<Integer> newSteps = RecordsSchedulerModels.LearningStepSettings.parseSteps(
                getStringSetting("new_learning_steps_minutes", defaults.newStepsText()),
                defaults.newStepsMinutes
        );
        List<Integer> reviewSteps = RecordsSchedulerModels.LearningStepSettings.parseSteps(
                getStringSetting("review_relearning_steps_minutes", defaults.reviewStepsText()),
                defaults.reviewStepsMinutes
        );
        return new RecordsSchedulerModels.LearningStepSettings(newSteps, reviewSteps);
    }

    void saveLearningStepSettings(RecordsSchedulerModels.LearningStepSettings settings) {
        RecordsSchedulerModels.LearningStepSettings normalized = settings == null ? RecordsSchedulerModels.LearningStepSettings.defaults() : settings;
        SQLiteDatabase db = store.getWritableDatabase();
        db.beginTransaction();
        try {
            putStringSetting("new_learning_steps_minutes", normalized.newStepsText());
            putStringSetting("review_relearning_steps_minutes", normalized.reviewStepsText());
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
