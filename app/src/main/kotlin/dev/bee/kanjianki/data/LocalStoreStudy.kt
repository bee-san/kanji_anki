package dev.bee.kanjianki.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.core.NewCardSortSettingsPolicy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.TextUtil
import dev.bee.kanjianki.StudyReviewActions
import dev.bee.kanjianki.theme.KaniThemeChoice

internal abstract class LocalStoreStudy(context: Context?) : LocalStoreHistory(context) {
    private fun studySettings(): LocalStoreStudySettings = LocalStoreStudySettings(this)

    private fun studyLog(): LocalStoreStudyLog = LocalStoreStudyLog(this)

    private fun studyStatus(): LocalStoreStudyStatus = LocalStoreStudyStatus(this as LocalStore)

    fun replaceStudyItems(items: List<RecordsStudyModels.StudyItem>) {
        replaceStudyItems(items, null, 0L, null)
    }

    fun replaceStudyItems(
        items: List<RecordsStudyModels.StudyItem>,
        syncId: Long?,
        occurredAt: Long,
        settings: RecordsSyncModels.Settings?,
    ) {
        val start = android.os.SystemClock.elapsedRealtime()
        writableDatabase.transaction {
            val previous = if (syncId == null) emptyMap() else studySnapshots(this)
            delete(TABLE_STUDY_ITEMS, null, null)
            for (item in items) {
                upsertStudyItem(this, item)
            }
            if (syncId != null) {
                appendStudyStateTimelineEvents(this, previous, items, syncId, occurredAt, settings)
            }
            StatsCacheStore(this@LocalStoreStudy as LocalStore).markDirty(this)
            clearStudyItemsCache()
        }
        dev.bee.kanjianki.studyLoadDebug(
            "replaceStudyItems WROTE count=${items.size} (delete-all + reinsert) " +
                "duration_ms=${android.os.SystemClock.elapsedRealtime() - start}"
        )
    }
    fun studySnapshots(db: SQLiteDatabase): Map<String, StudySnapshot> {
        val items = HashMap<String, StudySnapshot>()
        db.query(TABLE_STUDY_ITEMS, arrayOf(COLUMN_KANJI, COLUMN_ANSWER_SIGNATURE, COLUMN_STATE), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val kanji = string(cursor, COLUMN_KANJI)
                val answerSignature = string(cursor, COLUMN_ANSWER_SIGNATURE)
                items[studyFamilyKey(kanji, answerSignature)] = StudySnapshot(string(cursor, COLUMN_STATE))
            }
        }
        return items
    }

    fun saveStudyItem(item: RecordsStudyModels.StudyItem) {
        writableDatabase.transaction {
            upsertStudyItem(this, item)
            StatsCacheStore(this@LocalStoreStudy as LocalStore).markDirty(this)
            clearStudyItemsCache()
        }
    }

    fun saveReview(request: RecordsSchedulerModels.ReviewRequest, appliedRating: String?, reviewedAt: Long) {
        saveReview(request, appliedRating, reviewedAt, null, null)
    }

    fun saveReview(
        request: RecordsSchedulerModels.ReviewRequest,
        appliedRating: String?,
        reviewedAt: Long,
        beforeReview: RecordsStudyModels.StudyItem? = null,
        afterReview: RecordsStudyModels.StudyItem? = null,
    ) {
        writableDatabase.transaction {
            val inserted = insertReview(this, request, appliedRating, reviewedAt, beforeReview, afterReview)
            if (inserted != -1L) {
                appendReviewTimelineEvent(this, request, appliedRating, reviewedAt, "review:" + request.token)
                StatsCacheStore(this@LocalStoreStudy as LocalStore).markDirty(this)
            }
        }
    }

    fun undoLastAppliedReview(snapshot: StudyReviewActions.AppliedReviewSnapshot): Boolean {
        return writableDatabase.transaction {
            val deleted = delete(TABLE_REVIEW_LOG, "$COLUMN_TOKEN = ?", arrayOf(snapshot.token))
            if (deleted <= 0) {
                false
            } else {
                delete(TABLE_KANJI_TIMELINE_EVENTS, "$COLUMN_DEDUPE_KEY = ?", arrayOf("review:${snapshot.token}"))
                upsertStudyItem(this, snapshot.beforeReview)
                StatsCacheStore(this@LocalStoreStudy as LocalStore).markDirty(this)
                clearStudyItemsCache()
                true
            }
        }
    }

    fun cachedStatsSnapshotOrNull(): StatsCacheStore.Snapshot? {
        return StatsCacheStore(this as LocalStore).readFresh(nowMillis = System.currentTimeMillis())
    }

    fun latestStatsSnapshotOrNull(): StatsCacheStore.Snapshot? {
        return StatsCacheStore(this as LocalStore).readLatest()
    }

    fun recomputeStatsSnapshotSynchronously(nowMillis: Long): StatsCacheStore.Snapshot {
        return StatsPrecomputeStore(this as LocalStore).refresh(generatedAtMillis = nowMillis)
    }

    fun insertReview(
        db: SQLiteDatabase,
        request: RecordsSchedulerModels.ReviewRequest,
        appliedRating: String?,
        reviewedAt: Long,
        beforeReview: RecordsStudyModels.StudyItem?,
        afterReview: RecordsStudyModels.StudyItem?,
    ): Long {
        val values = ContentValues()
        values.put(COLUMN_KANJI, request.kanji)
        values.put(COLUMN_TOKEN, request.token)
        values.put(COLUMN_RATING, appliedRating)
        values.put(COLUMN_WRITING_REQUIRED, if (request.writingRequired) 1 else 0)
        values.put(COLUMN_WRITING_PASSED, if (request.writingPassed) 1 else 0)
        values.put(COLUMN_MANUAL_OVERRIDE, if (request.manualOverride) 1 else 0)
        values.put(COLUMN_REVIEWED_AT, reviewedAt)
        values.put(COLUMN_REVIEW_DAY_START, localDayStart(reviewedAt))
        values.put(COLUMN_TASK_TYPE, request.taskType)
        values.put(COLUMN_ANSWER_SIGNATURE, request.answerSignature)
        values.put("prompt", request.prompt)
        values.put("hints_used", request.hintsUsed)
        values.put("writing_clean", if (request.writingClean) 1 else 0)
        values.put("memory_before", taskMemoryText(beforeReview, request.taskType))
        values.put("memory_after", taskMemoryText(afterReview, request.taskType))
        values.put("scheduler_state_before_json", studyItemSchedulerJson(beforeReview))
        values.put("scheduler_state_after_json", studyItemSchedulerJson(afterReview))
        return db.insertWithOnConflict(TABLE_REVIEW_LOG, null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun taskMemoryText(item: RecordsStudyModels.StudyItem?, taskType: String?): String {
        if (item == null || taskType.isNullOrEmpty()) {
            return ""
        }
        return item.memoryForTaskType(taskType).encode()
    }

    fun studyItemSchedulerJson(item: RecordsStudyModels.StudyItem?): String {
        if (item == null) {
            return ""
        }
        return "{" +
            "\"state\":" + TextUtil.jsonQuote(item.state) +
            ",\"due_at\":" + item.dueAtMillis +
            ",\"stability\":" + item.stability +
            ",\"difficulty\":" + item.difficulty +
            ",\"total_reviews\":" + item.totalReviews +
            ",\"lapses\":" + item.lapses +
            ",\"learning_step\":" + item.learningStep +
            ",\"writing_level\":" + item.writingLevel +
            ",\"recognition_stage\":" + item.recognitionStage +
            ",\"consecutive_failed_recognition_days\":" + item.consecutiveFailedRecognitionDays +
            ",\"last_failed_recognition_day\":" + item.lastFailedRecognitionDayMillis +
            ",\"writing_remediation_pending\":" + (if (item.writingRemediationPending) "true" else "false") +
            ",\"suppressed_by_task_type\":" + TextUtil.jsonQuote(item.suppressedByTaskType) +
            ",\"suppressed_at\":" + item.suppressedAtMillis +
            ",\"mature_interval_days\":" + item.matureIntervalDays +
            ",\"answer_signature\":" + TextUtil.jsonQuote(item.answerSignature) +
            ",\"rung\":" + TextUtil.jsonQuote(item.rung.wireName()) +
            ",\"phase\":" + TextUtil.jsonQuote(item.phase.wireName()) +
            ",\"real_pass_streak\":" + item.realPassStreak +
            ",\"real_again_streak\":" + item.realAgainStreak +
            ",\"last_real_review_due_at\":" + item.lastRealReviewDueAtMillis +
            ",\"active_token\":" + TextUtil.jsonQuote(item.activeToken) +
            "}"
    }

    fun consumedTokens(): List<String> = studyStatus().consumedTokens()

    fun latestSync(): SyncStatus? = studyStatus().latestSync()

    fun hasSuccessfulSyncSince(finishedAtMillis: Long): Boolean = studyStatus().hasSuccessfulSyncSince(finishedAtMillis)

    fun getIntSetting(key: String, fallback: Int): Int = studySettings().getIntSetting(key, fallback)

    fun getLongSetting(key: String, fallback: Long): Long = studySettings().getLongSetting(key, fallback)

    fun getStringSetting(key: String, fallback: String?): String? {
        return settingsRepository().getString(key, fallback)
    }

    fun getDoubleSetting(key: String, fallback: Double): Double = studySettings().getDoubleSetting(key, fallback)

    fun reviewReminderNotificationsToday(nowMillis: Long): Int = studySettings().reviewReminderNotificationsToday(nowMillis)

    fun recordReviewReminderNotificationShown(nowMillis: Long) = studySettings().recordReviewReminderNotificationShown(nowMillis)

    fun clearReviewReminderNotifications(nowMillis: Long) = studySettings().clearReviewReminderNotifications(nowMillis)

    fun putIntSetting(key: String, value: Int) {
        studySettings().putIntSetting(key, value)
    }

    fun putLongSetting(key: String, value: Long) {
        studySettings().putLongSetting(key, value)
    }

    fun putStringSetting(key: String, value: String?) {
        studySettings().putStringSetting(key, value)
    }

    fun putDoubleSetting(key: String, value: Double) {
        studySettings().putDoubleSetting(key, value)
    }

    fun adaptiveLoadWorkPercent(): Int = studySettings().adaptiveLoadWorkPercent()

    fun saveAdaptiveLoadWorkPercent(percent: Int) {
        studySettings().saveAdaptiveLoadWorkPercent(percent)
    }

    fun studyAheadMinutes(): Int = studySettings().studyAheadMinutes()

    fun saveStudyAheadMinutes(minutes: Int) {
        studySettings().saveStudyAheadMinutes(minutes)
    }

    fun studyLadderSettings(): RecordsBase.StudyLadderSettings = studySettings().studyLadderSettings()

    fun saveStudyLadderSettings(settings: RecordsBase.StudyLadderSettings?) {
        studySettings().saveStudyLadderSettings(settings)
    }

    fun adaptiveLoadMaxItems(): Int = studySettings().adaptiveLoadMaxItems()

    fun saveAdaptiveLoadMaxItems(maxItems: Int) {
        studySettings().saveAdaptiveLoadMaxItems(maxItems)
    }

    fun adaptiveLoadMode(): String = studySettings().adaptiveLoadMode()

    fun saveAdaptiveLoadMode(mode: String?) {
        studySettings().saveAdaptiveLoadMode(mode)
    }

    fun saveNewCardSortMode(mode: String?): NewCardSortSettingsPolicy.SaveRequest {
        return studySettings().saveNewCardSortMode(mode)
    }

    fun appThemeChoice(): KaniThemeChoice = studySettings().appThemeChoice()

    fun saveAppThemeChoice(choice: KaniThemeChoice?): KaniThemeChoice = studySettings().saveAppThemeChoice(choice)

    fun reminderSettings(): ReminderSettings = studySettings().reminderSettings()

    fun saveReminderSettings(settings: ReminderSettings) {
        studySettings().saveReminderSettings(settings)
    }

    fun autoSyncSettings(): AutoSyncSettings = studySettings().autoSyncSettings()

    fun activateAutoSyncAfterFirstSuccess(): Boolean = studySettings().activateAutoSyncAfterFirstSuccess()

    fun setAutoSyncEnabled(enabled: Boolean) {
        studySettings().setAutoSyncEnabled(enabled)
    }

    fun markAutoSyncScheduled(nextRunAt: Long) {
        studySettings().markAutoSyncScheduled(nextRunAt)
    }

    fun recordAutoSyncAttempt(attemptedAt: Long, success: Boolean) {
        studySettings().recordAutoSyncAttempt(attemptedAt, success)
    }

    fun saveAutoSyncSettings(settings: AutoSyncSettings) {
        studySettings().saveAutoSyncSettings(settings)
    }

    fun autoUpdateStatus(): AutoUpdateStatus = studySettings().autoUpdateStatus()

    fun saveAutoUpdateEnabled(enabled: Boolean) {
        studySettings().saveAutoUpdateEnabled(enabled)
    }

    fun recordAutoUpdateResult(
        checkedAt: Long,
        result: String?,
        version: String?,
        pendingApkName: String?,
        pendingMessage: String?,
    ) {
        studySettings().recordAutoUpdateResult(checkedAt, result, version, pendingApkName, pendingMessage)
    }

    fun clearPendingAutoUpdate(result: String?) {
        studySettings().clearPendingAutoUpdate(result)
    }

    fun installPermissionPromptShown(): Boolean = studySettings().installPermissionPromptShown()

    fun installPermissionPromptLastVersion(): String = studySettings().installPermissionPromptLastVersion()

    fun recordInstallPermissionPrompted(version: String?) {
        studySettings().recordInstallPermissionPrompted(version)
    }

    fun schedulerParameters(): RecordsSchedulerModels.SchedulerParameters = studySettings().schedulerParameters()

    fun saveSchedulerParameters(parameters: RecordsSchedulerModels.SchedulerParameters) {
        studySettings().saveSchedulerParameters(parameters)
    }

    fun learningStepSettings(): RecordsSchedulerModels.LearningStepSettings = studySettings().learningStepSettings()

    fun saveLearningStepSettings(settings: RecordsSchedulerModels.LearningStepSettings?) {
        studySettings().saveLearningStepSettings(settings)
    }

    fun saveLearningRepeat(repeat: RecordsSchedulerModels.LearningRepeat) {
        studyLog().saveLearningRepeat(repeat)
    }

    fun enqueueLearningRepeat(
        item: RecordsStudyModels.StudyItem,
        taskType: String,
        repeatType: String,
        stepIndex: Int,
        dueAtMillis: Long,
        nowMillis: Long,
    ) {
        studyLog().enqueueLearningRepeat(item, taskType, repeatType, stepIndex, dueAtMillis, nowMillis)
    }

    fun clearLearningRepeat(repeat: RecordsSchedulerModels.LearningRepeat) {
        studyLog().clearLearningRepeat(repeat)
    }

    fun dueLearningRepeats(nowMillis: Long): List<RecordsSchedulerModels.LearningRepeat> {
        return studyLog().dueLearningRepeats(nowMillis)
    }

    fun reviewStatsSince(sinceMillis: Long): RecordsSchedulerModels.ReviewStats {
        return StudyStatsStore(this as LocalStore).reviewStatsSince(sinceMillis)
    }

    fun recordStudyTaskAnswered(
        taskKey: String?,
        kanji: String?,
        taskType: String?,
        startedAt: Long,
        answeredAt: Long,
        activeElapsedMillis: Long,
        outcome: String?,
    ): Boolean {
        val inserted = studyLog().recordStudyTaskAnswered(
            taskKey,
            kanji,
            taskType,
            startedAt,
            answeredAt,
            activeElapsedMillis,
            outcome
        )
        if (inserted) {
            StatsCacheStore(this@LocalStoreStudy as LocalStore).markDirty()
        }
        return inserted
    }

    fun studyTaskTimeStats(nowMillis: Long): StudyStatsStore.StudyTaskTimeStats {
        return StudyStatsStore(this as LocalStore).studyTaskTimeStats(nowMillis)
    }

    fun recentMistakes(limit: Int): List<StudyStatsStore.RecentMistake> {
        return StudyStatsStore(this as LocalStore).recentMistakes(limit)
    }

    fun studyStreak(nowMillis: Long): StudyStatsStore.StudyStreak {
        return StudyStatsStore(this as LocalStore).studyStreak(nowMillis)
    }

    fun studyImpactStats(): StudyStatsStore.StudyImpactStats {
        return StudyStatsStore(this as LocalStore).studyImpactStats()
    }

    fun kaniOutcomeStats(): StudyStatsStore.KaniOutcomeStats {
        return StudyStatsStore(this as LocalStore).kaniOutcomeStats()
    }

    fun kanjiRepairEvidence(): List<StudyStatsStore.KanjiRepairEvidence> {
        return StudyStatsStore(this as LocalStore).kanjiRepairEvidence()
    }

    fun retiredRepairsLast30Days(nowMillis: Long): Int {
        return StudyStatsStore(this as LocalStore).retiredRepairsLast30Days(nowMillis)
    }

    fun studiedKanjiSince(sinceMillis: Long): Set<String> {
        return StudyStatsStore(this as LocalStore).studiedKanjiSince(sinceMillis)
    }

    private companion object {
        fun localDayStart(millis: Long): Long = LocalDayPolicy.localDayStart(millis)
    }
}
