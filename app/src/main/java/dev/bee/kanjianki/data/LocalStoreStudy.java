package dev.bee.kanjianki.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.core.SimilarKanjiChoicePlanner;
import dev.bee.kanjianki.core.SimilarKanjiIndex;
import dev.bee.kanjianki.core.TextUtil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

abstract class LocalStoreStudy extends LocalStoreHistory {
    private static final String KEY_STUDY_LADDER_ORDER = "study_ladder_order";
    private static final String KEY_STUDY_LADDER_ENABLED = "study_ladder_enabled";

    LocalStoreStudy(Context context) {
        super(context);
    }

    public void replaceStudyItems(List<Records.StudyItem> items) {
        replaceStudyItems(items, null, 0L, null);
    }

    public void replaceStudyItems(List<Records.StudyItem> items, Long syncId, long occurredAt, Records.Settings settings) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Map<String, StudySnapshot> previous = syncId == null ? Collections.emptyMap() : studySnapshots(db);
            db.delete(TABLE_STUDY_ITEMS, null, null);
            for (Records.StudyItem item : items) {
                upsertStudyItem(db, item);
            }
            if (syncId != null) {
                appendStudyStateTimelineEvents(db, previous, items, syncId, occurredAt, settings);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void saveStudyItem(Records.StudyItem item) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            upsertStudyItem(db, item);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void saveReview(Records.ReviewRequest request, String appliedRating, long reviewedAt) {
        saveReview(request, appliedRating, reviewedAt, null, null);
    }

    public void saveReview(Records.ReviewRequest request, String appliedRating, long reviewedAt, Records.StudyItem beforeReview, Records.StudyItem afterReview) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long inserted = insertReview(db, request, appliedRating, reviewedAt, beforeReview, afterReview);
            if (inserted != -1L) {
                appendReviewTimelineEvent(db, request, appliedRating, reviewedAt, "review:" + request.token);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    long insertReview(SQLiteDatabase db, Records.ReviewRequest request, String appliedRating, long reviewedAt, Records.StudyItem beforeReview, Records.StudyItem afterReview) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_KANJI, request.kanji);
        values.put(COLUMN_TOKEN, request.token);
        values.put(COLUMN_RATING, appliedRating);
        values.put(COLUMN_WRITING_REQUIRED, request.writingRequired ? 1 : 0);
        values.put(COLUMN_WRITING_PASSED, request.writingPassed ? 1 : 0);
        values.put(COLUMN_MANUAL_OVERRIDE, request.manualOverride ? 1 : 0);
        values.put(COLUMN_REVIEWED_AT, reviewedAt);
        values.put(COLUMN_REVIEW_DAY_START, localDayStart(reviewedAt));
        values.put(COLUMN_TASK_TYPE, request.taskType);
        values.put(COLUMN_ANSWER_SIGNATURE, request.answerSignature);
        values.put("prompt", request.prompt);
        values.put("hints_used", request.hintsUsed);
        values.put("writing_clean", request.writingClean ? 1 : 0);
        values.put("memory_before", taskMemoryText(beforeReview, request.taskType));
        values.put("memory_after", taskMemoryText(afterReview, request.taskType));
        values.put("scheduler_state_after_json", studyItemSchedulerJson(afterReview));
        return db.insertWithOnConflict(TABLE_REVIEW_LOG, null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    String taskMemoryText(Records.StudyItem item, String taskType) {
        if (item == null || taskType == null || taskType.isEmpty()) {
            return "";
        }
        return item.memoryForTaskType(taskType).encode();
    }

    String studyItemSchedulerJson(Records.StudyItem item) {
        if (item == null) {
            return "";
        }
        return "{"
                + "\"state\":" + TextUtil.jsonQuote(item.state)
                + ",\"due_at\":" + item.dueAtMillis
                + ",\"stability\":" + item.stability
                + ",\"difficulty\":" + item.difficulty
                + ",\"total_reviews\":" + item.totalReviews
                + ",\"lapses\":" + item.lapses
                + ",\"learning_step\":" + item.learningStep
                + ",\"writing_level\":" + item.writingLevel
                + ",\"recognition_stage\":" + item.recognitionStage
                + ",\"writing_remediation_pending\":" + (item.writingRemediationPending ? "true" : "false")
                + ",\"mature_interval_days\":" + item.matureIntervalDays
                + "}";
    }

    public List<String> consumedTokens() {
        List<String> tokens = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(TABLE_REVIEW_LOG, new String[]{COLUMN_TOKEN}, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                tokens.add(string(cursor, COLUMN_TOKEN));
            }
        }
        return tokens;
    }

    public SyncStatus latestSync() {
        try (Cursor cursor = getReadableDatabase().query(TABLE_SYNC_RUNS, null, null, null, null, null, ORDER_ID_DESC, "1")) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new SyncStatus(new SyncStatusValues(
                    string(cursor, COLUMN_STATUS),
                    integer(cursor, COLUMN_ACTIVE_NOTES_COUNT),
                    integer(cursor, COLUMN_ACTIVE_CARDS_COUNT),
                    integer(cursor, COLUMN_SUSPENDED_CARDS_ARCHIVED_COUNT),
                    integer(cursor, COLUMN_SUSPENDED_KANJI_IMPORTED_COUNT),
                    longValue(cursor, COLUMN_FINISHED_AT),
                    string(cursor, COLUMN_ERROR_MESSAGE),
                    string(cursor, COLUMN_REMOVAL_MESSAGE)
            ));
        }
    }

    public boolean hasSuccessfulSyncSince(long finishedAtMillis) {
        try (Cursor cursor = getReadableDatabase().query(
                TABLE_SYNC_RUNS,
                new String[]{"id"},
                "status=? AND finished_at>=?",
                new String[]{STATUS_SUCCESS, Long.toString(finishedAtMillis)},
                null,
                null,
                ORDER_ID_DESC,
                "1"
        )) {
            return cursor.moveToFirst();
        }
    }

    public int getIntSetting(String key, int fallback) {
        try (Cursor cursor = getReadableDatabase().query(TABLE_SETTINGS, new String[]{COLUMN_VALUE}, WHERE_SETTING_KEY, new String[]{key}, null, null, null, "1")) {
            if (!cursor.moveToFirst()) {
                return fallback;
            }
            return SettingValueParser.parseInt(string(cursor, COLUMN_VALUE), fallback);
        }
    }

    public long getLongSetting(String key, long fallback) {
        try (Cursor cursor = getReadableDatabase().query(TABLE_SETTINGS, new String[]{COLUMN_VALUE}, WHERE_SETTING_KEY, new String[]{key}, null, null, null, "1")) {
            if (!cursor.moveToFirst()) {
                return fallback;
            }
            return SettingValueParser.parseLong(string(cursor, COLUMN_VALUE), fallback);
        }
    }

    public String getStringSetting(String key, String fallback) {
        try (Cursor cursor = getReadableDatabase().query(TABLE_SETTINGS, new String[]{COLUMN_VALUE}, WHERE_SETTING_KEY, new String[]{key}, null, null, null, "1")) {
            if (!cursor.moveToFirst()) {
                return fallback;
            }
            String value = string(cursor, COLUMN_VALUE);
            return value == null ? fallback : value;
        }
    }

    public double getDoubleSetting(String key, double fallback) {
        try (Cursor cursor = getReadableDatabase().query(TABLE_SETTINGS, new String[]{COLUMN_VALUE}, WHERE_SETTING_KEY, new String[]{key}, null, null, null, "1")) {
            if (!cursor.moveToFirst()) {
                return fallback;
            }
            return SettingValueParser.parseDouble(string(cursor, COLUMN_VALUE), fallback);
        }
    }

    public void putIntSetting(String key, int value) {
        putSetting(key, Integer.toString(value));
    }

    public void putLongSetting(String key, long value) {
        putSetting(key, Long.toString(value));
    }

    public void putStringSetting(String key, String value) {
        putSetting(key, value == null ? "" : value);
    }

    public void putDoubleSetting(String key, double value) {
        putSetting(key, String.format(Locale.ROOT, "%.4f", value));
    }

    public int adaptiveLoadWorkPercent() {
        return AdaptiveLoadPlanner.snapWorkloadPercent(getIntSetting(
                AdaptiveLoadPlanner.SETTING_KEY,
                AdaptiveLoadPlanner.DEFAULT_WORKLOAD_PERCENT
        ));
    }

    public void saveAdaptiveLoadWorkPercent(int percent) {
        putIntSetting(AdaptiveLoadPlanner.SETTING_KEY, AdaptiveLoadPlanner.snapWorkloadPercent(percent));
    }

    public int studyAheadMinutes() {
        return clampStudyAheadMinutes(getIntSetting(SETTING_STUDY_AHEAD_MINUTES, DEFAULT_STUDY_AHEAD_MINUTES));
    }

    public void saveStudyAheadMinutes(int minutes) {
        putIntSetting(SETTING_STUDY_AHEAD_MINUTES, clampStudyAheadMinutes(minutes));
    }

    public Records.StudyLadderSettings studyLadderSettings() {
        return Records.StudyLadderSettings.fromStored(
                getStringSetting(KEY_STUDY_LADDER_ORDER, ""),
                getStringSetting(KEY_STUDY_LADDER_ENABLED, "")
        );
    }

    public void saveStudyLadderSettings(Records.StudyLadderSettings settings) {
        Records.StudyLadderSettings normalized = settings == null ? Records.StudyLadderSettings.defaults() : settings;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            putStringSetting(KEY_STUDY_LADDER_ORDER, normalized.orderText());
            putStringSetting(KEY_STUDY_LADDER_ENABLED, normalized.enabledText());
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    static int clampStudyAheadMinutes(int minutes) {
        if (minutes <= 0) {
            return 0;
        }
        return Math.min(minutes, MAX_STUDY_AHEAD_MINUTES);
    }

    public int adaptiveLoadMaxItems() {
        return AdaptiveLoadPlanner.normalizeMaxItems(getIntSetting(
                "adaptive_load_max_items",
                AdaptiveLoadPlanner.DEFAULT_MAX_ITEMS
        ));
    }

    public void saveAdaptiveLoadMaxItems(int maxItems) {
        putIntSetting("adaptive_load_max_items", AdaptiveLoadPlanner.normalizeMaxItems(maxItems));
    }

    public String adaptiveLoadMode() {
        return AdaptiveLoadPlanner.normalizeWorkloadMode(getStringSetting(
                AdaptiveLoadPlanner.MODE_SETTING_KEY,
                AdaptiveLoadPlanner.DEFAULT_WORKLOAD_MODE
        ));
    }

    public void saveAdaptiveLoadMode(String mode) {
        putStringSetting(AdaptiveLoadPlanner.MODE_SETTING_KEY, AdaptiveLoadPlanner.normalizeWorkloadMode(mode));
    }

    public ReminderSettings reminderSettings() {
        return new ReminderSettings(
                getIntSetting("reminder_enabled", 0) == 1,
                getIntSetting("reminder_hour", DEFAULT_REMINDER_HOUR),
                getIntSetting("reminder_minute", DEFAULT_REMINDER_MINUTE)
        ).normalized();
    }

    public void saveReminderSettings(ReminderSettings settings) {
        ReminderSettings normalized = settings.normalized();
        SQLiteDatabase db = getWritableDatabase();
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

    public AutoSyncSettings autoSyncSettings() {
        return new AutoSyncSettings(
                getIntSetting("auto_sync_configured", 0) == 1,
                getIntSetting("auto_sync_enabled", 0) == 1,
                getIntSetting("auto_sync_hour", DEFAULT_AUTO_SYNC_HOUR),
                getIntSetting("auto_sync_minute", DEFAULT_AUTO_SYNC_MINUTE),
                getLongSetting(KEY_AUTO_SYNC_LAST_ATTEMPT_AT, 0L),
                getLongSetting(KEY_AUTO_SYNC_LAST_SUCCESS_AT, 0L),
                getLongSetting(KEY_AUTO_SYNC_NEXT_RUN_AT, 0L)
        ).normalized();
    }

    public boolean activateAutoSyncAfterFirstSuccess() {
        AutoSyncSettings current = autoSyncSettings();
        if (current.configured) {
            return false;
        }
        saveAutoSyncSettings(new AutoSyncSettings(true, true, current.hour, current.minute, current.lastAttemptAt, current.lastSuccessAt, current.nextRunAt));
        return true;
    }

    public void setAutoSyncEnabled(boolean enabled) {
        AutoSyncSettings current = autoSyncSettings();
        saveAutoSyncSettings(new AutoSyncSettings(true, enabled, current.hour, current.minute, current.lastAttemptAt, current.lastSuccessAt, current.nextRunAt));
    }

    public void markAutoSyncScheduled(long nextRunAt) {
        putLongSetting(KEY_AUTO_SYNC_NEXT_RUN_AT, nextRunAt);
    }

    public void recordAutoSyncAttempt(long attemptedAt, boolean success) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            putLongSetting(KEY_AUTO_SYNC_LAST_ATTEMPT_AT, attemptedAt);
            if (success) {
                putLongSetting(KEY_AUTO_SYNC_LAST_SUCCESS_AT, attemptedAt);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void saveAutoSyncSettings(AutoSyncSettings settings) {
        AutoSyncSettings normalized = settings.normalized();
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            putIntSetting("auto_sync_configured", normalized.configured ? 1 : 0);
            putIntSetting("auto_sync_enabled", normalized.enabled ? 1 : 0);
            putIntSetting("auto_sync_hour", normalized.hour);
            putIntSetting("auto_sync_minute", normalized.minute);
            putLongSetting(KEY_AUTO_SYNC_LAST_ATTEMPT_AT, normalized.lastAttemptAt);
            putLongSetting(KEY_AUTO_SYNC_LAST_SUCCESS_AT, normalized.lastSuccessAt);
            putLongSetting(KEY_AUTO_SYNC_NEXT_RUN_AT, normalized.nextRunAt);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public AutoUpdateStatus autoUpdateStatus() {
        return new AutoUpdateStatus(
                getIntSetting(KEY_AUTO_UPDATE_ENABLED, 1) == 1,
                getLongSetting(KEY_AUTO_UPDATE_LAST_CHECK_AT, 0L),
                getStringSetting(KEY_AUTO_UPDATE_LAST_RESULT, "No automatic update check has run yet."),
                getStringSetting(KEY_AUTO_UPDATE_LAST_VERSION, ""),
                getStringSetting(KEY_AUTO_UPDATE_PENDING_APK, ""),
                getStringSetting(KEY_AUTO_UPDATE_PENDING_MESSAGE, "")
        );
    }

    public void saveAutoUpdateEnabled(boolean enabled) {
        putIntSetting(KEY_AUTO_UPDATE_ENABLED, enabled ? 1 : 0);
    }

    public void recordAutoUpdateResult(long checkedAt, String result, String version, String pendingApkName, String pendingMessage) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            putLongSetting(KEY_AUTO_UPDATE_LAST_CHECK_AT, checkedAt);
            putStringSetting(KEY_AUTO_UPDATE_LAST_RESULT, result == null ? "" : result);
            putStringSetting(KEY_AUTO_UPDATE_LAST_VERSION, version == null ? "" : version);
            putStringSetting(KEY_AUTO_UPDATE_PENDING_APK, pendingApkName == null ? "" : pendingApkName);
            putStringSetting(KEY_AUTO_UPDATE_PENDING_MESSAGE, pendingMessage == null ? "" : pendingMessage);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void clearPendingAutoUpdate(String result) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            putStringSetting(KEY_AUTO_UPDATE_LAST_RESULT, result == null ? "" : result);
            putStringSetting(KEY_AUTO_UPDATE_PENDING_APK, "");
            putStringSetting(KEY_AUTO_UPDATE_PENDING_MESSAGE, "");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public Records.SchedulerParameters schedulerParameters() {
        Records.SchedulerParameters defaults = Records.SchedulerParameters.defaults();
        return new Records.SchedulerParameters(
                getDoubleSetting("scheduler_target_retention", defaults.targetRetention),
                getDoubleSetting("scheduler_again_multiplier", defaults.againMultiplier),
                getDoubleSetting("scheduler_hard_multiplier", defaults.hardMultiplier),
                getDoubleSetting("scheduler_good_multiplier", defaults.goodMultiplier),
                getDoubleSetting("scheduler_easy_multiplier", defaults.easyMultiplier),
                getLongSetting("scheduler_last_adjusted_at", defaults.lastAdjustedAtMillis),
                getIntSetting("scheduler_last_adjustment_review_count", defaults.lastAdjustmentReviewCount),
                getIntSetting("scheduler_frequency_retention_enabled", defaults.frequencyRetentionEnabled ? 1 : 0) == 1,
                getStringSetting("scheduler_frequency_retention_ranges", defaults.frequencyRetentionRanges)
        );
    }

    public void saveSchedulerParameters(Records.SchedulerParameters parameters) {
        SQLiteDatabase db = getWritableDatabase();
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

    public Records.LearningStepSettings learningStepSettings() {
        Records.LearningStepSettings defaults = Records.LearningStepSettings.defaults();
        List<Integer> newSteps = Records.LearningStepSettings.parseSteps(
                getStringSetting("new_learning_steps_minutes", defaults.newStepsText()),
                defaults.newStepsMinutes
        );
        List<Integer> reviewSteps = Records.LearningStepSettings.parseSteps(
                getStringSetting("review_relearning_steps_minutes", defaults.reviewStepsText()),
                defaults.reviewStepsMinutes
        );
        return new Records.LearningStepSettings(newSteps, reviewSteps);
    }

    public void saveLearningStepSettings(Records.LearningStepSettings settings) {
        Records.LearningStepSettings normalized = settings == null ? Records.LearningStepSettings.defaults() : settings;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            putStringSetting("new_learning_steps_minutes", normalized.newStepsText());
            putStringSetting("review_relearning_steps_minutes", normalized.reviewStepsText());
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void saveLearningRepeat(Records.LearningRepeat repeat) {
        if (repeat == null || repeat.kanji.isEmpty() || repeat.taskType.isEmpty()) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put(COLUMN_KANJI, repeat.kanji);
        values.put(COLUMN_ANSWER_SIGNATURE, repeat.answerSignature);
        values.put(COLUMN_TASK_TYPE, repeat.taskType);
        values.put("repeat_type", repeat.repeatType);
        values.put("step_index", repeat.stepIndex);
        values.put(COLUMN_DUE_AT, repeat.dueAtMillis);
        values.put(COLUMN_ACTIVE_TOKEN, repeat.activeToken);
        values.put(COLUMN_CREATED_AT, repeat.createdAtMillis);
        values.put(COLUMN_UPDATED_AT, repeat.updatedAtMillis);
        getWritableDatabase().insertWithOnConflict(TABLE_LEARNING_REPEATS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void enqueueLearningRepeat(Records.StudyItem item, String taskType, String repeatType, int stepIndex, long dueAtMillis, long nowMillis) {
        if (item == null || taskType == null || taskType.isEmpty()) {
            return;
        }
        saveLearningRepeat(new Records.LearningRepeat(
                item.kanji,
                item.answerSignature,
                taskType,
                repeatType,
                stepIndex,
                dueAtMillis,
                "",
                nowMillis,
                nowMillis
        ));
    }

    public void clearLearningRepeat(Records.LearningRepeat repeat) {
        if (repeat == null) {
            return;
        }
        getWritableDatabase().delete(
                TABLE_LEARNING_REPEATS,
                "kanji=? AND answer_signature=? AND task_type=?",
                new String[]{repeat.kanji, repeat.answerSignature, repeat.taskType}
        );
    }

    public List<Records.LearningRepeat> dueLearningRepeats(long nowMillis) {
        List<Records.LearningRepeat> repeats = new ArrayList<>();
        Cursor cursor = getReadableDatabase().query(
                TABLE_LEARNING_REPEATS,
                null,
                "due_at<=?",
                new String[]{Long.toString(nowMillis)},
                null,
                null,
                "due_at ASC, updated_at ASC"
        );
        try {
            while (cursor.moveToNext()) {
                repeats.add(readLearningRepeat(cursor));
            }
        } finally {
            cursor.close();
        }
        return repeats;
    }

    public Records.ReviewStats reviewStatsSince(long sinceMillis) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT "
                        + "COUNT(*) AS total, "
                        + "COALESCE(SUM(CASE WHEN rating='again' THEN 1 ELSE 0 END), 0) AS again_count, "
                        + "COALESCE(SUM(CASE WHEN rating='hard' THEN 1 ELSE 0 END), 0) AS hard_count, "
                        + "COALESCE(SUM(CASE WHEN rating='easy' THEN 1 ELSE 0 END), 0) AS easy_count, "
                        + "COALESCE(SUM(CASE WHEN rating NOT IN ('again', 'hard', 'easy') THEN 1 ELSE 0 END), 0) AS good_count, "
                        + "COALESCE(SUM(CASE WHEN writing_required=1 THEN 1 ELSE 0 END), 0) AS writing_required_count, "
                        + "COALESCE(SUM(CASE WHEN writing_required=1 AND writing_passed=0 AND manual_override=0 THEN 1 ELSE 0 END), 0) AS writing_failed_count "
                        + "FROM " + TABLE_REVIEW_LOG + " WHERE reviewed_at>=?",
                new String[]{Long.toString(sinceMillis)}
        );
        try {
            cursor.moveToFirst();
            return new Records.ReviewStats(
                    cursor.getInt(0),
                    cursor.getInt(1),
                    cursor.getInt(2),
                    cursor.getInt(4),
                    cursor.getInt(3),
                    cursor.getInt(5),
                    cursor.getInt(6)
            );
        } finally {
            cursor.close();
        }
    }

    public boolean recordStudyTaskAnswered(String taskKey, String kanji, String taskType, long startedAt, long answeredAt, long activeElapsedMillis, String outcome) {
        String normalizedKey = taskKey == null ? "" : taskKey;
        if (normalizedKey.isEmpty()) {
            return false;
        }
        ContentValues values = new ContentValues();
        values.put("task_key", normalizedKey);
        values.put(COLUMN_KANJI, kanji == null ? "" : kanji);
        values.put(COLUMN_TASK_TYPE, taskType == null ? "" : taskType);
        values.put(COLUMN_STARTED_AT, Math.max(0L, startedAt));
        values.put("answered_at", Math.max(0L, answeredAt));
        values.put("active_elapsed_ms", Math.min(MAX_STUDY_TASK_ELAPSED_MS, Math.max(0L, activeElapsedMillis)));
        values.put("outcome", outcome == null ? "" : outcome);
        return getWritableDatabase().insertWithOnConflict(TABLE_STUDY_TASK_LOG, null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L;
    }

    public StudyStatsStore.StudyTaskTimeStats studyTaskTimeStats(long nowMillis) {
        return new StudyStatsStore((LocalStore) this).studyTaskTimeStats(nowMillis);
    }

    public List<StudyStatsStore.RecentMistake> recentMistakes(int limit) {
        return new StudyStatsStore((LocalStore) this).recentMistakes(limit);
    }

    public StudyStatsStore.StudyStreak studyStreak(long nowMillis) {
        return new StudyStatsStore((LocalStore) this).studyStreak(nowMillis);
    }

    public StudyStatsStore.StudyImpactStats studyImpactStats() {
        return new StudyStatsStore((LocalStore) this).studyImpactStats();
    }

    public StudyStatsStore.KaniOutcomeStats kaniOutcomeStats() {
        return new StudyStatsStore((LocalStore) this).kaniOutcomeStats();
    }

    public Set<String> studiedKanjiSince(long sinceMillis) {
        Cursor cursor = getReadableDatabase().query(
                true,
                TABLE_REVIEW_LOG,
                new String[]{COLUMN_KANJI},
                "reviewed_at>=?",
                new String[]{Long.toString(sinceMillis)},
                null,
                null,
                null,
                null
        );
        Set<String> kanji = new HashSet<>();
        try {
            while (cursor.moveToNext()) {
                kanji.add(string(cursor, COLUMN_KANJI));
            }
        } finally {
            cursor.close();
        }
        return kanji;
    }

    private static long localDayStart(long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}
