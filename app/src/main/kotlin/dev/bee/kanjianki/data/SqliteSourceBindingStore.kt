package dev.bee.kanjianki.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import dev.bee.kanjianki.syncapi.PersistedSourceBinding
import dev.bee.kanjianki.syncapi.SourceBindingRecordCodec
import dev.bee.kanjianki.syncapi.SourceBindingStore

internal class SqliteSourceBindingStore(
    private val store: LocalStoreBase,
) : SourceBindingStore {
    override fun load(): PersistedSourceBinding? {
        val keys = SourceBindingRecordCodec.keys.toList()
        val placeholders = keys.joinToString(",") { "?" }
        val values = LinkedHashMap<String, String>()
        store.readableDatabase.query(
            LocalStoreBase.TABLE_SETTINGS,
            arrayOf("key", LocalStoreBase.COLUMN_VALUE),
            "key IN ($placeholders)",
            keys.toTypedArray(),
            null,
            null,
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                values[cursor.getString(0)] = cursor.getString(1)
            }
        }
        return SourceBindingRecordCodec.decode(values)
    }

    override fun save(binding: PersistedSourceBinding) {
        val database = store.writableDatabase
        try {
            database.transaction {
                for ((key, value) in SourceBindingRecordCodec.encode(binding)) {
                    val row = ContentValues().apply {
                        put("key", key)
                        put(LocalStoreBase.COLUMN_VALUE, value)
                        put(LocalStoreBase.COLUMN_UPDATED_AT, binding.lastValidatedAtMillis)
                    }
                    insertWithOnConflict(
                        LocalStoreBase.TABLE_SETTINGS,
                        null,
                        row,
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }
            }
        } finally {
            store.settingsStore().invalidate()
        }
    }

    override fun clear() {
        val database = store.writableDatabase
        val keys = SourceBindingRecordCodec.keys.toList()
        val placeholders = keys.joinToString(",") { "?" }
        try {
            database.transaction {
                delete(
                    LocalStoreBase.TABLE_SETTINGS,
                    "key IN ($placeholders)",
                    keys.toTypedArray(),
                )
            }
        } finally {
            store.settingsStore().invalidate()
        }
    }
}
