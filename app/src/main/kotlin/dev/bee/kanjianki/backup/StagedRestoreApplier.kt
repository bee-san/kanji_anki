package dev.bee.kanjianki.backup

import dev.bee.kanjianki.AppLocalStoreFactory

import android.content.Context
import android.os.Build
import android.util.Log
import dev.bee.kanjianki.core.DatabaseBackupAvailabilityPolicy
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
        UNSUPPORTED_PLATFORM,
        BLOCK_STARTUP,
    }

    enum class Step {
        SAFETY_BACKUP_CREATED,
        MARKER_READY,
        DATABASE_REPLACED,
        SIDECARS_DELETED,
        MARKER_DELETED,
    }

    fun interface StepHook {
        fun after(step: Step)
    }

    fun interface RequiredFileDeleter {
        @Throws(IOException::class)
        fun delete(file: File)
    }

    internal data class Operations(
        val apiLevel: Int = DatabaseBackupAvailabilityPolicy.MIN_SAFE_ANDROID_API,
        val atomicReplacer: BackupRestoreStager.AtomicFileReplacer =
            BackupRestoreStager.AtomicFileReplacer { source, destination ->
                BackupRestoreStager.moveAtomically(source, destination)
            },
        val backupPublisher: DatabaseBackupWorker.BackupPublisher =
            DatabaseBackupWorker.BackupPublisher(DatabaseBackupWorker::publishAtomically),
        val requiredFileDeleter: RequiredFileDeleter = RequiredFileDeleter(::deleteRequiredFromDisk),
        val directorySynchronizer: DirectorySynchronizer = SystemDirectorySynchronizer,
    )

    @JvmStatic
    fun apply(context: Context): Result {
        val appContext = context.applicationContext
        return try {
            applyOrThrow(
                filesDir = appContext.filesDir,
                databaseFile = appContext.getDatabasePath(DatabaseBackupPolicy.DB_NAME),
                nowMillis = System.currentTimeMillis(),
                snapshotter = DatabaseBackupWorker.Snapshotter { _, destination ->
                    AppLocalStoreFactory.create(appContext).use { store -> store.snapshotInto(destination) }
                },
                operations = Operations(apiLevel = Build.VERSION.SDK_INT),
            )
        } catch (error: IOException) {
            Log.e("StagedRestore", "Restore apply will retry on next process start", error)
            retryResult(appContext.filesDir)
        } catch (error: RuntimeException) {
            Log.e("StagedRestore", "Restore apply will retry on next process start", error)
            retryResult(appContext.filesDir)
        }
    }

    @JvmStatic
    internal fun retryResult(filesDir: File): Result {
        val marker = BackupRestoreStager.markerFile(filesDir)
        return if (marker.exists()) {
            // Any marker-bearing failure is conservative startup-blocking state. New
            // markers commit the restore attempt, while legacy/invalid markers do not
            // durably prove whether an older replacement started.
            Result.BLOCK_STARTUP
        } else {
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
        operations: Operations = Operations(),
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
            return finishMarkerOnlyRestore(
                restoreDir,
                databaseFile,
                marker,
                operations,
                stepHook,
            )
        }

        val markerState = BackupRestoreStager.markerState(marker)
        if (!DatabaseBackupAvailabilityPolicy.forAndroidApi(operations.apiLevel).operationsAllowed) {
            // A staged file is still pre-replacement. Preserve it and the live database;
            // any marker-bearing state is ambiguous across old/new protocols and must
            // not permit SQLite to open beside potentially stale sidecars.
            return if (markerState == BackupRestoreStager.MarkerState.MISSING) {
                Result.UNSUPPORTED_PLATFORM
            } else {
                Result.BLOCK_STARTUP
            }
        }

        if (markerState == BackupRestoreStager.MarkerState.INVALID) {
            throw IOException("Restore marker is invalid")
        }
        if (markerState != BackupRestoreStager.MarkerState.SAFETY_READY) {
            // Re-establish durability if staging returned after its rename but the
            // directory sync reported an error. No database is opened before this holds.
            operations.directorySynchronizer.sync(restoreDir)
            if (databaseFile.exists()) {
                createSafetyBackup(
                    databaseFile,
                    filesDir,
                    nowMillis,
                    snapshotter,
                    operations.backupPublisher,
                )
            }
        }
        stepHook.after(Step.SAFETY_BACKUP_CREATED)

        // SAFETY_READY is the durable commit intent. A ready marker plus a staged file
        // retries the replacement without opening SQLite beside possibly stale sidecars.
        BackupRestoreStager.ensureRecoveryMarker(
            marker,
            operations.atomicReplacer,
            operations.directorySynchronizer,
        )
        stepHook.after(Step.MARKER_READY)

        operations.atomicReplacer.replace(staged, databaseFile)
        BackupRestoreStager.syncMoveParents(staged, databaseFile, operations.directorySynchronizer)
        stepHook.after(Step.DATABASE_REPLACED)

        deleteSidecars(databaseFile, operations.requiredFileDeleter)
        operations.directorySynchronizer.sync(requireParent(databaseFile))
        stepHook.after(Step.SIDECARS_DELETED)

        operations.requiredFileDeleter.delete(marker)
        operations.directorySynchronizer.sync(restoreDir)
        stepHook.after(Step.MARKER_DELETED)
        deleteEmptyRestoreDir(restoreDir)
        return Result.APPLIED
    }

    @Throws(IOException::class)
    private fun finishMarkerOnlyRestore(
        restoreDir: File,
        databaseFile: File,
        marker: File,
        operations: Operations,
        stepHook: StepHook,
    ): Result {
        if (BackupRestoreStager.markerState(marker) != BackupRestoreStager.MarkerState.SAFETY_READY) {
            // Legacy markers were written before replacement and cannot prove which DB won.
            throw IOException("Restore marker is invalid")
        }
        if (!databaseFile.isFile) {
            throw IOException("Restored database is missing after replacement")
        }
        deleteSidecars(databaseFile, operations.requiredFileDeleter)
        operations.directorySynchronizer.sync(requireParent(databaseFile))
        stepHook.after(Step.SIDECARS_DELETED)
        operations.requiredFileDeleter.delete(marker)
        operations.directorySynchronizer.sync(restoreDir)
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
        publisher: DatabaseBackupWorker.BackupPublisher,
    ) {
        val backupDir = DatabaseBackupPolicy.backupDir(filesDir)
        if ((!backupDir.exists() && !backupDir.mkdirs()) || !backupDir.isDirectory) {
            throw IOException("Unable to create backup directory")
        }
        if (!DatabaseBackupWorker.deleteStaleScratchFiles(backupDir)) {
            throw IOException("Unable to clear abandoned restore scratch files")
        }
        val destination = availableSafetyBackupFile(filesDir, nowMillis)
        val raw = File(backupDir, destination.name + ".pre-restore.tmp")
        val partial = File(backupDir, destination.name + ".partial")
        deleteScratchOrThrow(raw)
        deleteScratchOrThrow(partial)
        try {
            snapshotter.snapshot(databaseFile, raw)
            if (!raw.isFile || raw.length() <= 0L) {
                throw IOException("Safety snapshot operation produced no database")
            }
            DatabaseBackupWorker.gzipFile(raw, partial)
            publisher.publish(partial, destination)
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
    private fun availableSafetyBackupFile(filesDir: File, nowMillis: Long): File {
        for (offsetSeconds in 0L until 60L) {
            val candidate = DatabaseBackupPolicy.backupFile(filesDir, nowMillis + offsetSeconds * 1_000L)
            if (!candidate.exists()) return candidate
        }
        throw IOException("Unable to allocate a unique safety backup name")
    }

    @Throws(IOException::class)
    private fun deleteSidecars(databaseFile: File, deleter: RequiredFileDeleter) {
        deleter.delete(File(databaseFile.absolutePath + "-wal"))
        deleter.delete(File(databaseFile.absolutePath + "-shm"))
    }

    @Throws(IOException::class)
    private fun deleteScratchOrThrow(file: File) {
        if (file.exists() && !file.delete()) throw IOException("Unable to clear restore scratch file")
    }

    @Throws(IOException::class)
    private fun deleteRequiredFromDisk(file: File) {
        if (file.exists() && !file.delete()) throw IOException("Unable to delete ${file.name}")
    }

    private fun requireParent(file: File): File {
        return file.parentFile ?: throw IOException("Database file has no parent directory")
    }

    private fun deleteEmptyRestoreDir(restoreDir: File) {
        if (restoreDir.list()?.isEmpty() == true) BackupRestoreStager.deleteBestEffort(restoreDir)
    }
}
