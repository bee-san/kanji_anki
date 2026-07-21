package dev.bee.kanjianki.backup

import dev.bee.kanjianki.AppLocalStoreFactory

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.ListenableWorker.Result
import androidx.work.Worker
import androidx.work.WorkerParameters
import dev.bee.kanjianki.core.DatabaseBackupPolicy
import dev.bee.kanjianki.core.DatabaseBackupAvailabilityPolicy
import dev.bee.kanjianki.data.LocalStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.GZIPOutputStream

class DatabaseBackupWorker(
    context: Context,
    workerParams: WorkerParameters,
) : Worker(context, workerParams) {
    override fun doWork(): Result {
        return doWork(
            AndroidBackupEnvironment(applicationContext),
            System.currentTimeMillis(),
            Build.VERSION.SDK_INT,
        )
    }

    /**
     * Produces a WAL-safe backup at [dest] from [dbFile]. Implementations copy committed
     * WAL content (e.g. `VACUUM INTO`) rather than doing a plain file copy that would
     * ignore the `-wal`/`-shm` sidecars and tear or stale the backup.
     */
    fun interface Snapshotter {
        @Throws(IOException::class)
        fun snapshot(dbFile: File, dest: File)
    }

    internal fun interface BackupPublisher {
        @Throws(IOException::class)
        fun publish(partial: File, destination: File)
    }

    interface BackupEnvironment {
        fun databasePath(name: String): File

        fun filesDir(): File

        @Throws(IOException::class)
        fun snapshot(dbFile: File, dest: File)
    }

    private class AndroidBackupEnvironment(
        private val context: Context,
    ) : BackupEnvironment {
        override fun databasePath(name: String): File {
            return context.getDatabasePath(name)
        }

        override fun filesDir(): File {
            return context.filesDir
        }

        override fun snapshot(dbFile: File, dest: File) {
            AppLocalStoreFactory.create(context).use { store ->
                store.snapshotInto(dest)
            }
        }
    }

    companion object {
        private const val TAG = "DatabaseBackupWorker"
        private const val BACKUP_TIMESTAMP_PATTERN = "yyyyMMdd_HHmmss"
        private val BACKUP_SCRATCH_NAME =
            Regex("^kanji_anki_simple_(\\d{8}_\\d{6})\\.db\\.gz\\.(?:tmp|partial|pre-restore\\.tmp)$")

        @JvmStatic
        fun doWork(environment: BackupEnvironment, nowMillis: Long): Result {
            return doWork(
                environment,
                nowMillis,
                DatabaseBackupAvailabilityPolicy.MIN_SAFE_ANDROID_API,
            )
        }

        internal fun doWork(
            environment: BackupEnvironment,
            nowMillis: Long,
            apiLevel: Int,
        ): Result {
            if (!DatabaseBackupAvailabilityPolicy.forAndroidApi(apiLevel).operationsAllowed) {
                // This is a permanent platform capability decision. A stale periodic
                // request should finish successfully without touching existing archives.
                return Result.success()
            }
            return backupDatabase(
                environment.databasePath(DatabaseBackupPolicy.DB_NAME),
                environment.filesDir(),
                nowMillis,
                environment::snapshot,
            )
        }

        @JvmStatic
        fun backupDatabase(
            dbFile: File,
            filesDir: File,
            nowMillis: Long,
            snapshotter: Snapshotter,
        ): Result = backupDatabase(dbFile, filesDir, nowMillis, snapshotter, ::publishAtomically)

        internal fun backupDatabase(
            dbFile: File,
            filesDir: File,
            nowMillis: Long,
            snapshotter: Snapshotter,
            publisher: BackupPublisher,
        ): Result {
            if (!dbFile.exists()) {
                return Result.failure()
            }

            val backupDir = DatabaseBackupPolicy.backupDir(filesDir)
            if ((!backupDir.exists() && !backupDir.mkdirs()) || !backupDir.isDirectory) {
                return Result.failure()
            }
            if (!deleteStaleScratchFiles(backupDir)) {
                return Result.failure()
            }

            val dest = DatabaseBackupPolicy.backupFile(filesDir, nowMillis)
            // Snapshot to a raw uncompressed temp copy, then gzip it into place. SQLite
            // Databases compress ~4-10x, and retention keeps a small tiered set of
            // seven recent daily plus up to four older weekly archives.
            val temp = File(backupDir, dest.name + ".tmp")
            val partial = File(backupDir, dest.name + ".partial")
            if (!deleteIncomplete(temp) || !deleteIncomplete(partial)) {
                return Result.failure()
            }

            try {
                snapshotter.snapshot(dbFile, temp)
                requireSnapshot(temp)
                gzipFile(temp, partial)
                publisher.publish(partial, dest)
            } catch (error: IOException) {
                deleteIncomplete(partial)
                warn(DatabaseBackupPolicy.sanitizedDiagnosticLine("Database backup failed.", error))
                return Result.failure()
            } catch (error: RuntimeException) {
                deleteIncomplete(partial)
                warn(DatabaseBackupPolicy.sanitizedDiagnosticLine("Database backup failed.", error))
                return Result.failure()
            } finally {
                deleteIncomplete(temp)
            }

            pruneOldBackups(backupDir)
            return Result.success()
        }

        @JvmStatic
        @Throws(IOException::class)
        fun gzipFile(src: File, dest: File) {
            FileInputStream(src).use { input ->
                FileOutputStream(dest).use { fileOut ->
                    GZIPOutputStream(fileOut).use { gzip ->
                        input.copyTo(gzip)
                        gzip.finish()
                        // Flush compressed bytes to disk before GZIPOutputStream.close()
                        // closes the underlying stream.
                        fileOut.fd.sync()
                    }
                }
            }
        }

        @Throws(IOException::class)
        internal fun publishAtomically(partial: File, destination: File) {
            check(partial.parentFile == destination.parentFile) {
                "Backup partial and destination must share a directory"
            }
            if (destination.exists()) {
                throw IOException("Backup destination already exists")
            }
            Files.move(
                partial.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
            )
            val destinationDirectory = destination.parentFile
                ?: throw IOException("Backup destination has no parent directory")
            SystemDirectorySynchronizer.sync(destinationDirectory)
        }

        @JvmStatic
        fun androidEnvironment(context: Context): BackupEnvironment {
            return AndroidBackupEnvironment(context)
        }

        private fun deleteIncomplete(dest: File): Boolean {
            if (dest.exists() && !dest.delete()) {
                warn("Failed to delete incomplete backup: ${dest.name}")
                return false
            }
            return true
        }

        internal fun deleteStaleScratchFiles(backupDir: File): Boolean {
            val entries = backupDir.listFiles()
            if (entries == null) {
                warn("Failed to inspect incomplete backups")
                return false
            }
            for (entry in entries) {
                if (isRecognizedBackupScratch(entry.name) && !deleteIncomplete(entry)) {
                    return false
                }
            }
            return true
        }

        private fun isRecognizedBackupScratch(name: String): Boolean {
            val match = BACKUP_SCRATCH_NAME.matchEntire(name) ?: return false
            val parser = SimpleDateFormat(BACKUP_TIMESTAMP_PATTERN, Locale.US).apply {
                isLenient = false
            }
            return try {
                parser.parse(match.groupValues[1])
                true
            } catch (_: ParseException) {
                false
            }
        }

        @Throws(IOException::class)
        private fun requireSnapshot(snapshot: File) {
            if (!snapshot.isFile || snapshot.length() <= 0L) {
                throw IOException("Snapshot operation produced no database")
            }
        }

        @JvmStatic
        fun pruneOldBackups(backupDir: File) {
            for (oldBackup in DatabaseBackupPolicy.oldBackupsToPrune(backupDir)) {
                if (!oldBackup.delete()) {
                    warn("Failed to prune old backup: ${oldBackup.name}")
                }
            }
        }

        private fun warn(message: String) {
            try {
                Log.w(TAG, message)
            } catch (_: RuntimeException) {
                // Android Log is unavailable in local JVM tests.
            }
        }
    }
}
