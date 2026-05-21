package dev.bee.kanjianki.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

internal class LocalStoreSyncRunStore(
    private val store: LocalStoreSync,
) {
    fun insertSyncRun(db: SQLiteDatabase, syncRun: LocalStoreBase.SyncRunInsert): Long {
        val values = ContentValues()
        values.put(LocalStoreBase.COLUMN_STARTED_AT, syncRun.startedAt())
        values.put(LocalStoreBase.COLUMN_FINISHED_AT, syncRun.finishedAt())
        values.put(LocalStoreBase.COLUMN_STATUS, syncRun.status())
        values.put(LocalStoreBase.COLUMN_ACTIVE_NOTES_COUNT, syncRun.activeIndex().noteIds.size)
        values.put(LocalStoreBase.COLUMN_ACTIVE_CARDS_COUNT, syncRun.activeIndex().activeCardCount)
        values.put(LocalStoreBase.COLUMN_SUSPENDED_CARDS_ARCHIVED_COUNT, syncRun.archivedSuspendedCardCount())
        values.put(LocalStoreBase.COLUMN_SUSPENDED_KANJI_IMPORTED_COUNT, syncRun.importCount())
        values.put("deleted_notes_count", syncRun.deletedNotes())
        values.put("deleted_cards_count", syncRun.deletedCards())
        values.put("error_code", syncRun.errorCode())
        values.put(LocalStoreBase.COLUMN_ERROR_MESSAGE, syncRun.errorMessage())
        values.put(LocalStoreBase.COLUMN_REMOVAL_MESSAGE, syncRun.removalMessage())
        return db.insert(LocalStoreBase.TABLE_SYNC_RUNS, null, values)
    }

    fun saveFailedSync(
        startedAt: Long,
        finishedAt: Long,
        status: String?,
        errorCode: String?,
        errorMessage: String?,
    ) {
        val values = ContentValues()
        values.put(LocalStoreBase.COLUMN_STARTED_AT, startedAt)
        values.put(LocalStoreBase.COLUMN_FINISHED_AT, finishedAt)
        values.put(LocalStoreBase.COLUMN_STATUS, status)
        values.put(LocalStoreBase.COLUMN_ACTIVE_NOTES_COUNT, 0)
        values.put(LocalStoreBase.COLUMN_ACTIVE_CARDS_COUNT, 0)
        values.put(LocalStoreBase.COLUMN_SUSPENDED_CARDS_ARCHIVED_COUNT, 0)
        values.put(LocalStoreBase.COLUMN_SUSPENDED_KANJI_IMPORTED_COUNT, 0)
        values.put("deleted_notes_count", 0)
        values.put("deleted_cards_count", 0)
        values.put("error_code", errorCode)
        values.put(LocalStoreBase.COLUMN_ERROR_MESSAGE, errorMessage)
        values.put(LocalStoreBase.COLUMN_REMOVAL_MESSAGE, "")
        store.writableDatabase.insert(LocalStoreBase.TABLE_SYNC_RUNS, null, values)
    }

    fun updateSyncRemovalMessage(syncId: Long, message: String?) {
        val values = ContentValues()
        values.put(LocalStoreBase.COLUMN_REMOVAL_MESSAGE, message ?: "")
        store.writableDatabase.update(
            LocalStoreBase.TABLE_SYNC_RUNS,
            values,
            "id=?",
            arrayOf(syncId.toString()),
        )
    }
}
