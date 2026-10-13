package dev.bee.kanjianki.data.sql

import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.core.ManualKanjiAdmissionPolicy
import dev.bee.kanjianki.core.MissingKanjiTextCopy
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SuspendedImportPolicy
import dev.bee.kanjianki.syncdomain.SyncMirrorPolicy
import dev.bee.kanjianki.data.AdaptiveWorkloadSnapshot
import dev.bee.kanjianki.data.RecordRepairedWriteBackCommand
import dev.bee.kanjianki.data.RecordSyncFailureCommand
import dev.bee.kanjianki.data.SyncPublicationCommand
import dev.bee.kanjianki.data.SyncPublicationResult
import dev.bee.kanjianki.data.SyncQueuePlanningSnapshot
import dev.bee.kanjianki.data.SyncRepository

/**
 * Driver-neutral sync publication and history. The provider mirror, derived
 * dashboard/inventory, and seeded study queue publish behind one transaction;
 * the pending sync_run is only flipped to `success` after the queue commits.
 * Write-back receipts are separate, idempotent, post-commit transactions.
 * Android production stays on LocalStore until Goal 184.
 */
class SqlSyncRepository(
    private val database: SqlDatabase,
    private val invalidation: SqlProjectionInvalidation = SqlProjectionInvalidation(),
) : SyncRepository {
    override suspend fun loadStoredState() = safeSqlStoreCall {
        database.readSnapshot { SqlSyncData(this).loadStoredState() }
    }

    override suspend fun publish(command: SyncPublicationCommand) = safeSqlStoreCall {
        val result = database.write { publishInternal(command) }
        invalidation.invalidateHome()
        result
    }

    override suspend fun recordFailure(command: RecordSyncFailureCommand) = safeSqlStoreCall {
        database.write {
            insertRow(
                "sync_runs",
                "ABORT",
                linkedMapOf(
                    "started_at" to command.startedAtMillis,
                    "finished_at" to command.finishedAtMillis,
                    "status" to command.status,
                    "active_notes_count" to 0,
                    "active_cards_count" to 0,
                    "suspended_cards_archived_count" to 0,
                    "suspended_kanji_imported_count" to 0,
                    "deleted_notes_count" to 0,
                    "deleted_cards_count" to 0,
                    "error_code" to command.errorCode,
                    "error_message" to command.errorMessage,
                    "removal_message" to "",
                ),
            )
        }
        Unit
    }

    override suspend fun updateRemovalMessage(syncId: Long, message: String?) = safeSqlStoreCall {
        database.write {
            executeBound(
                "UPDATE sync_runs SET removal_message = ? WHERE id = ?",
            ) {
                bindText(1, message.orEmpty())
                bindLong(2, syncId)
            }
        }
        Unit
    }

    override suspend fun repairedWriteBackProposal(
        snapshot: RecordsSyncModels.CollectionSnapshot,
        matureSupportThreshold: Int,
    ) = safeSqlStoreCall {
        database.readSnapshot {
            SqlRepairedWriteBackData(this).proposal(snapshot, matureSupportThreshold)
        }
    }

    override suspend fun repairedWriteBackPreview(matureSupportThreshold: Int) = safeSqlStoreCall {
        database.readSnapshot {
            SqlRepairedWriteBackData(this).preview(matureSupportThreshold)
        }
    }

    override suspend fun recordRepairedWriteBack(command: RecordRepairedWriteBackCommand) = safeSqlStoreCall {
        val repaired = database.write {
            SqlRepairedWriteBackData(this).record(
                command.proposal,
                command.taggedNoteIds,
                command.occurredAtMillis,
                command.syncId,
            )
        }
        if (repaired.isNotEmpty()) invalidation.invalidateHome()
        repaired
    }

    override suspend fun loadRepairedHandoff() = safeSqlStoreCall {
        database.readSnapshot { SqlSyncData(this).pendingRepairedHandoffKanji() }
    }

    override suspend fun dismissRepairedHandoff() = safeSqlStoreCall {
        database.write {
            executeBound(
                """
                INSERT INTO settings(key, value, updated_at)
                VALUES (?, '', 0)
                ON CONFLICT(key) DO UPDATE SET value = ''
                """.trimIndent(),
            ) {
                bindText(1, SqlSyncData.REPAIRED_HANDOFF_SETTING_KEY)
            }
        }
        Unit
    }

    private fun SqlTransactionScope.publishInternal(command: SyncPublicationCommand): SyncPublicationResult {
        val publisher = SqlSyncPublisher(this)
        val snapshot = command.snapshot
        val settings = command.settings
        val nowMillis = command.timing.finishedAtMillis
        val activeIndex = SyncMirrorPolicy.activeCardIndex(
            snapshot.cards.map { SyncMirrorPolicy.Card(it.cardId, it.noteId, it.suspended) },
        )
        val activeNoteIds = activeIndex.noteIds
        val selectedSuspendedCardIds = SyncMirrorPolicy.selectedSuspendedCardIds(
            command.imports.flatMap { imported ->
                imported.sources.map { SyncMirrorPolicy.SelectedSource(it.cardId, it.suspended) }
            },
        ) ?: emptySet()

        val previousRows = publisher.rowSnapshots()
        val deletedNotes = publisher.countExistingMissing("source_notes", "note_id", activeNoteIds)
        val deletedCards = publisher.countExistingMissing("source_cards", "card_id", activeIndex.cardIds)

        val syncId = publisher.insertSyncRun(
            startedAt = command.timing.startedAtMillis,
            finishedAt = nowMillis,
            status = STATUS_PENDING,
            activeNotesCount = activeNoteIds.size,
            activeCardsCount = activeIndex.activeCardCount,
            archivedSuspendedCardCount = selectedSuspendedCardIds.size,
            importedSuspendedKanjiCount = command.imports.size,
            deletedNotes = deletedNotes,
            deletedCards = deletedCards,
            removalMessage = command.removalMessage.orEmpty(),
        )
        publisher.purgeNonSuccessfulSyncTimelineEvents()
        val notesById = snapshot.notesById()
        publisher.appendHistoricalSyncSnapshots(
            snapshot, notesById, command.rows, settings, syncId,
            command.timing.startedAtMillis, nowMillis,
        )
        publisher.clearMirrorTables()
        publisher.saveSourceNotes(snapshot.notes, activeNoteIds, settings, syncId)
        publisher.saveSourceCardsAndArchive(
            snapshot.cards, notesById, selectedSuspendedCardIds, settings, nowMillis, syncId,
        )
        publisher.saveSuspendedImports(command.imports, nowMillis, syncId)
        publisher.saveImportAudit(command.auditImports.ifEmpty { command.imports }, settings, nowMillis, syncId)
        publisher.clearDashboardExampleTables()
        publisher.saveDashboardRows(command.rows, nowMillis)
        publisher.rebuildKanjiInventory(snapshot, command.imports, command.rows, nowMillis, settings, activeNoteIds)
        publisher.rebuildSimilarKanjiPairs(command.similarIndex, nowMillis)
        publisher.rebuildSimilarKanjiChoiceStates(nowMillis)
        publisher.rebuildKanjiReadingUsage(command.rows, command.dictionary)
        publisher.appendSuspendedImportedEvents(command.imports, syncId, nowMillis)
        publisher.appendRowTimelineEvents(previousRows, command.rows, syncId, nowMillis, settings.matureSupportThreshold)
        publisher.markStatsDirty()

        // Queue publication in the same transaction: plan, seed, and finalize.
        val settingsSnapshot = SqlSettingsRepository.readSnapshot(this)
        val locallySuspended = publisher.locallySuspendedKanji()
        val queueRows = ManualKanjiAdmissionPolicy.mergeRows(
            providerRows = command.rows,
            candidates = publisher.manualCandidates(),
            reasonText = MissingKanjiTextCopy.dictionarySourceReason(),
        )
        val activeRows = SuspendedImportPolicy.activeRows(queueRows, locallySuspended)
        val currentItems = publisher.studyItems()
        val studyStreak = SqlHomeData(this).studyStreak(nowMillis)
        val plan = command.queuePlanner.plan(
            SyncQueuePlanningSnapshot(
                providerRows = command.rows,
                rows = queueRows,
                activeRows = activeRows,
                currentItems = currentItems,
                locallySuspendedKanji = locallySuspended,
                settings = settings,
                repairEvidenceInputs = SqlRepairEvidenceReader(this).inputs(),
                studyLadder = settingsSnapshot.studyLadder,
                schedulerParameters = settingsSnapshot.schedulerParameters,
                schedulerFsrsWeights = settingsSnapshot.schedulerFsrsWeights,
                learningSteps = settingsSnapshot.learningSteps,
                adaptiveWorkload = AdaptiveWorkloadSnapshot(
                    settingsSnapshot.adaptiveWorkload.workPercent,
                    settingsSnapshot.adaptiveWorkload.maxItems,
                    settingsSnapshot.adaptiveWorkload.mode,
                ),
                recentReviewStats = reviewStatsSince(nowMillis - RECENT_REVIEW_WINDOW_MILLIS),
                currentStudyStreakDays = studyStreak.currentDays,
                studiedKanjiToday = studiedKanjiSince(LocalDayPolicy.localDayStart(nowMillis)),
                syncStartedAtMillis = command.timing.startedAtMillis,
                nowMillis = nowMillis,
            ),
        )
        val annotatedItems = SqlHomeData(this).annotateConditionalRungs(plan.items)
        commitPendingQueue(publisher, annotatedItems, syncId, nowMillis, settings, currentItems)

        return SyncPublicationResult(
            syncId = syncId,
            activeRows = activeRows,
            adaptiveLoadPlan = plan.adaptiveLoadPlan,
        )
    }

    private fun SqlTransactionScope.commitPendingQueue(
        publisher: SqlSyncPublisher,
        items: List<dev.bee.kanjianki.core.RecordsStudyModels.StudyItem>,
        syncId: Long,
        occurredAt: Long,
        settings: RecordsSyncModels.Settings,
        baseline: List<dev.bee.kanjianki.core.RecordsStudyModels.StudyItem>,
    ) {
        val previousStates = publisher.studyStateSnapshots()
        val persisted = publisher.studyItems()
        val merged = dev.bee.kanjianki.core.MidSyncReviewMergePolicy.merge(items, baseline, persisted)
        val retained = dev.bee.kanjianki.core.DurableStudyItemRetentionPolicy.retainUnseeded(merged, persisted)
        val toWrite = versionMaterialSyncChanges(retained, persisted)
        publisher.deleteAllStudyItems()
        toWrite.forEach(publisher::upsertStudyItem)
        publisher.appendStudyStateEvents(previousStates, toWrite, syncId, occurredAt, settings.matureSupportThreshold)
        publisher.finalizePendingSyncRun(syncId)
        publisher.purgeNonSuccessfulSnapshots()
        publisher.pruneSupersededSnapshots()
        publisher.markStatsDirty()
    }

    private fun versionMaterialSyncChanges(
        candidates: List<dev.bee.kanjianki.core.RecordsStudyModels.StudyItem>,
        persisted: List<dev.bee.kanjianki.core.RecordsStudyModels.StudyItem>,
    ): List<dev.bee.kanjianki.core.RecordsStudyModels.StudyItem> =
        candidates.map { candidate ->
            val existing = dev.bee.kanjianki.core.StudyItemLineagePolicy.counterpart(candidate, persisted)
                ?: return@map candidate
            if (dev.bee.kanjianki.core.StudyItemComparators.samePersistedState(existing, candidate)) {
                candidate.copyBuilder().schedulerRevision(existing.schedulerRevision).build()
            } else {
                candidate.copyBuilder()
                    .schedulerRevision(Math.addExact(existing.schedulerRevision, 1L))
                    .build()
            }
        }

    private fun SqlSession.reviewStatsSince(sinceMillis: Long) =
        queryOneOrNull(
            """
            SELECT
                COUNT(*) AS total,
                COALESCE(SUM(CASE WHEN rating='again' THEN 1 ELSE 0 END), 0) AS again_count,
                COALESCE(SUM(CASE WHEN rating='hard' THEN 1 ELSE 0 END), 0) AS hard_count,
                COALESCE(SUM(CASE WHEN rating='easy' THEN 1 ELSE 0 END), 0) AS easy_count,
                COALESCE(SUM(CASE WHEN rating NOT IN ('again','hard','easy') THEN 1 ELSE 0 END), 0) AS good_count,
                COALESCE(SUM(CASE WHEN writing_required=1 THEN 1 ELSE 0 END), 0) AS writing_required_count,
                COALESCE(SUM(CASE WHEN writing_required=1 AND writing_passed=0 AND manual_override=0 THEN 1 ELSE 0 END), 0) AS writing_failed_count
            FROM review_log WHERE reviewed_at >= ?
            """.trimIndent(),
            bind = { bindLong(1, sinceMillis) },
        ) { row ->
            dev.bee.kanjianki.core.RecordsSchedulerModels.ReviewStats(
                row.long(0).toInt(),
                row.long(1).toInt(),
                row.long(2).toInt(),
                row.long(4).toInt(),
                row.long(3).toInt(),
                row.long(5).toInt(),
                row.long(6).toInt(),
            )
        } ?: dev.bee.kanjianki.core.RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0)

    private fun SqlSession.studiedKanjiSince(sinceMillis: Long): Set<String> =
        queryList(
            "SELECT DISTINCT kanji FROM review_log WHERE reviewed_at >= ?",
            bind = { bindLong(1, sinceMillis) },
        ) { row -> row.text(0) }.filter(String::isNotEmpty).toSet()

    private companion object {
        const val STATUS_PENDING = "pending"
        const val RECENT_REVIEW_WINDOW_MILLIS = 7L * 86_400_000L
    }
}
