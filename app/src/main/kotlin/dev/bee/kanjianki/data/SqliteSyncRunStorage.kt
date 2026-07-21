package dev.bee.kanjianki.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException

internal class SqliteSyncRunStorage(
    private val store: LocalStoreSync,
    private val transactionDb: SQLiteDatabase? = null,
) : SyncRunStorage {
    override fun insert(record: SyncRunRecord): Long {
        val inserted = writableDatabase().insertOrThrow(LocalStoreBase.TABLE_SYNC_RUNS, null, values(record))
        if (inserted == -1L) {
            throw SQLiteException("Failed to insert required sync run")
        }
        return inserted
    }

    override fun updateRemovalMessage(syncId: Long, message: String) {
        val values = ContentValues()
        values.put(LocalStoreBase.COLUMN_REMOVAL_MESSAGE, message)
        writableDatabase().update(
            LocalStoreBase.TABLE_SYNC_RUNS,
            values,
            "id=?",
            arrayOf(syncId.toString()),
        )
    }

    override fun updateStatus(syncId: Long, status: String) {
        val values = ContentValues()
        values.put(LocalStoreBase.COLUMN_STATUS, status)
        writableDatabase().update(
            LocalStoreBase.TABLE_SYNC_RUNS,
            values,
            "id=?",
            arrayOf(syncId.toString()),
        )
    }

    private fun writableDatabase(): SQLiteDatabase = transactionDb ?: store.writableDatabase

    private fun values(record: SyncRunRecord): ContentValues {
        val values = ContentValues()
        values.put(LocalStoreBase.COLUMN_STARTED_AT, record.startedAt)
        values.put(LocalStoreBase.COLUMN_FINISHED_AT, record.finishedAt)
        values.put(LocalStoreBase.COLUMN_STATUS, record.status)
        values.put(LocalStoreBase.COLUMN_ACTIVE_NOTES_COUNT, record.activeNotesCount)
        values.put(LocalStoreBase.COLUMN_ACTIVE_CARDS_COUNT, record.activeCardsCount)
        values.put(LocalStoreBase.COLUMN_SUSPENDED_CARDS_ARCHIVED_COUNT, record.archivedSuspendedCardCount)
        values.put(LocalStoreBase.COLUMN_SUSPENDED_KANJI_IMPORTED_COUNT, record.importedSuspendedKanjiCount)
        values.put("deleted_notes_count", record.deletedNotesCount)
        values.put("deleted_cards_count", record.deletedCardsCount)
        values.put("error_code", record.errorCode)
        values.put(LocalStoreBase.COLUMN_ERROR_MESSAGE, record.errorMessage)
        values.put(LocalStoreBase.COLUMN_REMOVAL_MESSAGE, record.removalMessage)
        return values
    }
}
