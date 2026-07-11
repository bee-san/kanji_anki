package dev.bee.kanjianki.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SimilarKanjiIndex

internal abstract class LocalStoreSync(context: Context?) : LocalStoreInventory(context) {
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
     * separate transaction should pass [STATUS_PENDING] and call [markSyncSucceeded]
     * only after that second commit, so a crash between the two commits leaves a
     * `pending` row that `hasSuccessfulSyncSince` ignores (auto-sync retries) instead
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

    fun saveFailedSync(startedAt: Long, finishedAt: Long, status: String, errorCode: String, errorMessage: String?) {
        syncRunRepository().saveFailedSync(startedAt, finishedAt, status, errorCode, errorMessage)
    }

    fun updateSyncRemovalMessage(syncId: Long, message: String?) {
        syncRunRepository().updateSyncRemovalMessage(syncId, message)
    }

    fun markSyncSucceeded(syncId: Long) {
        syncRunRepository().markSyncSucceeded(syncId)
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
