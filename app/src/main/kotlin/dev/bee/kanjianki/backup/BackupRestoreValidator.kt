package dev.bee.kanjianki.backup

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
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

    @JvmStatic
    fun validate(
        restoreDir: File,
        sourceName: String,
        input: () -> InputStream?,
    ): BackupRestoreValidation {
        if ((!restoreDir.exists() && !restoreDir.mkdirs()) || !restoreDir.isDirectory) {
            return rejected(BackupRestorePolicy.CopyId.TRUNCATED_GZIP)
        }
        BackupRestoreStager.cleanupOrphanValidationFiles(restoreDir)
        val temp = File(restoreDir, "restore-${System.nanoTime()}${BackupRestoreStager.VALIDATING_SUFFIX}")
        val decompressed = try {
            val source = input() ?: throw IOException("No input stream")
            source.use { rawInput ->
                GZIPInputStream(rawInput).use { gzip ->
                    FileOutputStream(temp).use { output ->
                        gzip.copyTo(output)
                        output.fd.sync()
                    }
                }
            }
            true
        } catch (_: IOException) {
            false
        } catch (_: RuntimeException) {
            false
        }
        if (!decompressed) {
            BackupRestoreStager.deleteBestEffort(temp)
            return rejected(BackupRestorePolicy.CopyId.TRUNCATED_GZIP)
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
}
