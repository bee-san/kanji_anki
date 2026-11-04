package dev.bee.kanjianki.data.desktop

import dev.bee.kanjianki.backup.core.PortableBackupMetadata
import dev.bee.kanjianki.data.sql.SqlDatabase

/**
 * Stamps and reads the shared [PortableBackupMetadata] rows in a desktop
 * profile's `settings` table. A desktop export stamps `origin = desktop` and the
 * current format/schema version before snapshotting, so the snapshot carries its
 * provenance; a restore reads those rows back to drive
 * [dev.bee.kanjianki.backup.core.CrossPlatformRestorePlanner]. Backups written
 * before this metadata existed simply lack the rows and read back as
 * [PortableBackupMetadata.Origin.UNKNOWN].
 */
object DesktopPortableBackupStamper {
    /** Stamps `origin = desktop` and the current format/[schemaVersion] rows. */
    suspend fun stamp(database: SqlDatabase, schemaVersion: Int) {
        val rows = PortableBackupMetadata.rowsFor(
            PortableBackupMetadata.Origin.DESKTOP,
            schemaVersion,
        )
        database.write {
            prepare("INSERT OR REPLACE INTO settings(key, value, updated_at) VALUES (?, ?, 0)").use { statement ->
                for ((key, value) in rows) {
                    statement.reset()
                    statement.clearBindings()
                    statement.bindText(1, key)
                    statement.bindText(2, value)
                    statement.execute()
                }
            }
        }
    }

    /** Reads the portable metadata from an open (restored) database. */
    suspend fun read(database: SqlDatabase): PortableBackupMetadata.Metadata {
        val rows = database.readSnapshot {
            val collected = LinkedHashMap<String, String>()
            prepare("SELECT key, value FROM settings WHERE key IN (?, ?, ?)").use { statement ->
                statement.bindText(1, PortableBackupMetadata.ORIGIN_KEY)
                statement.bindText(2, PortableBackupMetadata.FORMAT_VERSION_KEY)
                statement.bindText(3, PortableBackupMetadata.SCHEMA_VERSION_KEY)
                statement.query().use { result ->
                    while (result.next()) {
                        collected[result.row.text(0)] = result.row.text(1)
                    }
                }
            }
            collected
        }
        return PortableBackupMetadata.decode(rows)
    }
}
