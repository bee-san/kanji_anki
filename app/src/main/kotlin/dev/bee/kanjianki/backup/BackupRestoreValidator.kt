package dev.bee.kanjianki.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.os.storage.StorageManager
import dev.bee.kanjianki.core.BackupRestorePolicy
import dev.bee.kanjianki.data.LocalStoreSchema
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream

internal data class ValidatedBackup(
    val databaseFile: File,
    val sourceName: String,
)

internal data class BackupRestoreValidation(
    val policy: BackupRestorePolicy.ValidationResult,
    val validatedBackup: ValidatedBackup? = null,
)

/** Android-side gzip and read-only SQLite validator for user-selected backups. */
internal object BackupRestoreValidator {
    private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    internal const val MAX_DECOMPRESSED_BYTES = 512L * 1024L * 1024L
    internal const val FREE_SPACE_RESERVE_BYTES = 64L * 1024L * 1024L

    internal fun interface AllocatableSpaceProbe {
        fun allocatableBytes(directory: File): Long
    }

    @JvmStatic
    fun validate(
        context: Context,
        restoreDir: File,
        sourceName: String,
        input: () -> InputStream?,
    ): BackupRestoreValidation = validate(
        restoreDir,
        sourceName,
        input,
        MAX_DECOMPRESSED_BYTES,
        FREE_SPACE_RESERVE_BYTES,
        allocatableSpaceProbe(context),
    )

    private fun allocatableSpaceProbe(context: Context): AllocatableSpaceProbe {
        val storageManager = context.getSystemService(StorageManager::class.java)
        return AllocatableSpaceProbe { directory ->
            if (storageManager == null) {
                directory.usableSpace
            } else {
                try {
                    storageManager.getAllocatableBytes(storageManager.getUuidForPath(directory))
                } catch (_: IOException) {
                    directory.usableSpace
                }
            }
        }
    }

    internal fun validate(
        restoreDir: File,
        sourceName: String,
        input: () -> InputStream?,
        maxDecompressedBytes: Long,
        freeSpaceReserveBytes: Long,
        allocatableSpaceProbe: AllocatableSpaceProbe,
    ): BackupRestoreValidation {
        require(maxDecompressedBytes > 0L) { "maxDecompressedBytes must be positive" }
        require(freeSpaceReserveBytes >= 0L) { "freeSpaceReserveBytes must not be negative" }
        if ((!restoreDir.exists() && !restoreDir.mkdirs()) || !restoreDir.isDirectory) {
            return rejected(BackupRestorePolicy.CopyId.TRUNCATED_GZIP)
        }
        BackupRestoreStager.cleanupOrphanValidationFiles(restoreDir)
        val temp = File(restoreDir, "restore-${System.nanoTime()}${BackupRestoreStager.VALIDATING_SUFFIX}")
        val decompressionFailure = try {
            val spaceBudget = SpaceBudget(
                allocatableSpaceProbe.allocatableBytes(restoreDir),
                freeSpaceReserveBytes,
            )
            spaceBudget.requireWrite(1)
            val source = input() ?: throw IOException("No input stream")
            source.use { rawInput ->
                GZIPInputStream(rawInput).use { gzip ->
                    FileOutputStream(temp).use { output ->
                        copyBounded(
                            gzip,
                            output,
                            restoreDir,
                            maxDecompressedBytes,
                            spaceBudget,
                            allocatableSpaceProbe,
                        )
                        try {
                            output.fd.sync()
                        } catch (error: IOException) {
                            rethrowIfStorageExhausted(
                                restoreDir,
                                freeSpaceReserveBytes,
                                allocatableSpaceProbe,
                                bytesNeeded = 1,
                                original = error,
                            )
                        }
                    }
                }
            }
            null
        } catch (_: BackupTooLargeException) {
            BackupRestorePolicy.CopyId.BACKUP_TOO_LARGE
        } catch (_: InsufficientStorageException) {
            BackupRestorePolicy.CopyId.INSUFFICIENT_STORAGE
        } catch (_: IOException) {
            BackupRestorePolicy.CopyId.TRUNCATED_GZIP
        } catch (_: RuntimeException) {
            BackupRestorePolicy.CopyId.TRUNCATED_GZIP
        }
        if (decompressionFailure != null) {
            BackupRestoreStager.deleteBestEffort(temp)
            return rejected(decompressionFailure)
        }

        val magicPresent = try {
            hasSqliteMagic(temp)
        } catch (_: IOException) {
            false
        } catch (_: RuntimeException) {
            false
        }
        if (!magicPresent) {
            BackupRestoreStager.deleteBestEffort(temp)
            return rejected(BackupRestorePolicy.CopyId.BAD_SQLITE_MAGIC)
        }

        val facts = try {
            SQLiteDatabase.openDatabase(temp.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                BackupRestorePolicy.ValidationFacts(
                    gzipReadable = true,
                    sqliteMagicPresent = true,
                    userVersion = scalarInt(db, "PRAGMA user_version"),
                    settingsTablePresent = hasSettingsTable(db),
                    quickCheckOk = quickCheckOk(db),
                )
            }
        } catch (_: SQLiteException) {
            BackupRestoreStager.deleteBestEffort(temp)
            return rejected(BackupRestorePolicy.CopyId.QUICK_CHECK_FAILED)
        } catch (_: RuntimeException) {
            BackupRestoreStager.deleteBestEffort(temp)
            return rejected(BackupRestorePolicy.CopyId.QUICK_CHECK_FAILED)
        }

        val policy = BackupRestorePolicy.validate(facts, LocalStoreSchema.DB_VERSION)
        if (!policy.accepted) {
            BackupRestoreStager.deleteBestEffort(temp)
            return BackupRestoreValidation(policy)
        }
        return BackupRestoreValidation(policy, ValidatedBackup(temp, sourceName))
    }

    private fun hasSqliteMagic(file: File): Boolean {
        if (file.length() < SQLITE_MAGIC.size) return false
        val actual = ByteArray(SQLITE_MAGIC.size)
        file.inputStream().use { input ->
            var offset = 0
            while (offset < actual.size) {
                val read = input.read(actual, offset, actual.size - offset)
                if (read < 0) return false
                offset += read
            }
        }
        return actual.contentEquals(SQLITE_MAGIC)
    }

    private fun copyBounded(
        input: InputStream,
        output: FileOutputStream,
        restoreDir: File,
        maxDecompressedBytes: Long,
        spaceBudget: SpaceBudget,
        allocatableSpaceProbe: AllocatableSpaceProbe,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return
            if (read == 0) continue
            if (totalBytes > maxDecompressedBytes - read) {
                throw BackupTooLargeException()
            }
            spaceBudget.requireWrite(read)
            try {
                output.write(buffer, 0, read)
            } catch (error: IOException) {
                rethrowIfStorageExhausted(
                    restoreDir,
                    spaceBudget.reserveBytes,
                    allocatableSpaceProbe,
                    bytesNeeded = read,
                    original = error,
                )
            }
            spaceBudget.recordWrite(read)
            totalBytes += read
        }
    }

    private class SpaceBudget(
        allocatableBytes: Long,
        val reserveBytes: Long,
    ) {
        private var remainingBytes = allocatableBytes.coerceAtLeast(0L)

        fun requireWrite(bytesToWrite: Int) {
            if (
                remainingBytes < reserveBytes ||
                remainingBytes - reserveBytes < bytesToWrite.toLong()
            ) {
                throw InsufficientStorageException()
            }
        }

        fun recordWrite(bytesWritten: Int) {
            remainingBytes = (remainingBytes - bytesWritten.toLong()).coerceAtLeast(0L)
        }
    }

    private fun rethrowIfStorageExhausted(
        restoreDir: File,
        freeSpaceReserveBytes: Long,
        allocatableSpaceProbe: AllocatableSpaceProbe,
        bytesNeeded: Int,
        original: IOException,
    ): Nothing {
        val allocatableBytes = allocatableSpaceProbe.allocatableBytes(restoreDir).coerceAtLeast(0L)
        if (
            allocatableBytes < freeSpaceReserveBytes ||
            allocatableBytes - freeSpaceReserveBytes < bytesNeeded.toLong()
        ) {
            throw InsufficientStorageException()
        }
        throw original
    }

    private fun scalarInt(db: SQLiteDatabase, sql: String): Int {
        db.rawQuery(sql, null).use { cursor ->
            if (!cursor.moveToFirst()) throw SQLiteException("No scalar row")
            return cursor.getInt(0)
        }
    }

    private fun quickCheckOk(db: SQLiteDatabase): Boolean {
        db.rawQuery("PRAGMA quick_check", null).use { cursor ->
            if (!cursor.moveToFirst()) return false
            do {
                if (!"ok".equals(cursor.getString(0), ignoreCase = true)) return false
            } while (cursor.moveToNext())
            return true
        }
    }

    private fun hasSettingsTable(db: SQLiteDatabase): Boolean {
        val args = arrayOf("table", "settings")
        return db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = ? AND name = ? LIMIT 1",
            args,
        ).use { cursor -> cursor.moveToFirst() }
    }

    private fun rejected(copyId: BackupRestorePolicy.CopyId): BackupRestoreValidation {
        return BackupRestoreValidation(BackupRestorePolicy.rejection(copyId))
    }

    private class BackupTooLargeException : IOException()

    private class InsufficientStorageException : IOException()
}
