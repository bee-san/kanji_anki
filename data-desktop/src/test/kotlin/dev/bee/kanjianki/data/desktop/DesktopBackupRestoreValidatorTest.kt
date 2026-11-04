package dev.bee.kanjianki.data.desktop

import dev.bee.kanjianki.core.BackupRestorePolicy
import dev.bee.kanjianki.data.SettingsSaveCommand
import dev.bee.kanjianki.data.sql.MigrationClock
import dev.bee.kanjianki.data.sql.MigrationContext
import dev.bee.kanjianki.data.sql.SqlSettingsRepository
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopBackupRestoreValidatorTest {
    private val temporaryDirectories = ArrayList<Path>()

    @After
    fun tearDown() {
        temporaryDirectories.asReversed().forEach { directory ->
            if (!Files.exists(directory)) return@forEach
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    @Test
    fun acceptsAValidBackupAndStagesTheDatabase() {
        val profile = tempRoot()
        val backup = createRealBackup(profile)
        val restoreDir = profile.resolve("restore")

        val validation = DesktopBackupRestoreValidator.validate(
            restoreDir,
            sourceName = "my-backup.db.gz",
            input = { Files.newInputStream(backup) },
        )

        assertTrue(validation.result.accepted)
        assertEquals(BackupRestorePolicy.CopyId.READY, validation.result.copyId)
        assertNotNull(validation.stagedDatabase)
        assertEquals("my-backup.db.gz", validation.sourceName)
        assertTrue(Files.isRegularFile(validation.stagedDatabase))
    }

    @Test
    fun rejectsATruncatedGzip() {
        val restoreDir = tempRoot().resolve("restore")
        val validation = DesktopBackupRestoreValidator.validate(
            restoreDir,
            sourceName = "broken.gz",
            input = { ByteArrayInputStream(byteArrayOf(0x1f, 0x8b.toByte(), 0x00, 0x01)) },
        )
        assertFalse(validation.result.accepted)
        assertEquals(BackupRestorePolicy.CopyId.TRUNCATED_GZIP, validation.result.copyId)
        assertNull(validation.stagedDatabase)
    }

    @Test
    fun rejectsAGzipThatIsNotASqliteDatabase() {
        val restoreDir = tempRoot().resolve("restore")
        val validation = DesktopBackupRestoreValidator.validate(
            restoreDir,
            sourceName = "notdb.gz",
            input = { gzipOf("this is definitely not a sqlite database".toByteArray()) },
        )
        assertFalse(validation.result.accepted)
        assertEquals(BackupRestorePolicy.CopyId.BAD_SQLITE_MAGIC, validation.result.copyId)
    }

    @Test
    fun rejectsWhenTheInputStreamIsNull() {
        val restoreDir = tempRoot().resolve("restore")
        val validation = DesktopBackupRestoreValidator.validate(
            restoreDir,
            sourceName = "none",
            input = { null },
        )
        assertFalse(validation.result.accepted)
        assertEquals(BackupRestorePolicy.CopyId.TRUNCATED_GZIP, validation.result.copyId)
    }

    @Test
    fun rejectsWhenTheDecompressedSizeExceedsTheCap() {
        val profile = tempRoot()
        val backup = createRealBackup(profile)
        val restoreDir = profile.resolve("restore")
        val validation = DesktopBackupRestoreValidator.validate(
            restoreDir,
            sourceName = "big.gz",
            input = { Files.newInputStream(backup) },
            maxDecompressedBytes = 16L,
        )
        assertFalse(validation.result.accepted)
        assertEquals(BackupRestorePolicy.CopyId.BACKUP_TOO_LARGE, validation.result.copyId)
    }

    @Test
    fun rejectsWhenThereIsInsufficientStorage() {
        val profile = tempRoot()
        val backup = createRealBackup(profile)
        val restoreDir = profile.resolve("restore")
        val validation = DesktopBackupRestoreValidator.validate(
            restoreDir,
            sourceName = "tight.gz",
            input = { Files.newInputStream(backup) },
            spaceProbe = { 0L },
        )
        assertFalse(validation.result.accepted)
        assertEquals(BackupRestorePolicy.CopyId.INSUFFICIENT_STORAGE, validation.result.copyId)
    }

    private fun createRealBackup(profile: Path): Path {
        val databasePath = profile.resolve("kanji_anki_simple.db")
        runBlocking {
            val opened = DesktopDatabaseFactory.open(
                databasePath.toString(),
                MigrationContext(clock = MigrationClock { FIXED_CLOCK }),
            )
            try {
                val settings = SqlSettingsRepository(opened.database) { FIXED_CLOCK }
                settings.save(SettingsSaveCommand.StudyAhead(minutes = 33))
            } finally {
                opened.database.close()
            }
        }
        val backup = profile.resolve("backup.db.gz")
        DesktopBackupSnapshotter.snapshot(databasePath, backup)
        return backup
    }

    private fun gzipOf(bytes: ByteArray): InputStream {
        val buffer = java.io.ByteArrayOutputStream()
        GZIPOutputStream(buffer).use { it.write(bytes) }
        return ByteArrayInputStream(buffer.toByteArray())
    }

    private fun tempRoot(): Path {
        val directory = Files.createTempDirectory("kani-desktop-restore-")
        temporaryDirectories.add(directory)
        return directory
    }

    private companion object {
        const val FIXED_CLOCK = 1_770_050_000_000L
    }
}
