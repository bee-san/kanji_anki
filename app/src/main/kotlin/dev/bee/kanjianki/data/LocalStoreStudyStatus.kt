package dev.bee.kanjianki.data

internal class LocalStoreStudyStatus(
    private val store: LocalStore,
) {
    /**
     * Indexed single-token idempotency check. The review submit path used to load the
     * entire (unbounded, ever-growing) review_log token column on every Pass/Fail tap
     * just to test membership of one token; the token column is UNIQUE, so a targeted
     * lookup answers the same question in O(log n).
     */
    fun hasConsumedToken(token: String): Boolean {
        store.readableDatabase.query(
            LocalStoreBase.TABLE_REVIEW_LOG,
            arrayOf(LocalStoreBase.COLUMN_TOKEN),
            "${LocalStoreBase.COLUMN_TOKEN}=?",
            arrayOf(token),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    fun consumedTokens(): List<String> {
        val tokens = mutableListOf<String>()
        store.readableDatabase.query(
            LocalStoreBase.TABLE_REVIEW_LOG,
            arrayOf(LocalStoreBase.COLUMN_TOKEN),
            null,
            null,
            null,
            null,
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                tokens.add(LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_TOKEN))
            }
        }
        return tokens
    }

    fun latestSync(): LocalStoreBase.SyncStatus? {
        store.readableDatabase.query(
            LocalStoreBase.TABLE_SYNC_RUNS,
            null,
            null,
            null,
            null,
            null,
            LocalStoreBase.ORDER_ID_DESC,
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                return null
            }
            return LocalStoreBase.SyncStatus(
                LocalStoreBase.SyncStatusValues(
                    LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_STATUS),
                    LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_ACTIVE_NOTES_COUNT),
                    LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_ACTIVE_CARDS_COUNT),
                    LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_SUSPENDED_CARDS_ARCHIVED_COUNT),
                    LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_SUSPENDED_KANJI_IMPORTED_COUNT),
                    LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_FINISHED_AT),
                    LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_ERROR_MESSAGE),
                    LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_REMOVAL_MESSAGE),
                )
            )
        }
    }

    fun hasSuccessfulSyncSince(finishedAtMillis: Long): Boolean {
        store.readableDatabase.query(
            LocalStoreBase.TABLE_SYNC_RUNS,
            arrayOf("id"),
            "status=? AND finished_at>=?",
            arrayOf(LocalStoreBase.STATUS_SUCCESS, finishedAtMillis.toString()),
            null,
            null,
            LocalStoreBase.ORDER_ID_DESC,
            "1",
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }
}
