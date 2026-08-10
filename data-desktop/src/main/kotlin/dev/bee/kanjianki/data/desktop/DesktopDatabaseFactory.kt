package dev.bee.kanjianki.data.desktop

import dev.bee.kanjianki.data.sql.DedicatedWriterSqlDatabase
import dev.bee.kanjianki.data.sql.MigrationContext
import dev.bee.kanjianki.data.sql.SchemaManager
import dev.bee.kanjianki.data.sql.SqlDatabase
import dev.bee.kanjianki.data.sql.SqlDatabaseConfiguration
import dev.bee.kanjianki.data.sql.SchemaTransition

/**
 * Opens a desktop profile's `kanji_anki_simple.db` on the bundled SQLite
 * driver, configures WAL/busy-timeout/serialized-writes through the shared
 * [DedicatedWriterSqlDatabase], and runs [SchemaManager] to the canonical
 * version. The caller (a later platform increment) is responsible for the
 * profile lock and directory permission preflight before opening.
 */
object DesktopDatabaseFactory {
    const val DEFAULT_BUSY_TIMEOUT_MILLIS = 5_000L

    data class OpenResult(
        val database: SqlDatabase,
        val transition: SchemaTransition,
    )

    /**
     * Opens (creating if necessary) the database at [databasePath] and migrates
     * it to the canonical schema. Returns the open database and the schema
     * transition that ran. The caller owns closing the database.
     */
    suspend fun open(
        databasePath: String,
        migrationContext: MigrationContext = MigrationContext.system(),
        busyTimeoutMillis: Long = DEFAULT_BUSY_TIMEOUT_MILLIS,
        writerThreadName: String = "kani-desktop-writer",
    ): OpenResult {
        val database = DedicatedWriterSqlDatabase(
            driver = BundledSqlDriver(databasePath),
            configuration = SqlDatabaseConfiguration(
                busyTimeoutMillis = busyTimeoutMillis,
                writerThreadName = writerThreadName,
            ),
        )
        return try {
            val transition = SchemaManager(migrationContext).initialize(database)
            OpenResult(database, transition)
        } catch (failure: Throwable) {
            database.close()
            throw failure
        }
    }
}
