package dev.bee.kanjianki.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteConstraintException
import androidx.core.database.sqlite.transaction
import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.AdaptiveCorePolicy
import dev.bee.kanjianki.core.AdaptiveStudyItemPolicy
import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.core.DurableStudyItemRetentionPolicy
import dev.bee.kanjianki.core.MidSyncReviewMergePolicy
import dev.bee.kanjianki.core.NewCardSortSettingsPolicy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyItemLineagePolicy
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.TextUtil
import dev.bee.kanjianki.core.StudyTaskTimingPolicy
import dev.bee.kanjianki.StudyReviewActions
import dev.bee.kanjianki.StudyItemComparators
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
        replaceStudyItems(items, syncId, occurredAt, settings, null)
    }

    /**
     * Reconcile study items. Without [baseline], [items] is an authoritative full
     * replacement. With a possibly scoped [baseline], re-read durable state inside the
     * transaction, keep review evidence that advanced since that seed input was read,
     * and retain persisted kanji outside the candidate scope.
     */
    fun replaceStudyItems(
        items: List<RecordsStudyModels.StudyItem>,
        syncId: Long?,
        occurredAt: Long,
        settings: RecordsSyncModels.Settings?,
        baseline: List<RecordsStudyModels.StudyItem>?,
    ) {
        val start = android.os.SystemClock.elapsedRealtime()
        var writes = 0
        writableDatabase.transaction {
            val previous = if (syncId == null) emptyMap() else studySnapshots(this)
            val persisted = readAllStudyItems(this)
            val retained = reconcileStudyItems(items, baseline, persisted)
            val toWrite = versionMaterialStudyChanges(retained, persisted)
            writes = persistReconciledStudyItems(this, toWrite, syncId, baseline)
            if (syncId != null) {
                appendStudyStateTimelineEvents(this, previous, toWrite, syncId, occurredAt, settings)
            }
            StatsCacheStore(this@LocalStoreStudy as LocalStore).markDirty(this)
        }
        clearStudyItemsCache()
        dev.bee.kanjianki.studyLoadDebug(
            "replaceStudyItems WROTE count=${items.size} writes=$writes " +
                "duration_ms=${android.os.SystemClock.elapsedRealtime() - start}"
        )
    }

    private fun reconcileStudyItems(
        items: List<RecordsStudyModels.StudyItem>,
        baseline: List<RecordsStudyModels.StudyItem>?,
        persisted: List<RecordsStudyModels.StudyItem>,
    ): List<RecordsStudyModels.StudyItem> {
        if (baseline == null) return items
        val merged = MidSyncReviewMergePolicy.merge(items, baseline, persisted)
        // A baseline-aware scoped refresh is not deletion authority. Merge reviews
        // first, then retain persisted kanji absent from the candidate so
        // transaction-local additions and out-of-cap rows both survive.
        return DurableStudyItemRetentionPolicy.retainUnseeded(merged, persisted)
    }

    private fun persistReconciledStudyItems(
        db: SQLiteDatabase,
        toWrite: List<RecordsStudyModels.StudyItem>,
        syncId: Long?,
        baseline: List<RecordsStudyModels.StudyItem>?,
    ): Int {
        if (syncId == null && baseline == null) {
            // Per-review queue refresh: after every answered card the seeder usually
            // changes exactly one row, so write only the diff instead of deleting and
            // reinserting the whole table.
            return applyStudyItemsDiff(db, toWrite)
        }
        db.delete(TABLE_STUDY_ITEMS, null, null)
        for (item in toWrite) {
            upsertStudyItem(db, item)
        }
        return toWrite.size
    }

    private fun versionMaterialStudyChanges(
        candidates: List<RecordsStudyModels.StudyItem>,
        persisted: List<RecordsStudyModels.StudyItem>,
    ): List<RecordsStudyModels.StudyItem> {
        return candidates.map { candidate ->
            val existing = StudyItemLineagePolicy.counterpart(candidate, persisted)
                ?: return@map candidate
            when {
                StudyItemComparators.samePersistedState(existing, candidate) ->
                    candidate.copyBuilder().schedulerRevision(existing.schedulerRevision).build()
                else -> candidate.copyBuilder().schedulerRevision(existing.schedulerRevision + 1L).build()
            }
        }
    }

    /**
     * Makes the study_items table equal [toWrite] by upserting only rows that changed
     * and deleting rows no longer present, instead of delete-all + reinsert. Returns
     * the number of row writes/deletes performed.
     */
    private fun applyStudyItemsDiff(
        db: SQLiteDatabase,
        toWrite: List<RecordsStudyModels.StudyItem>,
    ): Int {
        val persistedByKey = HashMap<String, RecordsStudyModels.StudyItem>()
        for (item in readAllStudyItems(db)) {
            persistedByKey[studyFamilyKey(item.kanji, item.answerSignature)] = item
        }
        val keep = HashSet<String>()
        var writes = 0
        for (item in toWrite) {
            keep.add(studyFamilyKey(item.kanji, item.answerSignature))
            val existing = persistedByKey[studyFamilyKey(item.kanji, item.answerSignature)]
            if (existing == null || !StudyItemComparators.sameStudyItem(existing, item)) {
                upsertStudyItem(db, item)
                writes++
            }
        }
        for (item in persistedByKey.values) {
            if (!keep.contains(studyFamilyKey(item.kanji, item.answerSignature))) {
                db.delete(
                    TABLE_STUDY_ITEMS,
                    "$COLUMN_KANJI=? AND $COLUMN_ANSWER_SIGNATURE=?",
                    arrayOf(item.kanji, item.answerSignature),
                )
                writes++
            }
        }
        return writes
    }

    private fun readAllStudyItems(db: SQLiteDatabase): List<RecordsStudyModels.StudyItem> {
        val items = ArrayList<RecordsStudyModels.StudyItem>()
        db.query(TABLE_STUDY_ITEMS, null, null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                items.add(readStudyItem(cursor))
            }
        }
        return items
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
        var wrote = false
        writableDatabase.transaction {
            val current = readStudyItemForFamily(this, item.kanji, item.answerSignature)
            if (current == null || item.schedulerRevision >= current.schedulerRevision) {
                upsertStudyItem(this, item)
                StatsCacheStore(this@LocalStoreStudy as LocalStore).markDirty(this)
                wrote = true
            }
        }
        if (wrote) {
            clearStudyItemsCache()
        }
    }

    private fun readStudyItemForFamily(
        db: SQLiteDatabase,
        kanji: String,
        answerSignature: String,
    ): RecordsStudyModels.StudyItem? {
        db.query(
            TABLE_STUDY_ITEMS,
            null,
            "$COLUMN_KANJI=? AND $COLUMN_ANSWER_SIGNATURE=?",
            arrayOf(kanji, answerSignature),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            return if (cursor.moveToFirst()) readStudyItem(cursor) else null
        }
    }

    /**
     * Persists the advanced study item and its review-log row in a single
     * transaction so process death can never leave scheduling advanced with no
     * `review_log` row (which would lose the review from streaks/stats/undo and
     * make the token wrongly appear retryable while the item already advanced).
     * Both-or-nothing: if either write fails the whole outcome rolls back.
     */
    fun saveReviewOutcome(
        item: RecordsStudyModels.StudyItem,
        request: RecordsSchedulerModels.ReviewRequest,
        appliedRating: String?,
        reviewedAt: Long,
        beforeReview: RecordsStudyModels.StudyItem,
    ): ReviewCommitResult {
        return commitReview(
            ReviewCommitCommand(
                afterReview = item,
                request = request,
                appliedRating = appliedRating,
                reviewedAtMillis = reviewedAt,
                beforeReview = beforeReview,
            )
        )
    }

    /**
     * Token-first, revision-CAS review persistence. A token conflict is a true
     * idempotent duplicate only when that exact token exists. Every other
     * ignored insert is surfaced as a constraint failure instead of silently
     * advancing scheduler state without a review row.
     */
    fun commitReview(command: ReviewCommitCommand): ReviewCommitResult {
        val persistedItem = command.persistedItem()
        val result = try {
            writableDatabase.transaction {
                val inserted = insertReview(
                    this,
                    command.request,
                    command.appliedRating,
                    command.reviewedAtMillis,
                    command.beforeReview,
                    persistedItem,
                )
                if (inserted == -1L) {
                    if (reviewTokenExists(this, command.request.token)) {
                        return@transaction ReviewCommitResult.duplicate()
                    }
                    throw SQLiteConstraintException("review_log rejected a non-duplicate review")
                }

                val updated = update(
                    TABLE_STUDY_ITEMS,
                    studyItemValues(persistedItem),
                    "$COLUMN_KANJI=? AND $COLUMN_ANSWER_SIGNATURE=? AND $COLUMN_SCHEDULER_REVISION=?",
                    arrayOf(
                        command.beforeReview.kanji,
                        command.beforeReview.answerSignature,
                        command.expectedRevision.toString(),
                    ),
                )
                if (updated != 1) {
                    throw StaleReviewCommitException()
                }

                applyReviewSideEffects(this, command)
                appendReviewTimelineEvent(
                    this,
                    command.request,
                    command.appliedRating,
                    command.reviewedAtMillis,
                    "review:" + command.request.token,
                )
                command.taskTiming?.let { insertReviewTaskTiming(this, it) }
                command.choiceLog?.let { insertReviewChoiceLog(this, it) }
                StatsCacheStore(this@LocalStoreStudy as LocalStore).markDirty(this)
                ReviewCommitResult.applied(persistedItem)
            }
        } catch (_: StaleReviewCommitException) {
            ReviewCommitResult.stale()
        }
        if (result.applied()) {
            // Cache invalidation is an after-commit effect. Readers can never
            // repopulate from an uncommitted state and keep it after rollback.
            clearStudyItemsCache()
        }
        return result
    }

    /** Subclasses can fold legacy evidence/state updates into the review transaction. */
    @Suppress("UNUSED_PARAMETER")
    internal open fun applyReviewSideEffects(db: SQLiteDatabase, command: ReviewCommitCommand) = Unit

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
        val restored = snapshot.beforeReview.copyBuilder()
            .schedulerRevision(snapshot.afterReview.schedulerRevision + 1L)
            .build()
        val undone = try {
            writableDatabase.transaction {
                val deleted = delete(TABLE_REVIEW_LOG, "$COLUMN_TOKEN = ?", arrayOf(snapshot.token))
                if (deleted <= 0) {
                    return@transaction false
                }
                val updated = update(
                    TABLE_STUDY_ITEMS,
                    studyItemValues(restored),
                    "$COLUMN_KANJI=? AND $COLUMN_ANSWER_SIGNATURE=? AND $COLUMN_SCHEDULER_REVISION=?",
                    arrayOf(
                        snapshot.afterReview.kanji,
                        snapshot.afterReview.answerSignature,
                        snapshot.afterReview.schedulerRevision.toString(),
                    ),
                )
                if (updated != 1) {
                    throw StaleReviewCommitException()
                }
                delete(TABLE_KANJI_TIMELINE_EVENTS, "$COLUMN_DEDUPE_KEY = ?", arrayOf("review:${snapshot.token}"))
                StatsCacheStore(this@LocalStoreStudy as LocalStore).markDirty(this)
                true
            }
        } catch (_: StaleReviewCommitException) {
            false
        }
        if (undone) {
            clearStudyItemsCache()
        }
        return undone
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
        values.put(COLUMN_CORE_SKILL, request.coreSkill)
        values.put(COLUMN_FAILURE_CAUSE, request.failureCause)
        values.put(COLUMN_EVIDENCE_SOURCE, request.evidenceSource)
        values.put(COLUMN_SELECTED_ANSWER, request.selectedAnswer)
        values.put(COLUMN_CORRECT_ANSWER, request.correctAnswer)
        values.put(COLUMN_ANSWER_EVIDENCE_JSON, request.answerEvidenceJson)
        return db.insertWithOnConflict(TABLE_REVIEW_LOG, null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun reviewTokenExists(db: SQLiteDatabase, token: String): Boolean {
        db.query(
            TABLE_REVIEW_LOG,
            arrayOf(COLUMN_TOKEN),
            "$COLUMN_TOKEN=?",
            arrayOf(token),
            null,
            null,
            null,
            "1",
        ).use { return it.moveToFirst() }
    }

    private fun insertReviewTaskTiming(db: SQLiteDatabase, timing: ReviewTaskTiming) {
        val values = ContentValues()
        values.put("task_key", timing.taskKey)
        values.put(COLUMN_KANJI, timing.kanji)
        values.put(COLUMN_TASK_TYPE, timing.taskType)
        values.put(COLUMN_STARTED_AT, timing.startedAtMillis.coerceAtLeast(0L))
        values.put("answered_at", timing.answeredAtMillis.coerceAtLeast(0L))
        values.put(
            "active_elapsed_ms",
            StudyTaskTimingPolicy.boundedElapsed(timing.activeElapsedMillis, MAX_STUDY_TASK_ELAPSED_MS),
        )
        values.put("outcome", timing.outcome)
        val inserted = db.insertWithOnConflict(
            TABLE_STUDY_TASK_LOG,
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE,
        )
        if (inserted == -1L && !studyTaskKeyExists(db, timing.taskKey)) {
            throw SQLiteConstraintException("study_task_log rejected a non-duplicate task")
        }
    }

    private fun studyTaskKeyExists(db: SQLiteDatabase, taskKey: String): Boolean {
        db.query(
            TABLE_STUDY_TASK_LOG,
            arrayOf("task_key"),
            "task_key=?",
            arrayOf(taskKey),
            null,
            null,
            null,
            "1",
        ).use { return it.moveToFirst() }
    }

    private fun insertReviewChoiceLog(db: SQLiteDatabase, choice: ReviewChoiceLog) {
        val values = ContentValues()
        values.put(COLUMN_TARGET_KANJI, choice.targetKanji)
        values.put(COLUMN_CHOICE_SIGNATURE, choice.choiceSignature)
        values.put("selected_kanji", choice.selectedAnswer)
        values.put("correct", if (choice.correct) 1 else 0)
        values.put(COLUMN_REVIEWED_AT, choice.reviewedAtMillis)
        values.put(COLUMN_RUNG, choice.rung)
        db.insertOrThrow(TABLE_SIMILAR_KANJI_REVIEW_LOG, null, values)
    }

    fun taskMemoryText(item: RecordsStudyModels.StudyItem?, taskType: String?): String {
        if (item == null || taskType.isNullOrEmpty()) {
            return ""
        }
        val ownerTask = if (AdaptiveStudyItemPolicy.isAdaptive(item)) {
            AdaptiveCorePolicy.coreForTaskType(taskType)?.let(AdaptiveCorePolicy::memoryOwnerTaskType) ?: taskType
        } else {
            taskType
        }
        return item.memoryForTaskType(ownerTask).encode()
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
            ",\"scheduler_revision\":" + item.schedulerRevision +
            ",\"routing_version\":" + item.routingVersion +
            ",\"adaptive_route_state_json\":" + TextUtil.jsonQuote(item.adaptiveRouteStateJson) +
            ",\"active_token\":" + TextUtil.jsonQuote(item.activeToken) +
            "}"
    }

    fun consumedTokens(): List<String> = studyStatus().consumedTokens()

    fun hasConsumedToken(token: String): Boolean = studyStatus().hasConsumedToken(token)

    fun hasMatchingConsumedReview(
        token: String,
        kanji: String,
        taskType: String,
        answerSignature: String,
    ): Boolean = studyStatus().hasMatchingConsumedReview(token, kanji, taskType, answerSignature)

    fun latestSync(): SyncStatus? = studyStatus().latestSync()

    fun latestSuccessfulSyncFinishedAt(): Long? = studyStatus().latestSuccessfulSyncFinishedAt()

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

    fun reminderThrottleState(nowMillis: Long): LocalStoreBase.ReminderThrottleState =
        studySettings().reminderThrottleState(nowMillis)

    fun reminderAntiSpamSettings(): LocalStoreBase.ReminderAntiSpamSettings =
        studySettings().reminderAntiSpamSettings()

    fun saveReminderAntiSpamSettings(settings: LocalStoreBase.ReminderAntiSpamSettings) =
        studySettings().saveReminderAntiSpamSettings(settings)

    fun recordReminderPosted(nowMillis: Long, family: String?, signature: String?, dailyTimeOverride: Boolean) =
        studySettings().recordReminderPosted(nowMillis, family, signature, dailyTimeOverride)

    fun recordReminderDismissed(nowMillis: Long, family: String?) =
        studySettings().recordReminderDismissed(nowMillis, family)

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

    // The theme choice is read from SQLite on every route composition (main thread).
    // Cache it in memory to avoid a per-navigation DB hit; invalidate on save.
    @Volatile
    private var cachedThemeChoice: KaniThemeChoice? = null

    fun appThemeChoice(): KaniThemeChoice {
        cachedThemeChoice?.let { return it }
        val choice = studySettings().appThemeChoice()
        cachedThemeChoice = choice
        return choice
    }

    /**
     * Non-blocking theme read for main-thread route composition. Never touches the
     * database: returns the cached choice when a background read or save has already
     * populated it, and the default theme otherwise. This keeps the very first frame
     * (and any frame racing a cold-boot migration) from blocking on SQLite - the
     * synchronous fallback used to ANR cold boots whenever the schema upgrade was
     * still running on the background executor.
     */
    fun appThemeChoiceNonBlocking(): KaniThemeChoice {
        return cachedThemeChoice ?: KaniThemeChoice.fromStorageKey(null)
    }

    fun saveAppThemeChoice(choice: KaniThemeChoice?): KaniThemeChoice {
        val saved = studySettings().saveAppThemeChoice(choice)
        cachedThemeChoice = saved
        return saved
    }

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

    fun debugLogEnabled(): Boolean = studySettings().debugLogEnabled()

    fun saveDebugLogEnabled(enabled: Boolean) {
        studySettings().saveDebugLogEnabled(enabled)
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

    fun schedulerFsrsWeights(): DoubleArray? = studySettings().schedulerFsrsWeights()

    fun saveSchedulerFsrsWeights(weights: DoubleArray?) {
        studySettings().saveSchedulerFsrsWeights(weights)
    }

    fun commitFsrsFitOutcome(
        weightsToAdopt: DoubleArray?,
        summaryJson: String,
        disabledSummaryJson: String?,
        preserveExistingWeights: Boolean,
    ): Boolean = studySettings().commitFsrsFitOutcome(
        weightsToAdopt,
        summaryJson,
        disabledSummaryJson,
        preserveExistingWeights,
    )

    fun fsrsPersonalizationEnabled(): Boolean = studySettings().fsrsPersonalizationEnabled()

    fun saveFsrsPersonalizationEnabled(enabled: Boolean) {
        studySettings().saveFsrsPersonalizationEnabled(enabled)
    }

    fun fsrsFitSummaryJson(): String = studySettings().fsrsFitSummaryJson()

    fun saveFsrsFitSummaryJson(summaryJson: String?) {
        studySettings().saveFsrsFitSummaryJson(summaryJson)
    }

    fun resetFsrsPersonalization() {
        studySettings().resetFsrsPersonalization()
    }

    fun learningStepSettings(): RecordsSchedulerModels.LearningStepSettings = studySettings().learningStepSettings()

    fun saveLearningStepSettings(settings: RecordsSchedulerModels.LearningStepSettings?) {
        studySettings().saveLearningStepSettings(settings)
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
