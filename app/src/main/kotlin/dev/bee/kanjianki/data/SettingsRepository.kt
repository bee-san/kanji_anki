package dev.bee.kanjianki.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.SettingValuePolicy
import java.util.Locale

internal class SettingsRepository(
    private val store: LocalStoreBase,
) {
    fun getInt(key: String?, fallback: Int): Int {
        settingCursor(key).use { cursor ->
            if (!cursor.moveToFirst()) {
                return fallback
            }
            return SettingValuePolicy.parseInt(
                LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_VALUE),
                fallback,
            )
        }
    }

    fun getLong(key: String?, fallback: Long): Long {
        settingCursor(key).use { cursor ->
            if (!cursor.moveToFirst()) {
                return fallback
            }
            return SettingValuePolicy.parseLong(
                LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_VALUE),
                fallback,
            )
        }
    }

    fun getString(key: String?, fallback: String?): String? {
        settingCursor(key).use { cursor ->
            if (!cursor.moveToFirst()) {
                return fallback
            }
            return LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_VALUE) ?: fallback
        }
    }

    fun getDouble(key: String?, fallback: Double): Double {
        settingCursor(key).use { cursor ->
            if (!cursor.moveToFirst()) {
                return fallback
            }
            return SettingValuePolicy.parseDouble(
                LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_VALUE),
                fallback,
            )
        }
    }

    fun putInt(key: String?, value: Int) {
        put(key, value.toString())
    }

    fun putLong(key: String?, value: Long) {
        put(key, value.toString())
    }

    fun putString(key: String?, value: String?) {
        put(key, value ?: "")
    }

    fun putDouble(key: String?, value: Double) {
        put(key, String.format(Locale.ROOT, "%.4f", value))
    }

    fun put(key: String?, value: String?) {
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

    private fun settingCursor(key: String?): Cursor {
        return store.getReadableDatabase().query(
            LocalStoreBase.TABLE_SETTINGS,
            arrayOf(LocalStoreBase.COLUMN_VALUE),
            LocalStoreBase.WHERE_SETTING_KEY,
            arrayOf(key),
            null,
            null,
            null,
            "1",
        )
    }
}
