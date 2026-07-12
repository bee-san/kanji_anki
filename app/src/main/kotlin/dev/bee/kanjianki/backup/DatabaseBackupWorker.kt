package dev.bee.kanjianki.backup

import android.content.Context
import android.util.Log
import androidx.work.ListenableWorker.Result
import androidx.work.Worker
import androidx.work.WorkerParameters
import dev.bee.kanjianki.core.DatabaseBackupPolicy
import dev.bee.kanjianki.data.LocalStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPOutputStream

class DatabaseBackupWorker(
    context: Context,
    workerParams: WorkerParameters,
) : Worker(context, workerParams) {
    override fun doWork(): Result {
        return doWork(AndroidBackupEnvironment(applicationContext), System.currentTimeMillis())
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
            LocalStore(context).use { store ->
                store.snapshotInto(dbFile, dest)
            }
        }
    }

    companion object {
        private const val TAG = "DatabaseBackupWorker"

        @JvmStatic
        fun doWork(environment: BackupEnvironment, nowMillis: Long): Result {
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
            if (!backupDir.exists() && !backupDir.mkdirs()) {
                return Result.failure()
            }

            val dest = DatabaseBackupPolicy.backupFile(filesDir, nowMillis)
            // Snapshot to a raw uncompressed temp copy, then gzip it into place. SQLite
            // databases compress ~4-10x, so 31 daily copies of a growing DB become a
            // small tiered set of compressed archives.
            val temp = File(backupDir, dest.name + ".tmp")
            val partial = File(backupDir, dest.name + ".partial")
            deleteIncomplete(temp)
            deleteIncomplete(partial)

            try {
                snapshotter.snapshot(dbFile, temp)
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
                val fileOut = FileOutputStream(dest)
                GZIPOutputStream(fileOut).use { gzip ->
                    input.copyTo(gzip)
                    gzip.finish()
                    // Flush compressed bytes to disk before GZIPOutputStream.close()
                    // closes the underlying stream.
                    fileOut.fd.sync()
                }
            }
        }

        @Throws(IOException::class)
        internal fun publishAtomically(partial: File, destination: File) {
            check(partial.parentFile == destination.parentFile) {
                "Backup partial and destination must share a directory"
            }
            Files.move(
                partial.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }

        @JvmStatic
        fun androidEnvironment(context: Context): BackupEnvironment {
            return AndroidBackupEnvironment(context)
        }

        private fun deleteIncomplete(dest: File) {
            if (dest.exists() && !dest.delete()) {
                warn("Failed to delete incomplete backup: ${dest.name}")
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
