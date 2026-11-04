package dev.bee.kanjianki.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.DurableStudyItemRetentionPolicy
import dev.bee.kanjianki.core.MidSyncReviewMergePolicy
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyItemComparators
import dev.bee.kanjianki.core.StudyItemLineagePolicy
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SimilarKanjiIndex

internal abstract class LocalStoreSync(
    context: Context?,
    diagnosticLogger: DiagnosticLogger,
) : LocalStoreInventory(context, diagnosticLogger) {
    /**
     * Keeps composite repository reads on one database generation. A non-exclusive
     * transaction permits concurrent readers while preventing a sync/settings writer
     * from publishing between component queries, including queries served from caches.
     */
    fun <T> readSnapshot(block: () -> T): T {
        return readableDatabase.transaction(exclusive = false) { block() }
    }

    /**
     * Publish the provider mirror, derived dashboard/inventory, and seeded study
     * queue behind one outer SQLite transaction. The existing save/finalize
     * methods use nested transactions; Android defers their commits until this
     * outer transaction succeeds, so a queue-build failure cannot expose a new
     * mirror paired with stale scheduler state.
     */
    fun <T> publishSyncAtomically(block: () -> T): T {
        return try {
            writableDatabase.transaction { block() }
        } finally {
            // Nested save methods may invalidate while the outer transaction is
            // still open. Clear once more after commit/rollback so no cache can
            // retain data observed from the unpublished transaction.
            clearDashboardRowsCache()
            clearStudyItemsCache()
            clearKanjiInventoryAllCache()
        }
    }

    fun hasPersistedCollectionMirror(): Boolean {
        val db = readableDatabase
        return tableHasRows(db, TABLE_SOURCE_NOTES, COLUMN_NOTE_ID) ||
            tableHasRows(db, TABLE_SOURCE_CARDS, COLUMN_CARD_ID)
    }

    fun isEmptyKaniProfile(): Boolean {
        val db = readableDatabase
        val hasSuccessfulSync = db.query(
            TABLE_SYNC_RUNS,
            arrayOf("id"),
            "$COLUMN_STATUS=?",
            arrayOf(STATUS_SUCCESS),
            null,
            null,
            null,
            "1",
        ).use { cursor -> cursor.moveToFirst() }
        return !hasSuccessfulSync && PROFILE_STATE_TABLES.none { table ->
            tableHasRows(db, table)
        }
    }

    fun collectionMirrorIdentityEvidence(): CollectionMirrorIdentityEvidence =
        CollectionMirrorIdentityEvidence(
            stableNoteIds = stableIdSample(TABLE_SOURCE_NOTES, COLUMN_NOTE_ID),
            stableCardIds = stableIdSample(TABLE_SOURCE_CARDS, COLUMN_CARD_ID),
        )

    private fun stableIdSample(table: String, column: String): List<Long> {
        val ids = ArrayList<Long>(64)
        readableDatabase.query(
            table,
            arrayOf(column),
            null,
            null,
            null,
            null,
            "($column < 0) ASC, $column ASC",
            "64",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                ids += cursor.getLong(0)
            }
        }
        return ids
    }

    private fun tableHasRows(db: SQLiteDatabase, table: String, idColumn: String): Boolean {
        return db.query(table, arrayOf(idColumn), null, null, null, null, null, "1").use { it.moveToFirst() }
    }

    private fun tableHasRows(db: SQLiteDatabase, table: String): Boolean {
        return db.rawQuery("SELECT 1 FROM $table LIMIT 1", null).use { cursor ->
            cursor.moveToFirst()
        }
    }

    private fun syncRunRepository(): SyncRunRepository {
        return SyncRunRepository(SqliteSyncRunStorage(this))
    }

    private fun importAuditStore(): LocalStoreSyncImportAuditStore {
        return LocalStoreSyncImportAuditStore()
    }

    private fun sourceStore(): LocalStoreSyncSourceStore {
        return LocalStoreSyncSourceStore()
    }

    private fun suspendedImportStore(): LocalStoreSyncSuspendedImportStore {
        return LocalStoreSyncSuspendedImportStore()
    }

    fun saveSuccessfulSync(
        snapshot: RecordsSyncModels.CollectionSnapshot,
        imports: List<RecordsImportModels.SuspendedImport>,
        rows: List<RecordsImportModels.DashboardRow>,
        settings: RecordsSyncModels.Settings,
        startedAt: Long,
        finishedAt: Long,
        removalMessage: String?,
    ): Long {
        return saveSuccessfulSync(
            snapshot,
            imports,
            rows,
            settings,
            LocalStoreBase.SyncTiming(startedAt, finishedAt),
            removalMessage,
            null,
        )
    }

    fun saveSuccessfulSync(
        snapshot: RecordsSyncModels.CollectionSnapshot,
        imports: List<RecordsImportModels.SuspendedImport>,
        rows: List<RecordsImportModels.DashboardRow>,
        settings: RecordsSyncModels.Settings,
        timing: LocalStoreBase.SyncTiming,
        removalMessage: String?,
        similarIndex: SimilarKanjiIndex?,
    ): Long {
        return saveSuccessfulSync(snapshot, imports, rows, settings, timing, removalMessage, similarIndex, imports)
    }

    fun saveSuccessfulSync(
        snapshot: RecordsSyncModels.CollectionSnapshot,
        imports: List<RecordsImportModels.SuspendedImport>,
        rows: List<RecordsImportModels.DashboardRow>,
        settings: RecordsSyncModels.Settings,
        timing: LocalStoreBase.SyncTiming,
        removalMessage: String?,
        similarIndex: SimilarKanjiIndex?,
        auditImports: List<RecordsImportModels.SuspendedImport>?,
    ): Long {
        return saveSuccessfulSync(
            snapshot,
            imports,
            rows,
            settings,
            timing,
            removalMessage,
            similarIndex,
            auditImports,
            STATUS_SUCCESS,
        )
    }

    /**
     * Persist a sync's mirror + sync_run row in one transaction. [initialStatus]
     * controls the sync_run status: callers that will commit study items in a
     * separate transaction should pass [STATUS_PENDING] and call
     * [commitPendingSyncStudyItems] for the queue publication, so a crash before that
     * atomic commit leaves a `pending` row that `hasSuccessfulSyncSince` ignores
     * (auto-sync retries) instead
     * of a committed `success` sitting on stale study items.
     */
    fun saveSuccessfulSync(
        snapshot: RecordsSyncModels.CollectionSnapshot,
        imports: List<RecordsImportModels.SuspendedImport>,
        rows: List<RecordsImportModels.DashboardRow>,
        settings: RecordsSyncModels.Settings,
        timing: LocalStoreBase.SyncTiming,
        removalMessage: String?,
        similarIndex: SimilarKanjiIndex?,
        auditImports: List<RecordsImportModels.SuspendedImport>?,
        initialStatus: String,
    ): Long {
        return saveSuccessfulSync(
            snapshot,
            imports,
            rows,
            settings,
            timing,
            removalMessage,
            similarIndex,
            auditImports,
            initialStatus,
            null,
        )
    }

    /**
     * Terminal overload. [dictionary] (the bundled KANJIDIC2 lookup) is used to
     * rebuild the reading-usage content tables (Goal 77); pass null in tests
     * that do not exercise reading rungs — the tables are then left empty.
     */
    fun saveSuccessfulSync(
        snapshot: RecordsSyncModels.CollectionSnapshot,
        imports: List<RecordsImportModels.SuspendedImport>,
        rows: List<RecordsImportModels.DashboardRow>,
        settings: RecordsSyncModels.Settings,
        timing: LocalStoreBase.SyncTiming,
        removalMessage: String?,
        similarIndex: SimilarKanjiIndex?,
        auditImports: List<RecordsImportModels.SuspendedImport>?,
        initialStatus: String,
        dictionary: DictionaryLookup?,
    ): Long {
        val db = writableDatabase
        val syncId = db.transaction {
            val decisionImports = auditImports ?: imports
            val previousRows = rowSnapshots(db)
            val activeIndex = LocalStoreSyncMirrorAdapters.activeCardIndex(snapshot.cards)
            val selectedSuspendedCardIds = LocalStoreSyncMirrorAdapters.selectedSuspendedCardIds(imports)
            val deletedNotes = countDeletedExisting(
                db,
                TABLE_SOURCE_NOTES,
                COLUMN_NOTE_ID,
                activeIndex.noteIds,
            )
            val deletedCards = countDeletedExisting(
                db,
                TABLE_SOURCE_CARDS,
                COLUMN_CARD_ID,
                activeIndex.cardIds,
            )
            val syncId = SyncRunRepository(SqliteSyncRunStorage(this@LocalStoreSync, db)).insertSuccessfulSync(
                LocalStoreBase.SyncRunInsert(
                    timing.startedAt,
                    timing.finishedAt,
                    initialStatus,
                    activeIndex,
                    selectedSuspendedCardIds.size,
                    imports.size,
                    null,
                    null,
                    removalMessage ?: "",
                    deletedNotes,
                    deletedCards,
                ),
            )
            purgeNonSuccessfulSyncTimelineEvents(db)
            val notesById = snapshot.notesById()
            appendHistoricalSyncSnapshots(db, snapshot, notesById, rows, settings, syncId, timing)
            clearSyncMirrorTables(db)
            val sourceStoreHelper = sourceStore()
            sourceStoreHelper.saveSourceNotes(db, snapshot.notes, activeIndex, settings, syncId)
            sourceStoreHelper.saveSourceCardsAndArchive(
                db,
                snapshot.cards,
                notesById,
                selectedSuspendedCardIds,
                settings,
                timing.finishedAt,
                syncId,
            )
            suspendedImportStore().saveSuspendedImports(db, imports, timing.finishedAt, syncId)
            saveImportAudit(db, decisionImports, settings, timing.finishedAt, syncId)

            saveRows(db, rows, timing.finishedAt)
            rebuildKanjiInventory(db, snapshot, imports, rows, timing.finishedAt, settings)
            if (similarIndex != null) {
                rebuildSimilarKanjiPairs(db, similarIndex, timing.finishedAt)
            }
            rebuildSimilarKanjiChoiceStates(db, timing.finishedAt)
            LocalStoreKanjiReadingMaintenance().rebuildKanjiReadingUsage(db, rows, dictionary)
            appendSyncTimelineEvents(db, previousRows, imports, rows, syncId, timing.finishedAt, settings)
            if (initialStatus == STATUS_SUCCESS) {
                purgeNonSuccessfulSnapshots(db)
                pruneSupersededSnapshots(db)
            }
            StatsCacheStore(this@LocalStoreSync as LocalStore).markDirty(db)
            syncId
        }
        // Publish invalidation only after the new content is committed. Clearing inside the write
        // transaction lets a concurrent WAL reader see the previous snapshot and repopulate a
        // just-cleared capability cache with stale values.
        clearDashboardRowsCache()
        clearStudyItemsCache()
        clearKanjiInventoryAllCache()
        return syncId
    }

    fun saveImportAudit(
        db: SQLiteDatabase,
        imports: List<RecordsImportModels.SuspendedImport>,
        settings: RecordsSyncModels.Settings,
        finishedAt: Long,
        syncId: Long,
    ) {
        importAuditStore().saveImportAudit(db, imports, settings, finishedAt, syncId)
    }

    fun clearSyncMirrorTables(db: SQLiteDatabase) {
        db.delete(TABLE_SOURCE_CARDS, null, null)
        db.delete(TABLE_SOURCE_NOTES, null, null)
        db.delete(TABLE_DASHBOARD_ROWS, null, null)
        db.delete(TABLE_KANJI_EXAMPLES, null, null)
    }

    fun clearProviderStateForSourceRebind(db: SQLiteDatabase) {
        val providerTables = listOf(
            TABLE_SOURCE_CARDS,
            TABLE_SOURCE_NOTES,
            TABLE_DASHBOARD_ROWS,
            TABLE_KANJI_EXAMPLES,
            TABLE_SIMILAR_KANJI_PAIRS,
            TABLE_KANJI_READING_USAGE,
            TABLE_KANJI_READING_POOL,
            TABLE_KANJI_INVENTORY,
            TABLE_ANKI_KANJI_INVENTORY,
            TABLE_ANKI_KANJI_INVENTORY_SCANS,
            TABLE_MISSING_KANJI_EXPORTS,
            TABLE_STATS_SCREEN_CACHE,
        )
        for (table in providerTables) {
            db.delete(table, null, null)
        }
        // Stable-ID overlap preserves retained provider history; only its prior
        // successful write receipt must be invalidated for the replacement binding.
        db.execSQL(
            "UPDATE $TABLE_SUSPENDED_ARCHIVE SET restored_at=NULL WHERE restored_at IS NOT NULL",
        )
        db.delete(
            TABLE_SETTINGS,
            WHERE_SETTING_KEY,
            arrayOf(REPAIRED_HANDOFF_SETTING_KEY),
        )
        StatsCacheStore(this as LocalStore).markDirty(db)
    }

    fun invalidateProviderStateAfterSourceRebind() {
        clearDashboardRowsCache()
        clearStudyItemsCache()
        clearKanjiInventoryAllCache()
    }

    fun saveFailedSync(startedAt: Long, finishedAt: Long, status: String, errorCode: String, errorMessage: String?) {
        syncRunRepository().saveFailedSync(startedAt, finishedAt, status, errorCode, errorMessage)
    }

    private companion object {
        // Product settings and failed-sync diagnostics do not establish a profile
        // origin. Every retained content/progress table below does.
        val PROFILE_STATE_TABLES = listOf(
            TABLE_SOURCE_NOTES,
            TABLE_SOURCE_CARDS,
            TABLE_SUSPENDED_ARCHIVE,
            TABLE_SUSPENDED_IMPORTS,
            TABLE_SUSPENDED_SOURCES,
            TABLE_IMPORT_RULE_AUDITS,
            TABLE_IMPORT_DECISIONS,
            TABLE_DASHBOARD_ROWS,
            TABLE_KANJI_EXAMPLES,
            TABLE_STUDY_ITEMS,
            TABLE_LEARNING_REPEATS,
            TABLE_REVIEW_LOG,
            TABLE_KANJI_INVENTORY,
            TABLE_KANJI_MNEMONIC_NOTES,
            TABLE_ANKI_KANJI_INVENTORY,
            TABLE_ANKI_KANJI_INVENTORY_SCANS,
            TABLE_MANUAL_KANJI_SOURCES,
            TABLE_MISSING_KANJI_EXPORTS,
            TABLE_LOCAL_KANJI_SUSPENSIONS,
            TABLE_SIMILAR_KANJI_CHOICE_STATE,
            TABLE_SIMILAR_KANJI_REPAIR_QUEUE,
            TABLE_SIMILAR_KANJI_REVIEW_LOG,
            TABLE_KANJI_READING_USAGE,
            TABLE_KANJI_READING_POOL,
            TABLE_STUDY_TASK_LOG,
            TABLE_KANJI_TIMELINE_EVENTS,
            TABLE_SYNC_CARD_SNAPSHOTS,
            TABLE_SYNC_NOTE_SNAPSHOTS,
            TABLE_SYNC_KANJI_SNAPSHOTS,
        )
    }

    fun updateSyncRemovalMessage(syncId: Long, message: String?) {
        syncRunRepository().updateSyncRemovalMessage(syncId, message)
    }

    /**
     * Publish the seeded queue and its pending sync run as one SQLite commit.
     *
     * Historical snapshots are written with the pending run in [saveSuccessfulSync]
     * because they describe that exact provider read. They remain invisible to all
     * analytics until this transaction changes the run to `success`. Finalization also
     * removes snapshots orphaned by older interrupted/failed syncs and applies the
     * heavy-snapshot retention policy using successful runs only.
     */
    fun commitPendingSyncStudyItems(
        items: List<RecordsStudyModels.StudyItem>,
        syncId: Long,
        occurredAt: Long,
        settings: RecordsSyncModels.Settings?,
        baseline: List<RecordsStudyModels.StudyItem>?,
    ) {
        writableDatabase.transaction {
            val previous = studySnapshots(this)
            val persisted = readStudyItemsForSyncCommit(this)
            val merged = if (baseline == null) {
                items
            } else {
                MidSyncReviewMergePolicy.merge(items, baseline, persisted)
            }
            // Sync publication is never an authority to hard-delete scheduler history.
            // The normal caller seeds from the complete durable item set and explicitly
            // retires kanji missing from the provider/analyzer rows. Retain any kanji
            // an incomplete or future narrowed caller still omits as a final integrity
            // backstop; a seeded row for the same kanji remains authoritative.
            val retained = DurableStudyItemRetentionPolicy.retainUnseeded(merged, persisted)
            val toWrite = versionMaterialSyncChanges(retained, persisted)

            delete(TABLE_STUDY_ITEMS, null, null)
            for (item in toWrite) {
                upsertStudyItem(this, item)
            }
            appendStudyStateTimelineEvents(this, previous, toWrite, syncId, occurredAt, settings)

            val status = ContentValues().apply { put(COLUMN_STATUS, STATUS_SUCCESS) }
            val updated = update(
                TABLE_SYNC_RUNS,
                status,
                "id=? AND $COLUMN_STATUS=?",
                arrayOf(syncId.toString(), STATUS_PENDING),
            )
            check(updated == 1) { "Pending sync $syncId could not be finalized" }

            purgeNonSuccessfulSnapshots(this)
            pruneSupersededSnapshots(this)
            StatsCacheStore(this@LocalStoreSync as LocalStore).markDirty(this)
        }
        clearStudyItemsCache()
    }

    private fun readStudyItemsForSyncCommit(db: SQLiteDatabase): List<RecordsStudyModels.StudyItem> {
        val items = ArrayList<RecordsStudyModels.StudyItem>()
        db.query(TABLE_STUDY_ITEMS, null, null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                items.add(readStudyItem(cursor))
            }
        }
        return items
    }

    private fun purgeNonSuccessfulSyncTimelineEvents(db: SQLiteDatabase) {
        db.delete(
            TABLE_KANJI_TIMELINE_EVENTS,
            "$COLUMN_SYNC_ID IS NOT NULL AND $COLUMN_SYNC_ID NOT IN " +
                "(SELECT id FROM $TABLE_SYNC_RUNS WHERE $COLUMN_STATUS=?)",
            arrayOf(STATUS_SUCCESS),
        )
    }

    private fun versionMaterialSyncChanges(
        candidates: List<RecordsStudyModels.StudyItem>,
        persisted: List<RecordsStudyModels.StudyItem>,
    ): List<RecordsStudyModels.StudyItem> {
        return candidates.map { candidate ->
            val existing = StudyItemLineagePolicy.counterpart(candidate, persisted)
                ?: return@map candidate
            if (StudyItemComparators.samePersistedState(existing, candidate)) {
                if (candidate.schedulerRevision == existing.schedulerRevision) {
                    candidate
                } else {
                    candidate.copyBuilder().schedulerRevision(existing.schedulerRevision).build()
                }
            } else {
                val nextRevision = Math.addExact(existing.schedulerRevision, 1L)
                candidate.copyBuilder().schedulerRevision(nextRevision).build()
            }
        }
    }

    private fun countDeletedExisting(db: SQLiteDatabase, table: String, idColumn: String, currentIds: Set<Long>): Int {
        var missing = 0
        db.query(table, arrayOf(idColumn), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                if (!currentIds.contains(cursor.getLong(0))) {
                    missing++
                }
            }
        }
        return missing
    }
}
