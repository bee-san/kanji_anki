package dev.bee.kanjianki.data

import android.content.Context
import java.io.File

class KaniLocalDataResetCoordinator(
    private val resetPolicy: KaniRoomDatabaseResetPolicy,
    private val databaseStore: KaniDatabaseStore,
) {
    fun prepareForRoomOpen(): KaniLocalDataResetReport {
        val legacyDatabases = resetPolicy.legacyDatabaseNames
            .filter(databaseStore::hasDatabaseFamily)
            .sorted()
        if (legacyDatabases.isEmpty()) {
            return KaniLocalDataResetReport()
        }
        check(resetPolicy.allowLegacyDatabaseReset) {
            "Legacy Kani database(s) ${legacyDatabases.joinToString()} are present, but legacy reset is not allowed."
        }

        legacyDatabases.forEach(databaseStore::deleteDatabaseFamily)
        val remaining = legacyDatabases.filter(databaseStore::hasDatabaseFamily)
        check(remaining.isEmpty()) {
            "Unable to remove legacy Kani database(s) before opening Room: ${remaining.joinToString()}."
        }

        return KaniLocalDataResetReport(
            legacyDatabasesFound = legacyDatabases,
            legacyDatabasesDeleted = legacyDatabases,
        )
    }
}

data class KaniLocalDataResetReport(
    val legacyDatabasesFound: List<String> = emptyList(),
    val legacyDatabasesDeleted: List<String> = emptyList(),
) {
    val resetRequired: Boolean
        get() = legacyDatabasesFound.isNotEmpty()
}

interface KaniDatabaseStore {
    fun hasDatabaseFamily(databaseName: String): Boolean

    fun deleteDatabaseFamily(databaseName: String): Boolean
}

class AndroidKaniDatabaseStore(
    context: Context,
) : KaniDatabaseStore {
    private val appContext = context.applicationContext

    override fun hasDatabaseFamily(databaseName: String): Boolean {
        val database = appContext.getDatabasePath(databaseName)
        return database.exists() || sidecars(database).any(File::exists)
    }

    override fun deleteDatabaseFamily(databaseName: String): Boolean {
        val database = appContext.getDatabasePath(databaseName)
        val mainDeleted = appContext.deleteDatabase(databaseName)
        val sidecarDeleted = sidecars(database).fold(false) { deletedAny, sidecar ->
            if (sidecar.exists()) sidecar.delete() || deletedAny else deletedAny
        }
        return mainDeleted || sidecarDeleted
    }

    private fun sidecars(database: File): List<File> = listOf(
        File(database.path + "-wal"),
        File(database.path + "-shm"),
        File(database.path + "-journal"),
    )
}
