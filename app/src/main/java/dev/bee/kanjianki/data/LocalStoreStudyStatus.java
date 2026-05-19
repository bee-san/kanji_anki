package dev.bee.kanjianki.data;

import android.database.Cursor;

import java.util.ArrayList;
import java.util.List;

final class LocalStoreStudyStatus {
    private final LocalStore store;

    LocalStoreStudyStatus(LocalStore store) {
        this.store = store;
    }

    List<String> consumedTokens() {
        List<String> tokens = new ArrayList<>();
        try (Cursor cursor = store.getReadableDatabase().query(LocalStoreBase.TABLE_REVIEW_LOG, new String[]{LocalStoreBase.COLUMN_TOKEN}, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                tokens.add(LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_TOKEN));
            }
        }
        return tokens;
    }

    LocalStoreBase.SyncStatus latestSync() {
        try (Cursor cursor = store.getReadableDatabase().query(LocalStoreBase.TABLE_SYNC_RUNS, null, null, null, null, null, LocalStoreBase.ORDER_ID_DESC, "1")) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new LocalStoreBase.SyncStatus(new LocalStoreBase.SyncStatusValues(
                    LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_STATUS),
                    LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_ACTIVE_NOTES_COUNT),
                    LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_ACTIVE_CARDS_COUNT),
                    LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_SUSPENDED_CARDS_ARCHIVED_COUNT),
                    LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_SUSPENDED_KANJI_IMPORTED_COUNT),
                    LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_FINISHED_AT),
                    LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_ERROR_MESSAGE),
                    LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_REMOVAL_MESSAGE)
            ));
        }
    }

    boolean hasSuccessfulSyncSince(long finishedAtMillis) {
        try (Cursor cursor = store.getReadableDatabase().query(
                LocalStoreBase.TABLE_SYNC_RUNS,
                new String[]{"id"},
                "status=? AND finished_at>=?",
                new String[]{LocalStoreBase.STATUS_SUCCESS, Long.toString(finishedAtMillis)},
                null,
                null,
                LocalStoreBase.ORDER_ID_DESC,
                "1"
        )) {
            return cursor.moveToFirst();
        }
    }
}
