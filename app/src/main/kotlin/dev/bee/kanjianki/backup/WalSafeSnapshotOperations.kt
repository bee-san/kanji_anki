package dev.bee.kanjianki.backup

import dev.bee.kanjianki.core.DatabaseBackupAvailabilityPolicy
import java.io.File
import java.io.IOException

/** Enforces the fail-closed boundary around SQLite's WAL-safe `VACUUM INTO` copy. */
internal object WalSafeSnapshotOperations {
    fun interface VacuumIntoOperation {
        @Throws(IOException::class)
        fun create(destination: File)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun create(
        destination: File,
        apiLevel: Int,
        vacuumInto: VacuumIntoOperation,
    ) {
        val availability = DatabaseBackupAvailabilityPolicy.forAndroidApi(apiLevel)
        if (!availability.operationsAllowed) {
            throw IOException("WAL-safe snapshots are unavailable on this Android version")
        }
        if (destination.exists()) {
            throw IOException("Snapshot destination already exists")
        }

        try {
            vacuumInto.create(destination)
            if (!destination.isFile || destination.length() <= 0L) {
                throw IOException("Snapshot operation produced no database")
            }
        } catch (error: IOException) {
            deletePartial(destination, error)
            throw error
        } catch (error: RuntimeException) {
            val failure = IOException("WAL-safe snapshot failed", error)
            deletePartial(destination, failure)
            throw failure
        }
    }

    private fun deletePartial(destination: File, failure: IOException) {
        if (destination.exists() && !destination.delete()) {
            failure.addSuppressed(IOException("Could not delete incomplete snapshot"))
        }
    }
}
