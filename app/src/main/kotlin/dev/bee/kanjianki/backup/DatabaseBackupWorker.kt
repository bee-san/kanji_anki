package dev.bee.kanjianki.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.work.ListenableWorker.Result
import androidx.work.Worker
import androidx.work.WorkerParameters
import dev.bee.kanjianki.core.DatabaseBackupPolicy
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class DatabaseBackupWorker(
    context: Context,
    workerParams: WorkerParameters,
) : Worker(context, workerParams) {
    override fun doWork(): Result {
        return doWork(AndroidBackupEnvironment(applicationContext), System.currentTimeMillis())
    }

    fun interface Checkpointer {
        fun checkpoint(dbFile: File)
    }

    fun interface FileCopier {
        @Throws(IOException::class)
        fun copy(src: File, dst: File)
    }

    interface BackupEnvironment {
        fun databasePath(name: String): File

        fun filesDir(): File
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
    }

    fun interface CheckpointDatabaseOpener {
        @Throws(IOException::class)
        fun open(dbFile: File): CheckpointDatabase
    }

    interface CheckpointDatabase {
        @Throws(IOException::class)
        fun checkpoint()

        @Throws(IOException::class)
        fun close()
    }

    private class SQLiteCheckpointDatabase(
        private val db: SQLiteDatabase,
    ) : CheckpointDatabase {
        override fun checkpoint() {
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).close()
        }

        override fun close() {
            db.close()
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
                ::checkpoint,
                ::copyFile,
            )
        }

        @JvmStatic
        fun backupDatabase(
            dbFile: File,
            filesDir: File,
            nowMillis: Long,
            checkpointer: Checkpointer,
            copier: FileCopier,
        ): Result {
            if (!dbFile.exists()) {
                return Result.failure()
            }

            val backupDir = DatabaseBackupPolicy.backupDir(filesDir)
            if (!backupDir.exists() && !backupDir.mkdirs()) {
                return Result.failure()
            }

            try {
                checkpointer.checkpoint(dbFile)
            } catch (error: RuntimeException) {
                warn(DatabaseBackupPolicy.sanitizedDiagnosticLine(
                    "Backup checkpoint failed; copying database without a fresh WAL checkpoint.",
                    error,
                ))
            }

            val dest = DatabaseBackupPolicy.backupFile(filesDir, nowMillis)

            try {
                copier.copy(dbFile, dest)
            } catch (error: IOException) {
                if (!dest.delete()) {
                    warn("Failed to delete incomplete backup: ${dest.name}")
                }
                warn(DatabaseBackupPolicy.sanitizedDiagnosticLine("Database backup failed.", error))
                return Result.failure()
            }

            pruneOldBackups(backupDir)
            return Result.success()
        }

        @JvmStatic
        fun androidEnvironment(context: Context): BackupEnvironment {
            return AndroidBackupEnvironment(context)
        }

        @JvmStatic
        fun checkpoint(dbFile: File) {
            checkpoint(dbFile, ::openCheckpointDatabase)
        }

        @JvmStatic
        fun checkpoint(dbFile: File, opener: CheckpointDatabaseOpener) {
            var db: CheckpointDatabase? = null
            try {
                db = opener.open(dbFile)
                db.checkpoint()
            } catch (error: IOException) {
                warn(DatabaseBackupPolicy.sanitizedDiagnosticLine(
                    "Backup checkpoint failed; copying database without a fresh WAL checkpoint.",
                    error,
                ))
            } catch (error: RuntimeException) {
                warn(DatabaseBackupPolicy.sanitizedDiagnosticLine(
                    "Backup checkpoint failed; copying database without a fresh WAL checkpoint.",
                    error,
                ))
            } finally {
                closeCheckpointDatabase(db)
            }
        }

        private fun openCheckpointDatabase(dbFile: File): CheckpointDatabase {
            val db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE,
            )
            return SQLiteCheckpointDatabase(db)
        }

        private fun closeCheckpointDatabase(db: CheckpointDatabase?) {
            if (db == null) {
                return
            }
            try {
                db.close()
            } catch (error: IOException) {
                warn(DatabaseBackupPolicy.sanitizedDiagnosticLine(
                    "Failed to close database after backup checkpoint.",
                    error,
                ))
            } catch (error: RuntimeException) {
                warn(DatabaseBackupPolicy.sanitizedDiagnosticLine(
                    "Failed to close database after backup checkpoint.",
                    error,
                ))
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun copyFile(src: File, dst: File) {
            FileInputStream(src).use { inStream ->
                FileOutputStream(dst).use { outStream ->
                    inStream.channel.use { inChannel ->
                        outStream.channel.use { outChannel ->
                            val size = inChannel.size()
                            var transferred = 0L
                            while (transferred < size) {
                                transferred += inChannel.transferTo(transferred, size - transferred, outChannel)
                            }
                            outChannel.force(true)
                        }
                    }
                }
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
