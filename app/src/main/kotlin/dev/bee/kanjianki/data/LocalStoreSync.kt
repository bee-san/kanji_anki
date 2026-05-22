package dev.bee.kanjianki.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SimilarKanjiIndex

internal abstract class LocalStoreSync(context: Context?) : LocalStoreInventory(context) {
    private fun syncRunStore(): LocalStoreSyncRunStore {
        return LocalStoreSyncRunStore(this)
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
        val db = writableDatabase
        return db.transaction {
            val decisionImports = auditImports ?: imports
            val previousRows = rowSnapshots(db)
            val activeIndex = activeCardIndex(snapshot.cards)
            val selectedSuspendedCardIds = selectedSuspendedCardIds(imports)
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
            val syncId = syncRunStore().insertSyncRun(
                db,
                LocalStoreBase.SyncRunInsert(
                    timing.startedAt,
                    timing.finishedAt,
                    STATUS_SUCCESS,
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
            appendSyncTimelineEvents(db, previousRows, imports, rows, syncId, timing.finishedAt, settings)
            syncId
        }
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
        syncRunStore().saveFailedSync(startedAt, finishedAt, status, errorCode, errorMessage)
    }

    fun updateSyncRemovalMessage(syncId: Long, message: String?) {
        syncRunStore().updateSyncRemovalMessage(syncId, message)
    }
}
