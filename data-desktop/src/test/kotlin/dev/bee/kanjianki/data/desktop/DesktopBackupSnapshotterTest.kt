package dev.bee.kanjianki.data.desktop

import dev.bee.kanjianki.data.SettingsSaveCommand
import dev.bee.kanjianki.data.sql.MigrationClock
import dev.bee.kanjianki.data.sql.MigrationContext
import dev.bee.kanjianki.data.sql.SchemaTransitionKind
import dev.bee.kanjianki.data.sql.SqlSettingsRepository
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopBackupSnapshotterTest {
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
    fun snapshotsAPopulatedDatabaseIntoARestorableGzip() = runBlocking {
        val profile = tempRoot()
        val databasePath = profile.resolve("kanji_anki_simple.db")
        writeStudyAhead(databasePath, minutes = 55)

        val destination = profile.resolve("backups/snapshot.db.gz")
        val bytes = DesktopBackupSnapshotter.snapshot(databasePath, destination)

        assertTrue(bytes > 0L)
        assertTrue(Files.isRegularFile(destination))
        assertTrue(isGzip(destination))
        // No scratch files survive.
        assertFalse(Files.exists(profile.resolve("backups/snapshot.db.gz.vacuum.tmp")))
        assertFalse(Files.exists(profile.resolve("backups/snapshot.db.gz.partial")))

        // The decompressed snapshot is a valid v34 database carrying the setting.
        val restored = profile.resolve("restored.db")
        decompressTo(destination, restored)
        assertEquals(55, readStudyAhead(restored))
    }

    @Test
    fun refusesToOverwriteAnExistingDestination() = runBlocking {
        val profile = tempRoot()
        val databasePath = profile.resolve("kanji_anki_simple.db")
        writeStudyAhead(databasePath, minutes = 10)
        val destination = profile.resolve("snapshot.db.gz")
        Files.write(destination, byteArrayOf(1))

        assertThrowsIo { DesktopBackupSnapshotter.snapshot(databasePath, destination) }
    }

    @Test
    fun refusesWhenTheDatabaseIsMissing() {
        val profile = tempRoot()
        assertThrowsIo {
            DesktopBackupSnapshotter.snapshot(
                profile.resolve("absent.db"),
                profile.resolve("snapshot.db.gz"),
            )
        }
    }

    private fun writeStudyAhead(databasePath: Path, minutes: Int) = runBlocking {
        val opened = DesktopDatabaseFactory.open(databasePath.toString(), migrationContext())
        try {
            assertEquals(SchemaTransitionKind.CREATED, opened.transition.kind)
            val settings = SqlSettingsRepository(opened.database) { FIXED_CLOCK }
            assertTrue(settings.save(SettingsSaveCommand.StudyAhead(minutes = minutes)).isOk())
        } finally {
            opened.database.close()
        }
    }

    private fun readStudyAhead(databasePath: Path): Int? = runBlocking {
        val opened = DesktopDatabaseFactory.open(databasePath.toString(), migrationContext())
        try {
            SqlSettingsRepository(opened.database) { FIXED_CLOCK }.load().valueOrNull()?.studyAheadMinutes
        } finally {
            opened.database.close()
        }
    }

    private fun isGzip(path: Path): Boolean {
        val header = Files.readAllBytes(path)
        return header.size >= 2 &&
            header[0] == 0x1f.toByte() &&
            header[1] == 0x8b.toByte()
    }

    private fun decompressTo(source: Path, destination: Path) {
        GZIPInputStream(Files.newInputStream(source)).use { input ->
            Files.newOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun assertThrowsIo(block: () -> Unit) {
        try {
            block()
            throw AssertionError("expected IOException")
        } catch (_: IOException) {
            // expected
        }
    }

    private fun migrationContext(): MigrationContext =
        MigrationContext(clock = MigrationClock { FIXED_CLOCK })

    private fun tempRoot(): Path {
        val directory = Files.createTempDirectory("kani-desktop-backup-")
        temporaryDirectories.add(directory)
        return directory
    }

    private companion object {
        const val FIXED_CLOCK = 1_770_050_000_000L
    }
}
