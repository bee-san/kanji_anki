package dev.bee.kanjianki.data.desktop

import dev.bee.kanjianki.core.DatabaseBackupPolicy
import dev.bee.kanjianki.data.SettingsSaveCommand
import dev.bee.kanjianki.data.sql.MigrationClock
import dev.bee.kanjianki.data.sql.MigrationContext
import dev.bee.kanjianki.data.sql.SqlSettingsRepository
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopBackupManagerTest {
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
    fun createsATimestampedBackupInTheBackupsDirectory() {
        val profile = tempRoot()
        val databasePath = profile.resolve("kanji_anki_simple.db")
        writeStudyAhead(databasePath, minutes = 20)
        val backupsDir = profile.resolve("backups")

        val result = DesktopBackupManager.createBackup(databasePath, backupsDir, DAY_ONE_MILLIS)

        assertTrue(Files.isRegularFile(result.file))
        assertTrue(result.gzipSizeBytes > 0L)
        assertTrue(result.file.fileName.toString().startsWith("kanji_anki_simple_"))
        assertTrue(result.file.fileName.toString().endsWith(".db.gz"))
        assertEquals(listOf(result.file), DesktopBackupManager.listBackups(backupsDir))
    }

    @Test
    fun advancesTheTimestampWhenANameIsAlreadyTaken() {
        val profile = tempRoot()
        val databasePath = profile.resolve("kanji_anki_simple.db")
        writeStudyAhead(databasePath, minutes = 5)
        val backupsDir = profile.resolve("backups")

        val first = DesktopBackupManager.createBackup(databasePath, databasePathParentBackups(backupsDir), DAY_ONE_MILLIS)
        val second = DesktopBackupManager.createBackup(databasePath, backupsDir, DAY_ONE_MILLIS)

        assertFalse(first.file.fileName == second.file.fileName)
        assertEquals(2, DesktopBackupManager.listBackups(backupsDir).size)
    }

    @Test
    fun prunesBeyondTheTieredRetentionWindow() {
        val profile = tempRoot()
        val backupsDir = profile.resolve("backups")
        Files.createDirectories(backupsDir)
        // 10 consecutive daily backups: retention keeps 7 daily + up to 4 weekly.
        val created = ArrayList<Path>()
        for (day in 1..10) {
            val name = DatabaseBackupPolicy.backupFile(backupsDir.toFile(), dayMillis(day)).name
            val file = backupsDir.resolve(name)
            Files.write(file, byteArrayOf(day.toByte()))
            created.add(file)
        }

        val pruned = DesktopBackupManager.prune(backupsDir)

        val remaining = DesktopBackupManager.listBackups(backupsDir)
        assertTrue("some backups should be pruned", pruned.isNotEmpty())
        assertTrue(remaining.size < created.size)
        // The most recent daily backup is always retained.
        assertTrue(remaining.contains(created.last()))
        pruned.forEach { assertFalse(Files.exists(it)) }
    }

    @Test
    fun listingAMissingDirectoryIsEmpty() {
        assertTrue(DesktopBackupManager.listBackups(tempRoot().resolve("absent")).isEmpty())
    }

    private fun databasePathParentBackups(backupsDir: Path): Path {
        Files.createDirectories(backupsDir)
        return backupsDir
    }

    private fun writeStudyAhead(databasePath: Path, minutes: Int) = runBlocking {
        val opened = DesktopDatabaseFactory.open(
            databasePath.toString(),
            MigrationContext(clock = MigrationClock { FIXED_CLOCK }),
        )
        try {
            val settings = SqlSettingsRepository(opened.database) { FIXED_CLOCK }
            assertTrue(settings.save(SettingsSaveCommand.StudyAhead(minutes = minutes)).isOk())
        } finally {
            opened.database.close()
        }
    }

    private fun dayMillis(day: Int): Long = DAY_ONE_MILLIS + (day - 1).toLong() * 86_400_000L

    private fun tempRoot(): Path {
        val directory = Files.createTempDirectory("kani-desktop-backup-mgr-")
        temporaryDirectories.add(directory)
        return directory
    }

    private companion object {
        const val FIXED_CLOCK = 1_770_050_000_000L
        const val DAY_ONE_MILLIS = 1_700_000_000_000L
    }
}
