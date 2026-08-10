package dev.bee.kanjianki.data.sql

import dev.bee.kanjianki.syncapi.PersistedSourceBinding
import dev.bee.kanjianki.syncapi.SourceBindingRecordCodec
import dev.bee.kanjianki.syncapi.SourceBindingStore

/**
 * Driver-neutral persistence of the versioned opaque collection source binding
 * over the shared `settings` table. This is the platform-independent core of
 * the binding store; the Android legacy-migration and explicit-recovery
 * extensions stay in `:app` and route their provider-state reset through the
 * same shared SQL layer. Runtime Android composition switches to this at Goal
 * 184.
 */
class SqlSourceBindingStore(
    private val database: SqlDatabase,
) : SourceBindingStore {
    override fun load(): PersistedSourceBinding? =
        runBlockingRead {
            val values = LinkedHashMap<String, String>()
            val keys = SourceBindingRecordCodec.keys.toList()
            val placeholders = keys.joinToString(",") { "?" }
            queryList(
                "SELECT key, value FROM settings WHERE key IN ($placeholders)",
                bind = { keys.forEachIndexed { index, key -> bindText(index + 1, key) } },
            ) { row -> row.text(0) to row.text(1) }.forEach { (key, value) -> values[key] = value }
            SourceBindingRecordCodec.decode(values)
        }

    override fun save(binding: PersistedSourceBinding) {
        runBlockingWrite { saveBinding(binding) }
    }

    override fun clear() {
        runBlockingWrite {
            val keys = SourceBindingRecordCodec.keys.toList()
            val placeholders = keys.joinToString(",") { "?" }
            executeBound(
                "DELETE FROM settings WHERE key IN ($placeholders)",
                bind = { keys.forEachIndexed { index, key -> bindText(index + 1, key) } },
            )
        }
    }

    /** Writes the encoded binding record; shared by save and the Android recovery paths. */
    fun SqlTransactionScope.saveBinding(binding: PersistedSourceBinding) {
        for ((key, value) in SourceBindingRecordCodec.encode(binding)) {
            executeBound(
                """
                INSERT INTO settings(key, value, updated_at) VALUES (?, ?, ?)
                ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at
                """.trimIndent(),
            ) {
                bindText(1, key)
                bindText(2, value)
                bindLong(3, binding.lastValidatedAtMillis)
            }
        }
    }

    // The SourceBindingStore contract is synchronous and is called from sync
    // background threads (never the SQL writer thread), so bridging the suspend
    // database boundary with runBlocking here cannot deadlock.
    private fun <T> runBlockingRead(block: SqlReadScope.() -> T): T =
        kotlinx.coroutines.runBlocking { database.readSnapshot(block) }

    private fun <T> runBlockingWrite(block: SqlTransactionScope.() -> T): T =
        kotlinx.coroutines.runBlocking { database.write(block) }
}
