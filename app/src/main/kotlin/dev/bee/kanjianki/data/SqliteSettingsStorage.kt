package dev.bee.kanjianki.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

internal class SqliteSettingsStorage(
    private val store: LocalStoreBase,
) : SettingsStorage {
    override fun get(key: String?): String? {
        store.getReadableDatabase().query(
            LocalStoreBase.TABLE_SETTINGS,
            arrayOf(LocalStoreBase.COLUMN_VALUE),
            LocalStoreBase.WHERE_SETTING_KEY,
            arrayOf(key),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            return if (cursor.moveToFirst()) {
                LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_VALUE)
            } else {
                null
            }
        }
    }

    override fun put(key: String?, value: String?) {
        val values = ContentValues()
        values.put("key", key)
        values.put(LocalStoreBase.COLUMN_VALUE, value)
        values.put(LocalStoreBase.COLUMN_UPDATED_AT, System.currentTimeMillis())
        store.getWritableDatabase().insertWithOnConflict(
            LocalStoreBase.TABLE_SETTINGS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }
}
