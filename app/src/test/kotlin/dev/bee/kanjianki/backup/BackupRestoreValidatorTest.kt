package dev.bee.kanjianki.backup

import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.BackupRestorePolicy
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupRestoreValidatorTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun acceptsCurrentKaniDatabaseAndKeepsValidatedPrivateTemp() {
        val gzip = fixtureGzip(userVersion = 29, createSettings = true)
        val restoreDir = temp.newFolder("restore")
        val orphan = File(restoreDir, "old${BackupRestoreStager.VALIDATING_SUFFIX}").apply {
            writeText("orphan")
        }

        val result = BackupRestoreValidator.validate(restoreDir, "fixture.db.gz") {
            ByteArrayInputStream(gzip)
        }

        assertTrue(result.policy.accepted)
        assertEquals(BackupRestorePolicy.CopyId.READY, result.policy.copyId)
        assertNotNull(result.validatedBackup)
        assertTrue(result.validatedBackup!!.databaseFile.isFile)
        assertEquals("fixture.db.gz", result.validatedBackup!!.sourceName)
        assertFalse(orphan.exists())
    }

    @Test
    fun rejectsTruncatedGzipBadMagicNewerSchemaAndMissingSentinel() {
        val restoreDir = temp.newFolder("restore-reject")

        assertEquals(
            BackupRestorePolicy.CopyId.TRUNCATED_GZIP,
            BackupRestoreValidator.validate(restoreDir, "truncated") {
                ByteArrayInputStream(byteArrayOf(0x1f, 0x8b.toByte(), 0x08))
            }.policy.copyId,
        )
        assertEquals(
            BackupRestorePolicy.CopyId.BAD_SQLITE_MAGIC,
            BackupRestoreValidator.validate(restoreDir, "wrong") {
                ByteArrayInputStream(gzip("not sqlite".toByteArray()))
            }.policy.copyId,
        )
        assertEquals(
            BackupRestorePolicy.CopyId.NEWER_DATABASE_VERSION,
            BackupRestoreValidator.validate(restoreDir, "newer") {
                ByteArrayInputStream(fixtureGzip(userVersion = 30, createSettings = true))
            }.policy.copyId,
        )
        assertEquals(
            BackupRestorePolicy.CopyId.MISSING_SETTINGS_TABLE,
            BackupRestoreValidator.validate(restoreDir, "other") {
                ByteArrayInputStream(fixtureGzip(userVersion = 29, createSettings = false))
            }.policy.copyId,
        )
        assertTrue(restoreDir.listFiles().isNullOrEmpty())
    }

    private fun fixtureGzip(userVersion: Int, createSettings: Boolean): ByteArray {
        val dbFile = File(temp.root, "fixture-$userVersion-$createSettings-${System.nanoTime()}.db")
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            db.execSQL("CREATE TABLE probe(id INTEGER PRIMARY KEY)")
            if (createSettings) {
                db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY, value TEXT NOT NULL, updated_at INTEGER NOT NULL)")
            }
            db.execSQL("PRAGMA user_version = $userVersion")
        }
        val result = gzip(dbFile.readBytes())
        assertTrue(dbFile.delete())
        return result
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(bytes) }
        return output.toByteArray()
    }
}
