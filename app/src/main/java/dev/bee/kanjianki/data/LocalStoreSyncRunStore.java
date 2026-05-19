package dev.bee.kanjianki.data;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

final class LocalStoreSyncRunStore {
    private final LocalStoreSync store;

    LocalStoreSyncRunStore(LocalStoreSync store) {
        this.store = store;
    }

    long insertSyncRun(SQLiteDatabase db, LocalStoreBase.SyncRunInsert syncRun) {
        ContentValues values = new ContentValues();
        values.put(LocalStoreBase.COLUMN_STARTED_AT, syncRun.startedAt());
        values.put(LocalStoreBase.COLUMN_FINISHED_AT, syncRun.finishedAt());
        values.put(LocalStoreBase.COLUMN_STATUS, syncRun.status());
        values.put(LocalStoreBase.COLUMN_ACTIVE_NOTES_COUNT, syncRun.activeIndex().noteIds.size());
        values.put(LocalStoreBase.COLUMN_ACTIVE_CARDS_COUNT, syncRun.activeIndex().activeCardCount);
        values.put(LocalStoreBase.COLUMN_SUSPENDED_CARDS_ARCHIVED_COUNT, syncRun.archivedSuspendedCardCount());
        values.put(LocalStoreBase.COLUMN_SUSPENDED_KANJI_IMPORTED_COUNT, syncRun.importCount());
        values.put("deleted_notes_count", syncRun.deletedNotes());
        values.put("deleted_cards_count", syncRun.deletedCards());
        values.put("error_code", syncRun.errorCode());
        values.put(LocalStoreBase.COLUMN_ERROR_MESSAGE, syncRun.errorMessage());
        values.put(LocalStoreBase.COLUMN_REMOVAL_MESSAGE, syncRun.removalMessage());
        return db.insert(LocalStoreBase.TABLE_SYNC_RUNS, null, values);
    }

    void saveFailedSync(long startedAt, long finishedAt, String status, String errorCode, String errorMessage) {
        ContentValues values = new ContentValues();
        values.put(LocalStoreBase.COLUMN_STARTED_AT, startedAt);
        values.put(LocalStoreBase.COLUMN_FINISHED_AT, finishedAt);
        values.put(LocalStoreBase.COLUMN_STATUS, status);
        values.put(LocalStoreBase.COLUMN_ACTIVE_NOTES_COUNT, 0);
        values.put(LocalStoreBase.COLUMN_ACTIVE_CARDS_COUNT, 0);
        values.put(LocalStoreBase.COLUMN_SUSPENDED_CARDS_ARCHIVED_COUNT, 0);
        values.put(LocalStoreBase.COLUMN_SUSPENDED_KANJI_IMPORTED_COUNT, 0);
        values.put("deleted_notes_count", 0);
        values.put("deleted_cards_count", 0);
        values.put("error_code", errorCode);
        values.put(LocalStoreBase.COLUMN_ERROR_MESSAGE, errorMessage);
        values.put(LocalStoreBase.COLUMN_REMOVAL_MESSAGE, "");
        store.getWritableDatabase().insert(LocalStoreBase.TABLE_SYNC_RUNS, null, values);
    }

    void updateSyncRemovalMessage(long syncId, String message) {
        ContentValues values = new ContentValues();
        values.put(LocalStoreBase.COLUMN_REMOVAL_MESSAGE, message == null ? "" : message);
        store.getWritableDatabase().update(LocalStoreBase.TABLE_SYNC_RUNS, values, "id=?", new String[]{Long.toString(syncId)});
    }
}
