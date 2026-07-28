package dev.bee.kanjianki.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import dev.bee.kanjianki.syncapi.PersistedSourceBinding
import dev.bee.kanjianki.syncapi.SourceBindingRecordCodec
import dev.bee.kanjianki.syncapi.SourceBindingStore

internal interface AndroidSourceBindingStateStore : SourceBindingStore {
    fun legacyAndroidMigrationEligible(): Boolean

    fun saveLegacyMigrationResult(binding: PersistedSourceBinding)
}

internal object SourceBindingMigrationRecord {
    const val KEY_ANDROID_LEGACY_MIGRATION =
        "collection_source_binding.android_legacy_migration"
    const val ELIGIBLE = "eligible"
}

internal class SqliteSourceBindingStore(
    private val store: LocalStoreBase,
) : AndroidSourceBindingStateStore {
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
                saveBinding(this, binding)
            }
        } finally {
            store.settingsStore().invalidate()
        }
    }

    override fun legacyAndroidMigrationEligible(): Boolean =
        store.readableDatabase.query(
            LocalStoreBase.TABLE_SETTINGS,
            arrayOf(LocalStoreBase.COLUMN_VALUE),
            "key = ?",
            arrayOf(SourceBindingMigrationRecord.KEY_ANDROID_LEGACY_MIGRATION),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            cursor.moveToFirst() &&
                cursor.getString(0) == SourceBindingMigrationRecord.ELIGIBLE
        }

    override fun saveLegacyMigrationResult(binding: PersistedSourceBinding) {
        val database = store.writableDatabase
        try {
            database.transaction {
                saveBinding(this, binding)
                delete(
                    LocalStoreBase.TABLE_SETTINGS,
                    "key = ?",
                    arrayOf(SourceBindingMigrationRecord.KEY_ANDROID_LEGACY_MIGRATION),
                )
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

    private fun saveBinding(
        database: SQLiteDatabase,
        binding: PersistedSourceBinding,
    ) {
        for ((key, value) in SourceBindingRecordCodec.encode(binding)) {
            val row = ContentValues().apply {
                put("key", key)
                put(LocalStoreBase.COLUMN_VALUE, value)
                put(LocalStoreBase.COLUMN_UPDATED_AT, binding.lastValidatedAtMillis)
            }
            database.insertWithOnConflict(
                LocalStoreBase.TABLE_SETTINGS,
                null,
                row,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
    }
}
