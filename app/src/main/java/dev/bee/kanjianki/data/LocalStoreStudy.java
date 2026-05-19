package dev.bee.kanjianki.data;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import dev.bee.kanjianki.core.KanjiImpactAnalyzer;
import dev.bee.kanjianki.core.LocalDayPolicy;
import dev.bee.kanjianki.core.TextUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract class LocalStoreStudy extends LocalStoreHistory {
    LocalStoreStudy(Context context) {
        super(context);
    }

    private LocalStoreStudySettings studySettings() {
        return new LocalStoreStudySettings(this);
    }

    private LocalStoreStudyLog studyLog() {
        return new LocalStoreStudyLog(this);
    }

    private LocalStoreStudyStatus studyStatus() {
        return new LocalStoreStudyStatus((LocalStore) this);
    }

    public void replaceStudyItems(List<RecordsStudyModels.StudyItem> items) {
        replaceStudyItems(items, null, 0L, null);
    }

    public void replaceStudyItems(List<RecordsStudyModels.StudyItem> items, Long syncId, long occurredAt, RecordsSyncModels.Settings settings) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Map<String, StudySnapshot> previous = syncId == null ? Collections.emptyMap() : studySnapshots(db);
            db.delete(TABLE_STUDY_ITEMS, null, null);
            for (RecordsStudyModels.StudyItem item : items) {
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

    public void saveStudyItem(RecordsStudyModels.StudyItem item) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            upsertStudyItem(db, item);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void saveReview(RecordsSchedulerModels.ReviewRequest request, String appliedRating, long reviewedAt) {
        saveReview(request, appliedRating, reviewedAt, null, null);
    }

    public void saveReview(RecordsSchedulerModels.ReviewRequest request, String appliedRating, long reviewedAt, RecordsStudyModels.StudyItem beforeReview, RecordsStudyModels.StudyItem afterReview) {
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

    public KanjiImpactAnalyzer.Report kanjiImpactReport() {
        return new KanjiImpactReportStore((LocalStore) this).report();
    }

    long insertReview(SQLiteDatabase db, RecordsSchedulerModels.ReviewRequest request, String appliedRating, long reviewedAt, RecordsStudyModels.StudyItem beforeReview, RecordsStudyModels.StudyItem afterReview) {
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

    String taskMemoryText(RecordsStudyModels.StudyItem item, String taskType) {
        if (item == null || taskType == null || taskType.isEmpty()) {
            return "";
        }
        return item.memoryForTaskType(taskType).encode();
    }

    String studyItemSchedulerJson(RecordsStudyModels.StudyItem item) {
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
        return studyStatus().consumedTokens();
    }

    public SyncStatus latestSync() {
        return studyStatus().latestSync();
    }

    public boolean hasSuccessfulSyncSince(long finishedAtMillis) {
        return studyStatus().hasSuccessfulSyncSince(finishedAtMillis);
    }

    public int getIntSetting(String key, int fallback) {
        return studySettings().getIntSetting(key, fallback);
    }

    public long getLongSetting(String key, long fallback) {
        return studySettings().getLongSetting(key, fallback);
    }

    public String getStringSetting(String key, String fallback) {
        return studySettings().getStringSetting(key, fallback);
    }

    public double getDoubleSetting(String key, double fallback) {
        return studySettings().getDoubleSetting(key, fallback);
    }

    public void putIntSetting(String key, int value) {
        studySettings().putIntSetting(key, value);
    }

    public void putLongSetting(String key, long value) {
        studySettings().putLongSetting(key, value);
    }

    public void putStringSetting(String key, String value) {
        studySettings().putStringSetting(key, value);
    }

    public void putDoubleSetting(String key, double value) {
        studySettings().putDoubleSetting(key, value);
    }

    public int adaptiveLoadWorkPercent() {
        return studySettings().adaptiveLoadWorkPercent();
    }

    public void saveAdaptiveLoadWorkPercent(int percent) {
        studySettings().saveAdaptiveLoadWorkPercent(percent);
    }

    public int studyAheadMinutes() {
        return studySettings().studyAheadMinutes();
    }

    public void saveStudyAheadMinutes(int minutes) {
        studySettings().saveStudyAheadMinutes(minutes);
    }

    public RecordsBase.StudyLadderSettings studyLadderSettings() {
        return studySettings().studyLadderSettings();
    }

    public void saveStudyLadderSettings(RecordsBase.StudyLadderSettings settings) {
        studySettings().saveStudyLadderSettings(settings);
    }

    public int adaptiveLoadMaxItems() {
        return studySettings().adaptiveLoadMaxItems();
    }

    public void saveAdaptiveLoadMaxItems(int maxItems) {
        studySettings().saveAdaptiveLoadMaxItems(maxItems);
    }

    public String adaptiveLoadMode() {
        return studySettings().adaptiveLoadMode();
    }

    public void saveAdaptiveLoadMode(String mode) {
        studySettings().saveAdaptiveLoadMode(mode);
    }

    public ReminderSettings reminderSettings() {
        return studySettings().reminderSettings();
    }

    public void saveReminderSettings(ReminderSettings settings) {
        studySettings().saveReminderSettings(settings);
    }

    public AutoSyncSettings autoSyncSettings() {
        return studySettings().autoSyncSettings();
    }

    public boolean activateAutoSyncAfterFirstSuccess() {
        return studySettings().activateAutoSyncAfterFirstSuccess();
    }

    public void setAutoSyncEnabled(boolean enabled) {
        studySettings().setAutoSyncEnabled(enabled);
    }

    public void markAutoSyncScheduled(long nextRunAt) {
        studySettings().markAutoSyncScheduled(nextRunAt);
    }

    public void recordAutoSyncAttempt(long attemptedAt, boolean success) {
        studySettings().recordAutoSyncAttempt(attemptedAt, success);
    }

    public void saveAutoSyncSettings(AutoSyncSettings settings) {
        studySettings().saveAutoSyncSettings(settings);
    }

    public AutoUpdateStatus autoUpdateStatus() {
        return studySettings().autoUpdateStatus();
    }

    public void saveAutoUpdateEnabled(boolean enabled) {
        studySettings().saveAutoUpdateEnabled(enabled);
    }

    public void recordAutoUpdateResult(long checkedAt, String result, String version, String pendingApkName, String pendingMessage) {
        studySettings().recordAutoUpdateResult(checkedAt, result, version, pendingApkName, pendingMessage);
    }

    public void clearPendingAutoUpdate(String result) {
        studySettings().clearPendingAutoUpdate(result);
    }

    public RecordsSchedulerModels.SchedulerParameters schedulerParameters() {
        return studySettings().schedulerParameters();
    }

    public void saveSchedulerParameters(RecordsSchedulerModels.SchedulerParameters parameters) {
        studySettings().saveSchedulerParameters(parameters);
    }

    public RecordsSchedulerModels.LearningStepSettings learningStepSettings() {
        return studySettings().learningStepSettings();
    }

    public void saveLearningStepSettings(RecordsSchedulerModels.LearningStepSettings settings) {
        studySettings().saveLearningStepSettings(settings);
    }

    public void saveLearningRepeat(RecordsSchedulerModels.LearningRepeat repeat) {
        studyLog().saveLearningRepeat(repeat);
    }

    public void enqueueLearningRepeat(RecordsStudyModels.StudyItem item, String taskType, String repeatType, int stepIndex, long dueAtMillis, long nowMillis) {
        studyLog().enqueueLearningRepeat(item, taskType, repeatType, stepIndex, dueAtMillis, nowMillis);
    }

    public void clearLearningRepeat(RecordsSchedulerModels.LearningRepeat repeat) {
        studyLog().clearLearningRepeat(repeat);
    }

    public List<RecordsSchedulerModels.LearningRepeat> dueLearningRepeats(long nowMillis) {
        return studyLog().dueLearningRepeats(nowMillis);
    }

    public RecordsSchedulerModels.ReviewStats reviewStatsSince(long sinceMillis) {
        return new StudyStatsStore((LocalStore) this).reviewStatsSince(sinceMillis);
    }

    public boolean recordStudyTaskAnswered(String taskKey, String kanji, String taskType, long startedAt, long answeredAt, long activeElapsedMillis, String outcome) {
        return studyLog().recordStudyTaskAnswered(taskKey, kanji, taskType, startedAt, answeredAt, activeElapsedMillis, outcome);
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
        return new StudyStatsStore((LocalStore) this).studiedKanjiSince(sinceMillis);
    }

    private static long localDayStart(long millis) {
        return LocalDayPolicy.localDayStart(millis);
    }
}
