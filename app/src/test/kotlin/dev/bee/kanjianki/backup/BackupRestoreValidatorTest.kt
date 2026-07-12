package dev.bee.kanjianki.backup

import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.BackupRestorePolicy
import dev.bee.kanjianki.data.LocalStoreSchema
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
        val gzip = fixtureGzip(userVersion = LocalStoreSchema.DB_VERSION, createSettings = true)
        val restoreDir = temp.newFolder("restore")
        var spaceProbeCalls = 0
        val orphan = File(restoreDir, "old${BackupRestoreStager.VALIDATING_SUFFIX}").apply {
            writeText("orphan")
        }

        val result = validate(
            restoreDir,
            "fixture.db.gz",
            BackupRestoreValidator.AllocatableSpaceProbe {
                spaceProbeCalls += 1
                Long.MAX_VALUE
            },
        ) { ByteArrayInputStream(gzip) }

        assertTrue(result.policy.accepted)
        assertEquals(BackupRestorePolicy.CopyId.READY, result.policy.copyId)
        assertNotNull(result.validatedBackup)
        assertTrue(result.validatedBackup!!.databaseFile.isFile)
        assertEquals("fixture.db.gz", result.validatedBackup!!.sourceName)
        assertFalse(orphan.exists())
        assertEquals(1, spaceProbeCalls)
    }

    @Test
    fun rejectsTruncatedGzipBadMagicNewerSchemaAndMissingSentinel() {
        val restoreDir = temp.newFolder("restore-reject")

        assertEquals(
            BackupRestorePolicy.CopyId.TRUNCATED_GZIP,
            validate(restoreDir, "truncated") {
                ByteArrayInputStream(byteArrayOf(0x1f, 0x8b.toByte(), 0x08))
            }.policy.copyId,
        )
        assertEquals(
            BackupRestorePolicy.CopyId.BAD_SQLITE_MAGIC,
            validate(restoreDir, "wrong") {
                ByteArrayInputStream(gzip("not sqlite".toByteArray()))
            }.policy.copyId,
        )
        assertEquals(
            BackupRestorePolicy.CopyId.NEWER_DATABASE_VERSION,
            validate(restoreDir, "newer") {
                ByteArrayInputStream(
                    fixtureGzip(userVersion = LocalStoreSchema.DB_VERSION + 1, createSettings = true),
                )
            }.policy.copyId,
        )
        assertEquals(
            BackupRestorePolicy.CopyId.MISSING_SETTINGS_TABLE,
            validate(restoreDir, "other") {
                ByteArrayInputStream(
                    fixtureGzip(userVersion = LocalStoreSchema.DB_VERSION, createSettings = false),
                )
            }.policy.copyId,
        )
        assertTrue(restoreDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun rejectsDecompressionBombAndLowStorageWithDistinctReasons() {
        val gzip = fixtureGzip(userVersion = LocalStoreSchema.DB_VERSION, createSettings = true)
        val tooLargeDir = temp.newFolder("restore-too-large")
        val tooLarge = BackupRestoreValidator.validate(
            tooLargeDir,
            "large.db.gz",
            { ByteArrayInputStream(gzip) },
            maxDecompressedBytes = 64L,
            freeSpaceReserveBytes = 0L,
            allocatableSpaceProbe = BackupRestoreValidator.AllocatableSpaceProbe { Long.MAX_VALUE },
        )

        assertEquals(BackupRestorePolicy.CopyId.BACKUP_TOO_LARGE, tooLarge.policy.copyId)
        assertTrue(tooLargeDir.listFiles().isNullOrEmpty())

        val lowSpaceDir = temp.newFolder("restore-low-space")
        val lowSpace = BackupRestoreValidator.validate(
            lowSpaceDir,
            "low-space.db.gz",
            { ByteArrayInputStream(gzip) },
            maxDecompressedBytes = Long.MAX_VALUE,
            freeSpaceReserveBytes = 100L,
            allocatableSpaceProbe = BackupRestoreValidator.AllocatableSpaceProbe { 100L },
        )

        assertEquals(BackupRestorePolicy.CopyId.INSUFFICIENT_STORAGE, lowSpace.policy.copyId)
        assertTrue(lowSpaceDir.listFiles().isNullOrEmpty())

        val cumulativeDir = temp.newFolder("restore-cumulative-space")
        val cumulative = BackupRestoreValidator.validate(
            cumulativeDir,
            "cumulative-space.db.gz",
            { ByteArrayInputStream(gzip) },
            maxDecompressedBytes = Long.MAX_VALUE,
            freeSpaceReserveBytes = 0L,
            allocatableSpaceProbe = BackupRestoreValidator.AllocatableSpaceProbe { 9_000L },
        )

        assertEquals(BackupRestorePolicy.CopyId.INSUFFICIENT_STORAGE, cumulative.policy.copyId)
        assertTrue(cumulativeDir.listFiles().isNullOrEmpty())
    }

    private fun validate(
        restoreDir: File,
        sourceName: String,
        allocatableSpaceProbe: BackupRestoreValidator.AllocatableSpaceProbe =
            BackupRestoreValidator.AllocatableSpaceProbe { Long.MAX_VALUE },
        input: () -> ByteArrayInputStream,
    ): BackupRestoreValidation = BackupRestoreValidator.validate(
        restoreDir,
        sourceName,
        input,
        maxDecompressedBytes = BackupRestoreValidator.MAX_DECOMPRESSED_BYTES,
        freeSpaceReserveBytes = BackupRestoreValidator.FREE_SPACE_RESERVE_BYTES,
        allocatableSpaceProbe = allocatableSpaceProbe,
    )

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
