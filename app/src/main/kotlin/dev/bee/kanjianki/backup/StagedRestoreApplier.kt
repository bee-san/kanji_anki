package dev.bee.kanjianki.backup

import android.content.Context
import android.util.Log
import dev.bee.kanjianki.core.DatabaseBackupPolicy
import dev.bee.kanjianki.data.LocalStore
import java.io.File
import java.io.IOException

/** Applies a validated restore before any receiver, worker, or activity opens LocalStore. */
internal object StagedRestoreApplier {
    enum class Result {
        NO_OP,
        APPLIED,
        RETRY_NEEDED,
    }

    enum class Step {
        MARKER_READY,
        SAFETY_BACKUP_CREATED,
        DATABASE_REPLACED,
        SIDECARS_DELETED,
        MARKER_DELETED,
    }

    fun interface StepHook {
        fun after(step: Step)
    }

    @JvmStatic
    fun apply(context: Context): Result {
        val appContext = context.applicationContext
        return try {
            applyOrThrow(
                filesDir = appContext.filesDir,
                databaseFile = appContext.getDatabasePath(DatabaseBackupPolicy.DB_NAME),
                nowMillis = System.currentTimeMillis(),
                snapshotter = DatabaseBackupWorker.Snapshotter { dbFile, destination ->
                    LocalStore(appContext).use { store -> store.snapshotInto(dbFile, destination) }
                },
            )
        } catch (error: IOException) {
            Log.e("StagedRestore", "Restore apply will retry on next process start", error)
            Result.RETRY_NEEDED
        } catch (error: RuntimeException) {
            Log.e("StagedRestore", "Restore apply will retry on next process start", error)
            Result.RETRY_NEEDED
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    internal fun applyOrThrow(
        filesDir: File,
        databaseFile: File,
        nowMillis: Long,
        snapshotter: DatabaseBackupWorker.Snapshotter,
        stepHook: StepHook = StepHook {},
    ): Result {
        val restoreDir = BackupRestoreStager.restoreDir(filesDir)
        // Fresh installs never create this directory, so the normal startup path is one
        // existence check and no directory listing or database work.
        if (!restoreDir.exists()) return Result.NO_OP
        val staged = BackupRestoreStager.stagedFile(filesDir)
        val marker = BackupRestoreStager.markerFile(filesDir)
        BackupRestoreStager.cleanupOrphanValidationFiles(restoreDir)
        if (!staged.exists() && !marker.exists()) {
            deleteEmptyRestoreDir(restoreDir)
            return Result.NO_OP
        }

        // A marker without a staged file means the atomic database move completed before a
        // crash. Finish only the idempotent sidecar/marker cleanup.
        if (!staged.exists()) {
            deleteSidecars(databaseFile)
            stepHook.after(Step.SIDECARS_DELETED)
            deleteRequired(marker)
            stepHook.after(Step.MARKER_DELETED)
            deleteEmptyRestoreDir(restoreDir)
            return Result.APPLIED
        }

        BackupRestoreStager.ensureRecoveryMarker(marker)
        stepHook.after(Step.MARKER_READY)

        if (databaseFile.exists()) {
            createSafetyBackup(databaseFile, filesDir, nowMillis, snapshotter)
        }
        stepHook.after(Step.SAFETY_BACKUP_CREATED)

        BackupRestoreStager.moveAtomically(staged, databaseFile)
        stepHook.after(Step.DATABASE_REPLACED)

        deleteSidecars(databaseFile)
        stepHook.after(Step.SIDECARS_DELETED)

        deleteRequired(marker)
        stepHook.after(Step.MARKER_DELETED)
        deleteEmptyRestoreDir(restoreDir)
        return Result.APPLIED
    }

    @Throws(IOException::class)
    private fun createSafetyBackup(
        databaseFile: File,
        filesDir: File,
        nowMillis: Long,
        snapshotter: DatabaseBackupWorker.Snapshotter,
    ) {
        val backupDir = DatabaseBackupPolicy.backupDir(filesDir)
        if ((!backupDir.exists() && !backupDir.mkdirs()) || !backupDir.isDirectory) {
            throw IOException("Unable to create backup directory")
        }
        val destination = DatabaseBackupPolicy.backupFile(filesDir, nowMillis)
        val raw = File(backupDir, destination.name + ".pre-restore.tmp")
        val partial = File(backupDir, destination.name + ".partial")
        BackupRestoreStager.deleteBestEffort(raw)
        BackupRestoreStager.deleteBestEffort(partial)
        try {
            snapshotter.snapshot(databaseFile, raw)
            DatabaseBackupWorker.gzipFile(raw, partial)
            DatabaseBackupWorker.publishAtomically(partial, destination)
        } catch (error: IOException) {
            BackupRestoreStager.deleteBestEffort(partial)
            throw error
        } catch (error: RuntimeException) {
            BackupRestoreStager.deleteBestEffort(partial)
            throw error
        } finally {
            BackupRestoreStager.deleteBestEffort(raw)
        }
        DatabaseBackupWorker.pruneOldBackups(backupDir)
    }

    @Throws(IOException::class)
    private fun deleteSidecars(databaseFile: File) {
        deleteRequired(File(databaseFile.absolutePath + "-wal"))
        deleteRequired(File(databaseFile.absolutePath + "-shm"))
    }

    @Throws(IOException::class)
    private fun deleteRequired(file: File) {
        if (file.exists() && !file.delete()) throw IOException("Unable to delete ${file.name}")
    }

    private fun deleteEmptyRestoreDir(restoreDir: File) {
        if (restoreDir.list()?.isEmpty() == true) BackupRestoreStager.deleteBestEffort(restoreDir)
    }
}
