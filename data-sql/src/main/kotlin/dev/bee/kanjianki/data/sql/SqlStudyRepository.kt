package dev.bee.kanjianki.data.sql

import dev.bee.kanjianki.core.AdaptiveCorePolicy
import dev.bee.kanjianki.core.AdaptiveStudyItemPolicy
import dev.bee.kanjianki.core.AppliedReviewSnapshot
import dev.bee.kanjianki.core.DurableStudyItemRetentionPolicy
import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.core.MidSyncReviewMergePolicy
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.SimilarKanjiChoicePlanner
import dev.bee.kanjianki.core.SimilarKanjiChoiceReviewPolicy
import dev.bee.kanjianki.core.SimilarKanjiRepairPolicy
import dev.bee.kanjianki.core.StudyItemComparators
import dev.bee.kanjianki.core.StudyItemLineagePolicy
import dev.bee.kanjianki.core.StudyTaskTimingPolicy
import dev.bee.kanjianki.core.TextUtil
import dev.bee.kanjianki.core.TimelineCopy
import dev.bee.kanjianki.data.FinishLegacyRepairCommand
import dev.bee.kanjianki.data.ReviewCommitCommand
import dev.bee.kanjianki.data.ReviewCommitResult
import dev.bee.kanjianki.data.ReviewChoiceLog
import dev.bee.kanjianki.data.ReviewTaskTiming
import dev.bee.kanjianki.data.ReviewTokenQuery
import dev.bee.kanjianki.data.SaveMnemonicCommand
import dev.bee.kanjianki.data.SimilarChoiceCommit
import dev.bee.kanjianki.data.SkipLegacyRepairCommand
import dev.bee.kanjianki.data.StudyRecoveryQuery
import dev.bee.kanjianki.data.StudyRecoveryStatus
import dev.bee.kanjianki.data.StudyQueueWriteCommand
import dev.bee.kanjianki.data.StudyRepository

/**
 * Driver-neutral Study persistence. Owns the token-first, revision-CAS review
 * commit plus queue reconciliation, item writes, undo recovery, task timing,
 * and the legacy similar-choice/writing-repair compatibility state. Android
 * production stays on LocalStore until the Goal 184 composition switch.
 */
class SqlStudyRepository(
    private val database: SqlDatabase,
    private val invalidation: SqlProjectionInvalidation = SqlProjectionInvalidation(),
) : StudyRepository {
    override suspend fun loadQueue(nowMillis: Long) = safeSqlStoreCall {
        database.readSnapshot { SqlStudyData(this).loadQueue(nowMillis) }
    }

    override suspend fun loadAllItems() = safeSqlStoreCall {
        database.readSnapshot {
            queryList("SELECT * FROM study_items", map = SqlStudyItemMapper::read)
        }
    }

    override suspend fun loadItems(kanji: Collection<String>) = safeSqlStoreCall {
        database.readSnapshot { SqlStudyData(this).studyItemsForKanji(kanji) }
    }

    override suspend fun replaceQueue(command: StudyQueueWriteCommand) = safeSqlStoreCall {
        database.write { replaceStudyItems(command.items, command.baseline) }
        invalidation.invalidateHome()
        Unit
    }

    override suspend fun annotateCapabilities(items: List<RecordsStudyModels.StudyItem>) =
        safeSqlStoreCall {
            if (items.isEmpty()) {
                items
            } else {
                database.readSnapshot { SqlHomeData(this).annotateConditionalRungs(items) }
            }
        }

    override suspend fun saveItem(item: RecordsStudyModels.StudyItem) = safeSqlStoreCall {
        val wrote = database.write {
            val current = readStudyItemForFamily(item.kanji, item.answerSignature)
            if (current == null || item.schedulerRevision >= current.schedulerRevision) {
                upsertStudyItem(item)
                markStatsDirty()
                true
            } else {
                false
            }
        }
        if (wrote) invalidation.invalidateHome()
        Unit
    }

    override suspend fun recordTaskTiming(timing: ReviewTaskTiming) = safeSqlStoreCall {
        database.write { insertReviewTaskTiming(timing, requireInsert = false) }
    }

    override suspend fun commitReview(command: ReviewCommitCommand) = safeSqlStoreCall {
        val result = commitReviewInternal(command)
        if (result.applied()) invalidation.invalidateHome()
        result
    }

    override suspend fun undoLastReview(snapshot: AppliedReviewSnapshot) = safeSqlStoreCall {
        val undone = undoInternal(snapshot)
        if (undone) invalidation.invalidateHome()
        undone
    }

    override suspend fun loadQueueVersion() = safeSqlStoreCall {
        database.readSnapshot { SqlHomeData(this).latestSuccessfulSyncAtMillis() }
    }

    override suspend fun reviewTokenStatus(query: ReviewTokenQuery) = safeSqlStoreCall {
        database.readSnapshot { SqlStudyData(this).reviewTokenStatus(query.toLookup()) }
    }

    override suspend fun recoveryStatus(query: StudyRecoveryQuery) = safeSqlStoreCall {
        database.readSnapshot {
            val data = SqlStudyData(this)
            val token = data.reviewTokenStatus(query.review.toLookup())
            val repairFinished = query.repairId?.let { repairId ->
                data.hasFinishedSimilarWritingRepairAttempt(
                    repairId,
                    query.review.token,
                    query.repairAttemptsBefore,
                    query.repairPassed,
                )
            } ?: false
            StudyRecoveryStatus(token, repairFinished)
        }
    }

    override suspend fun loadChoiceData(kanji: String, nowMillis: Long) = safeSqlStoreCall {
        database.readSnapshot { SqlStudyData(this).loadChoiceData(kanji, nowMillis) }
    }

    override suspend fun loadDueSimilarChoice(targetKanji: String, nowMillis: Long) = safeSqlStoreCall {
        database.readSnapshot { SqlStudyData(this).dueSimilarChoiceForActiveTarget(targetKanji, nowMillis) }
    }

    override suspend fun loadDueLegacyWritingRepairs(nowMillis: Long) = safeSqlStoreCall {
        database.readSnapshot { SqlStudyData(this).dueWritingRepairs(nowMillis) }
    }

    override suspend fun saveLegacyWritingRepair(
        repair: RecordsImportModels.SimilarKanjiWritingRepair,
    ) = safeSqlStoreCall {
        if (repair.id > 0L) {
            database.write {
                executeBound(
                    """
                    UPDATE similar_kanji_repair_queue
                    SET active_token = ?, updated_at = ?
                    WHERE id = ? AND status = ?
                    """.trimIndent(),
                ) {
                    bindText(1, repair.activeToken)
                    bindLong(2, repair.updatedAtMillis)
                    bindLong(3, repair.id)
                    bindText(4, STATUS_PENDING)
                }
            }
        }
        Unit
    }

    override suspend fun finishLegacyWritingRepair(command: FinishLegacyRepairCommand) = safeSqlStoreCall {
        database.write {
            resolveLegacyRepair(command.repairId, command.token) { current ->
                SimilarKanjiRepairPolicy.finishUpdate(current, command.passed, command.finishedAtMillis)
            }
        }
    }

    override suspend fun skipLegacyWritingRepair(command: SkipLegacyRepairCommand) = safeSqlStoreCall {
        database.write {
            resolveLegacyRepair(command.repairId, command.token) { current ->
                SimilarKanjiRepairPolicy.skipUpdate(current, command.skippedAtMillis)
            }
        }
    }

    override suspend fun loadMnemonic(kanji: String) = safeSqlStoreCall {
        database.readSnapshot { SqlStudyData(this).mnemonic(kanji) }
    }

    override suspend fun saveMnemonic(command: SaveMnemonicCommand) = safeSqlStoreCall {
        val key = TextUtil.normalizeSingleKanji(command.kanji)
        if (key.isNotEmpty()) {
            val note = command.note.trim()
            database.write {
                if (note.isEmpty()) {
                    executeBound(
                        "DELETE FROM kanji_mnemonic_notes WHERE kanji = ?",
                        bind = { bindText(1, key) },
                    )
                } else {
                    executeBound(
                        """
                        INSERT INTO kanji_mnemonic_notes(kanji, note, updated_at)
                        VALUES (?, ?, ?)
                        ON CONFLICT(kanji) DO UPDATE SET note = excluded.note, updated_at = excluded.updated_at
                        """.trimIndent(),
                    ) {
                        bindText(1, key)
                        bindText(2, note)
                        bindLong(3, command.updatedAtMillis)
                    }
                }
            }
        }
        Unit
    }

    // --- Transactional write internals -------------------------------------

    private suspend fun commitReviewInternal(command: ReviewCommitCommand): ReviewCommitResult {
        val persistedItem = command.persistedItem()
        return try {
            database.write {
                val inserted = insertReview(
                    command.request,
                    command.appliedRating,
                    command.reviewedAtMillis,
                    command.beforeReview,
                    persistedItem,
                )
                if (!inserted) {
                    if (reviewTokenExists(command.request.token)) {
                        return@write ReviewCommitResult.duplicate()
                    }
                    throw SqlConstraintException(
                        SqlConstraintKind.UNIQUE,
                        "review_log rejected a non-duplicate review",
                    )
                }
                val updated = updateStudyItemWithRevision(
                    persistedItem,
                    command.beforeReview.kanji,
                    command.beforeReview.answerSignature,
                    command.expectedRevision,
                )
                if (!updated) throw StaleSqlReviewCommit()

                applySimilarChoiceSideEffect(command.similarChoice)
                appendReviewTimelineEvent(
                    command.request,
                    command.appliedRating,
                    command.reviewedAtMillis,
                    "review:" + command.request.token,
                )
                command.taskTiming?.let { insertReviewTaskTiming(it, requireInsert = true) }
                command.choiceLog?.let { insertReviewChoiceLog(it) }
                markStatsDirty()
                ReviewCommitResult.applied(persistedItem)
            }
        } catch (_: StaleSqlReviewCommit) {
            ReviewCommitResult.stale()
        }
    }

    private suspend fun undoInternal(snapshot: AppliedReviewSnapshot): Boolean {
        val restored = snapshot.beforeReview.copyBuilder()
            .schedulerRevision(Math.addExact(snapshot.afterReview.schedulerRevision, 1L))
            .build()
        return try {
            database.write {
                executeBound(
                    "DELETE FROM review_log WHERE token = ?",
                    bind = { bindText(1, snapshot.token) },
                )
                if (changes() <= 0L) return@write false
                val updated = updateStudyItemWithRevision(
                    restored,
                    snapshot.afterReview.kanji,
                    snapshot.afterReview.answerSignature,
                    snapshot.afterReview.schedulerRevision,
                )
                if (!updated) throw StaleSqlReviewCommit()
                executeBound(
                    "DELETE FROM kanji_timeline_events WHERE dedupe_key = ?",
                    bind = { bindText(1, "review:${snapshot.token}") },
                )
                markStatsDirty()
                true
            }
        } catch (_: StaleSqlReviewCommit) {
            false
        }
    }

    private fun SqlTransactionScope.replaceStudyItems(
        items: List<RecordsStudyModels.StudyItem>,
        baseline: List<RecordsStudyModels.StudyItem>?,
    ) {
        val persisted = queryList("SELECT * FROM study_items", map = SqlStudyItemMapper::read)
        val retained = if (baseline == null) {
            items
        } else {
            DurableStudyItemRetentionPolicy.retainUnseeded(
                MidSyncReviewMergePolicy.merge(items, baseline, persisted),
                persisted,
            )
        }
        val toWrite = versionMaterialStudyChanges(retained, persisted)
        applyStudyItemsDiff(toWrite)
        markStatsDirty()
    }

    private fun versionMaterialStudyChanges(
        candidates: List<RecordsStudyModels.StudyItem>,
        persisted: List<RecordsStudyModels.StudyItem>,
    ): List<RecordsStudyModels.StudyItem> =
        candidates.map { candidate ->
            val existing = StudyItemLineagePolicy.counterpart(candidate, persisted)
                ?: return@map candidate
            if (StudyItemComparators.samePersistedState(existing, candidate)) {
                candidate.copyBuilder().schedulerRevision(existing.schedulerRevision).build()
            } else {
                candidate.copyBuilder()
                    .schedulerRevision(Math.addExact(existing.schedulerRevision, 1L))
                    .build()
            }
        }

    private fun SqlTransactionScope.applyStudyItemsDiff(toWrite: List<RecordsStudyModels.StudyItem>) {
        val persistedByKey = HashMap<String, RecordsStudyModels.StudyItem>()
        queryList("SELECT * FROM study_items", map = SqlStudyItemMapper::read).forEach { item ->
            persistedByKey[familyKey(item.kanji, item.answerSignature)] = item
        }
        val keep = HashSet<String>()
        for (item in toWrite) {
            keep.add(familyKey(item.kanji, item.answerSignature))
            val existing = persistedByKey[familyKey(item.kanji, item.answerSignature)]
            if (existing == null || !StudyItemComparators.sameStudyItem(existing, item)) {
                upsertStudyItem(item)
            }
        }
        for (item in persistedByKey.values) {
            if (!keep.contains(familyKey(item.kanji, item.answerSignature))) {
                executeBound(
                    "DELETE FROM study_items WHERE kanji = ? AND answer_signature = ?",
                    bind = {
                        bindText(1, item.kanji)
                        bindText(2, item.answerSignature)
                    },
                )
            }
        }
    }

    private fun SqlSession.upsertStudyItem(item: RecordsStudyModels.StudyItem) {
        val columns = SqlStudyItemMapper.COLUMNS
        val placeholders = columns.joinToString(",") { "?" }
        val assignments = columns
            .filterNot { it == "kanji" || it == "answer_signature" }
            .joinToString(",") { "$it = excluded.$it" }
        prepare(
            """
            INSERT INTO study_items(${columns.joinToString(",")})
            VALUES ($placeholders)
            ON CONFLICT(kanji, answer_signature) DO UPDATE SET $assignments
            """.trimIndent(),
        ).use { statement ->
            SqlStudyItemMapper.bindUpsert(statement, item)
            statement.execute()
        }
    }

    private fun SqlSession.updateStudyItemWithRevision(
        item: RecordsStudyModels.StudyItem,
        kanji: String,
        answerSignature: String,
        expectedRevision: Long,
    ): Boolean {
        val setColumns = SqlStudyItemMapper.COLUMNS
        // The SET clause binds the full column block in bindUpsert's order; the
        // CAS predicate binds after it (matching the legacy revision compare).
        val setClause = setColumns.joinToString(",") { "$it = ?" }
        prepare(
            """
            UPDATE study_items SET $setClause
            WHERE kanji = ? AND answer_signature = ? AND scheduler_revision = ?
            """.trimIndent(),
        ).use { statement ->
            SqlStudyItemMapper.bindUpsert(statement, item)
            statement.bindText(setColumns.size + 1, kanji)
            statement.bindText(setColumns.size + 2, answerSignature)
            statement.bindLong(setColumns.size + 3, expectedRevision)
            statement.execute()
        }
        return changes() == 1L
    }

    private fun SqlSession.readStudyItemForFamily(
        kanji: String,
        answerSignature: String,
    ): RecordsStudyModels.StudyItem? =
        queryOneOrNull(
            "SELECT * FROM study_items WHERE kanji = ? AND answer_signature = ? LIMIT 1",
            bind = {
                bindText(1, kanji)
                bindText(2, answerSignature)
            },
            map = SqlStudyItemMapper::read,
        )

    private fun SqlSession.insertReview(
        request: RecordsSchedulerModels.ReviewRequest,
        appliedRating: String?,
        reviewedAt: Long,
        beforeReview: RecordsStudyModels.StudyItem?,
        afterReview: RecordsStudyModels.StudyItem?,
    ): Boolean {
        prepare(
            """
            INSERT OR IGNORE INTO review_log(
                kanji, token, rating, writing_required, writing_passed, manual_override,
                reviewed_at, review_day_start, task_type, answer_signature, prompt, hints_used,
                writing_clean, memory_before, memory_after, scheduler_state_before_json,
                scheduler_state_after_json, core_skill, failure_cause, evidence_source,
                selected_answer, correct_answer, answer_evidence_json
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.bindText(1, request.kanji)
            statement.bindText(2, request.token)
            bindNullableText(statement, 3, appliedRating)
            statement.bindLong(4, if (request.writingRequired) 1L else 0L)
            statement.bindLong(5, if (request.writingPassed) 1L else 0L)
            statement.bindLong(6, if (request.manualOverride) 1L else 0L)
            statement.bindLong(7, reviewedAt)
            statement.bindLong(8, LocalDayPolicy.localDayStart(reviewedAt))
            statement.bindText(9, request.taskType)
            statement.bindText(10, request.answerSignature)
            statement.bindText(11, request.prompt)
            statement.bindLong(12, request.hintsUsed.toLong())
            statement.bindLong(13, if (request.writingClean) 1L else 0L)
            statement.bindText(14, taskMemoryText(beforeReview, request.taskType))
            statement.bindText(15, taskMemoryText(afterReview, request.taskType))
            statement.bindText(16, studyItemSchedulerJson(beforeReview))
            statement.bindText(17, studyItemSchedulerJson(afterReview))
            statement.bindText(18, request.coreSkill)
            statement.bindText(19, request.failureCause)
            statement.bindText(20, request.evidenceSource)
            statement.bindText(21, request.selectedAnswer)
            statement.bindText(22, request.correctAnswer)
            statement.bindText(23, request.answerEvidenceJson)
            statement.execute()
        }
        return changes() == 1L
    }

    private fun SqlSession.reviewTokenExists(token: String): Boolean =
        queryOneOrNull(
            "SELECT 1 FROM review_log WHERE token = ? LIMIT 1",
            bind = { bindText(1, token) },
        ) { true } == true

    private fun SqlSession.insertReviewTaskTiming(timing: ReviewTaskTiming, requireInsert: Boolean): Boolean {
        prepare(
            """
            INSERT OR IGNORE INTO study_task_log(
                task_key, kanji, task_type, started_at, answered_at, active_elapsed_ms, outcome
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.bindText(1, timing.taskKey)
            statement.bindText(2, timing.kanji)
            statement.bindText(3, timing.taskType)
            statement.bindLong(4, timing.startedAtMillis.coerceAtLeast(0L))
            statement.bindLong(5, timing.answeredAtMillis.coerceAtLeast(0L))
            statement.bindLong(
                6,
                StudyTaskTimingPolicy.boundedElapsed(timing.activeElapsedMillis, MAX_STUDY_TASK_ELAPSED_MS),
            )
            statement.bindText(7, timing.outcome)
            statement.execute()
        }
        val inserted = changes() == 1L
        if (!inserted && requireInsert && !studyTaskKeyExists(timing.taskKey)) {
            throw SqlConstraintException(
                SqlConstraintKind.UNIQUE,
                "study_task_log rejected a non-duplicate task",
            )
        }
        return inserted
    }

    private fun SqlSession.studyTaskKeyExists(taskKey: String): Boolean =
        queryOneOrNull(
            "SELECT 1 FROM study_task_log WHERE task_key = ? LIMIT 1",
            bind = { bindText(1, taskKey) },
        ) { true } == true

    private fun SqlSession.insertReviewChoiceLog(choice: ReviewChoiceLog) {
        executeBound(
            """
            INSERT INTO similar_kanji_review_log(
                target_kanji, choice_signature, selected_kanji, correct, reviewed_at, rung
            ) VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ) {
            bindText(1, choice.targetKanji)
            bindText(2, choice.choiceSignature)
            bindText(3, choice.selectedAnswer)
            bindLong(4, if (choice.correct) 1L else 0L)
            bindLong(5, choice.reviewedAtMillis)
            bindText(6, choice.rung)
        }
    }

    private fun SqlTransactionScope.applySimilarChoiceSideEffect(choice: SimilarChoiceCommit?) {
        if (choice == null) return
        val stored = similarChoiceCard(choice.submitted.targetKanji, choice.submitted.choiceSignature)
            ?: throw StaleSqlReviewCommit()
        val evaluated = SimilarKanjiChoicePlanner()
            .evaluateSelection(stored, TextUtil.normalizeSingleKanji(choice.selectedAnswer))
        val update = SimilarKanjiChoiceReviewPolicy.reviewUpdate(stored, evaluated, choice.reviewedAtMillis)

        val assignments = ArrayList<String>()
        assignments += "last_reviewed_at = ?"
        assignments += "passed_at = ?"
        update.dueAtMillis()?.let { assignments += "due_at = ?" }
        update.correctCount()?.let { assignments += "correct_count = ?" }
        update.wrongCount()?.let { assignments += "wrong_count = ?" }
        prepare(
            """
            UPDATE similar_kanji_choice_state SET ${assignments.joinToString(",")}
            WHERE target_kanji = ? AND choice_signature = ?
            """.trimIndent(),
        ).use { statement ->
            var index = 1
            statement.bindLong(index++, update.lastReviewedAtMillis())
            statement.bindLong(index++, update.passedAtMillis())
            update.dueAtMillis()?.let { statement.bindLong(index++, it) }
            update.correctCount()?.let { statement.bindLong(index++, it.toLong()) }
            update.wrongCount()?.let { statement.bindLong(index++, it.toLong()) }
            statement.bindText(index++, stored.targetKanji)
            statement.bindText(index, stored.choiceSignature)
            statement.execute()
        }
        if (changes() != 1L) throw StaleSqlReviewCommit()

        executeBound(
            """
            INSERT INTO similar_kanji_review_log(
                target_kanji, choice_signature, selected_kanji, correct, reviewed_at, rung
            ) VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ) {
            bindText(1, stored.targetKanji)
            bindText(2, stored.choiceSignature)
            bindText(3, evaluated.selectedKanji)
            bindLong(4, if (evaluated.correct) 1L else 0L)
            bindLong(5, choice.reviewedAtMillis)
            bindText(6, dev.bee.kanjianki.core.RecordsBase.LadderRung.SIMILAR_KANJI.wireName())
        }
    }

    private fun SqlSession.similarChoiceCard(
        targetKanji: String,
        choiceSignature: String,
    ): RecordsImportModels.SimilarKanjiChoiceCard? =
        queryOneOrNull(
            """
            SELECT * FROM similar_kanji_choice_state
            WHERE target_kanji = ? AND choice_signature = ?
            LIMIT 1
            """.trimIndent(),
            bind = {
                bindText(1, targetKanji)
                bindText(2, choiceSignature)
            },
        ) { row ->
            val values = NamedSqlRow(row)
            RecordsImportModels.SimilarKanjiChoiceCard(
                values.text("target_kanji"),
                values.text("primary_meaning"),
                dev.bee.kanjianki.core.SimilarChoiceCodec.deserializeChoices(values.text("choices")),
                values.text("choice_signature"),
                values.long("due_at"),
                values.long("passed_at"),
                values.long("last_reviewed_at"),
                values.int("correct_count"),
                values.int("wrong_count"),
            )
        }

    private fun SqlTransactionScope.resolveLegacyRepair(
        repairId: Long,
        token: String?,
        update: (RecordsImportModels.SimilarKanjiWritingRepair) -> SimilarKanjiRepairPolicy.FinishUpdate,
    ): Boolean {
        val current = SqlStudyData(this).similarWritingRepair(repairId)
        if (current == null || current.status != STATUS_PENDING) return false
        if (current.activeToken.isNotEmpty() && current.activeToken != (token ?: "")) return false
        val finish = update(current)
        val assignments = ArrayList<String>()
        assignments += "active_token = ?"
        assignments += "updated_at = ?"
        finish.status()?.let { assignments += "status = ?" }
        finish.completedAtMillis()?.let { assignments += "completed_at = ?" }
        finish.attempts()?.let { assignments += "attempts = ?" }
        finish.dueAtMillis()?.let { assignments += "due_at = ?" }
        prepare(
            "UPDATE similar_kanji_repair_queue SET ${assignments.joinToString(",")} WHERE id = ?",
        ).use { statement ->
            var index = 1
            bindNullableText(statement, index++, finish.activeToken())
            statement.bindLong(index++, finish.updatedAtMillis())
            finish.status()?.let { statement.bindText(index++, it) }
            finish.completedAtMillis()?.let { statement.bindLong(index++, it) }
            finish.attempts()?.let { statement.bindLong(index++, it.toLong()) }
            finish.dueAtMillis()?.let { statement.bindLong(index++, it) }
            statement.bindLong(index, repairId)
            statement.execute()
        }
        return true
    }

    private fun SqlTransactionScope.appendReviewTimelineEvent(
        request: RecordsSchedulerModels.ReviewRequest,
        appliedRating: String?,
        reviewedAt: Long,
        dedupeKey: String,
    ) {
        val event = TimelineCopy.reviewEvent(request, appliedRating)
        val source = firstExampleForKanji(request.kanji)
        val row = rowSnapshotForKanji(request.kanji)
        prepare(
            """
            INSERT INTO kanji_timeline_events(
                kanji, occurred_at, event_type, title, detail, source_expression, source_reading,
                rating, writing_required, writing_passed, manual_override, weakness_score,
                mature_support_count, sync_id, dedupe_key
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.bindText(1, request.kanji)
            statement.bindLong(2, reviewedAt)
            statement.bindText(3, event.eventType())
            statement.bindText(4, event.title())
            statement.bindText(5, event.detail())
            statement.bindText(6, source.first)
            statement.bindText(7, source.second)
            bindNullableText(statement, 8, appliedRating)
            statement.bindLong(9, if (request.writingRequired) 1L else 0L)
            statement.bindLong(10, if (request.writingPassed) 1L else 0L)
            statement.bindLong(11, if (request.manualOverride) 1L else 0L)
            if (row == null) statement.bindNull(12) else statement.bindLong(12, row.first.toLong())
            if (row == null) statement.bindNull(13) else statement.bindLong(13, row.second.toLong())
            statement.bindText(14, dedupeKey)
            statement.execute()
        }
    }

    private fun SqlSession.firstExampleForKanji(kanji: String): Pair<String, String> =
        queryOneOrNull(
            """
            SELECT expression, reading FROM kanji_examples
            WHERE kanji = ?
            ORDER BY source_type ASC, id ASC
            LIMIT 1
            """.trimIndent(),
            bind = { bindText(1, kanji) },
        ) { row -> row.textOrEmpty(0) to row.textOrEmpty(1) } ?: ("" to "")

    private fun SqlSession.rowSnapshotForKanji(kanji: String): Pair<Int, Int>? =
        queryOneOrNull(
            "SELECT weakness_score, mature_support_count FROM dashboard_rows WHERE kanji = ? LIMIT 1",
            bind = { bindText(1, kanji) },
        ) { row -> row.long(0).toInt() to row.long(1).toInt() }

    private fun SqlSession.markStatsDirty() {
        executeBound(
            """
            INSERT INTO stats_cache_state(key, value)
            VALUES (?, 2)
            ON CONFLICT(key) DO UPDATE SET value = value + 1
            """.trimIndent(),
        ) {
            bindText(1, STATS_SOURCE_VERSION_KEY)
        }
    }

    private fun taskMemoryText(item: RecordsStudyModels.StudyItem?, taskType: String?): String {
        if (item == null || taskType.isNullOrEmpty()) return ""
        val ownerTask = if (AdaptiveStudyItemPolicy.isAdaptive(item)) {
            AdaptiveCorePolicy.coreForTaskType(taskType)
                ?.let(AdaptiveCorePolicy::memoryOwnerTaskType) ?: taskType
        } else {
            taskType
        }
        return item.memoryForTaskType(ownerTask).encode()
    }

    private fun studyItemSchedulerJson(item: RecordsStudyModels.StudyItem?): String {
        if (item == null) return ""
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
            ",\"active_token\":" + TextUtil.jsonQuote(item.activeToken.orEmpty()) +
            "}"
    }

    private fun ReviewTokenQuery.toLookup(): SqlStudyData.TokenLookup =
        SqlStudyData.TokenLookup(token, kanji, taskType, answerSignature)

    private class StaleSqlReviewCommit : RuntimeException()

    private companion object {
        const val STATS_SOURCE_VERSION_KEY = "stats_source_version"
        const val STATUS_PENDING = "pending"
        const val MAX_STUDY_TASK_ELAPSED_MS = 15L * 60L * 1000L

        fun familyKey(kanji: String, answerSignature: String): String = "$kanji\u0000$answerSignature"

        fun bindNullableText(statement: SqlStatement, index: Int, value: String?) {
            if (value == null) statement.bindNull(index) else statement.bindText(index, value)
        }
    }
}
