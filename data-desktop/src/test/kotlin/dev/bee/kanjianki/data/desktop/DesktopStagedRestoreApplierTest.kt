package dev.bee.kanjianki.data.desktop

import dev.bee.kanjianki.backup.core.RestoreMarkerCodec
import dev.bee.kanjianki.data.SettingsSaveCommand
import dev.bee.kanjianki.data.sql.MigrationClock
import dev.bee.kanjianki.data.sql.MigrationContext
import dev.bee.kanjianki.data.sql.SqlSettingsRepository
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopStagedRestoreApplierTest {
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
    fun noOpWhenNoRestoreDirectoryExists() {
        val profile = tempRoot()
        assertEquals(DesktopStagedRestoreApplier.Result.NO_OP, DesktopStagedRestoreApplier.apply(profile, NOW))
    }

    @Test
    fun stageThenApplyReplacesTheLiveDatabaseAndKeepsASafetyBackup() {
        val profile = tempRoot()
        // The live profile database holds minutes=10.
        writeStudyAhead(profile.resolve(DesktopStorageLayout.DATABASE_FILE_NAME), minutes = 10)
        // A validated restore database (from a different profile) holds minutes=99.
        val validated = buildValidatedDatabase(profile, minutes = 99)

        DesktopStagedRestoreApplier.stage(profile, validated)
        val steps = ArrayList<DesktopStagedRestoreApplier.Step>()
        val result = DesktopStagedRestoreApplier.apply(profile, NOW) { steps.add(it) }

        assertEquals(DesktopStagedRestoreApplier.Result.APPLIED, result)
        // Live database now carries the restored value.
        assertEquals(99, readStudyAhead(profile.resolve(DesktopStorageLayout.DATABASE_FILE_NAME)))
        // A pre-restore safety backup was created.
        val backups = DesktopBackupManager.listBackups(profile.resolve(DesktopStorageLayout.BACKUPS_DIR_NAME))
        assertTrue("expected a safety backup", backups.isNotEmpty())
        // Restore directory is cleaned up and the full step sequence ran.
        assertFalse(Files.exists(profile.resolve("restore")))
        assertTrue(steps.contains(DesktopStagedRestoreApplier.Step.SAFETY_BACKUP_CREATED))
        assertTrue(steps.contains(DesktopStagedRestoreApplier.Step.DATABASE_REPLACED))
        assertTrue(steps.contains(DesktopStagedRestoreApplier.Step.MARKER_DELETED))
    }

    @Test
    fun markerOnlyStateFinishesTheReplacementIdempotently() {
        val profile = tempRoot()
        writeStudyAhead(profile.resolve(DesktopStorageLayout.DATABASE_FILE_NAME), minutes = 42)
        val restoreDir = profile.resolve("restore")
        Files.createDirectories(restoreDir)
        // A SAFETY_READY marker with no staged file: the replace already happened.
        Files.write(
            restoreDir.resolve(DesktopStagedRestoreApplier.MARKER_FILE_NAME),
            RestoreMarkerCodec.encodeReady(
                RestoreMarkerCodec.ReadyMarker("backup.db.gz", NOW),
            ).toByteArray(StandardCharsets.UTF_8),
        )
        // Leftover sidecars to be cleaned.
        Files.write(profile.resolve(DesktopStorageLayout.DATABASE_FILE_NAME + "-wal"), byteArrayOf(1))

        val result = DesktopStagedRestoreApplier.apply(profile, NOW)

        assertEquals(DesktopStagedRestoreApplier.Result.APPLIED, result)
        assertFalse(Files.exists(profile.resolve(DesktopStorageLayout.DATABASE_FILE_NAME + "-wal")))
        assertFalse(Files.exists(restoreDir))
    }

    @Test
    fun blocksStartupWhenAMarkerOnlyStateIsInvalid() {
        val profile = tempRoot()
        val restoreDir = profile.resolve("restore")
        Files.createDirectories(restoreDir)
        Files.write(
            restoreDir.resolve(DesktopStagedRestoreApplier.MARKER_FILE_NAME),
            "garbage=not-a-real-marker".toByteArray(StandardCharsets.UTF_8),
        )

        val result = DesktopStagedRestoreApplier.apply(profile, NOW)

        assertEquals(DesktopStagedRestoreApplier.Result.BLOCK_STARTUP, result)
    }

    @Test
    fun blocksStartupWhenAStagedFileHasAnInvalidMarker() {
        val profile = tempRoot()
        writeStudyAhead(profile.resolve(DesktopStorageLayout.DATABASE_FILE_NAME), minutes = 3)
        val restoreDir = profile.resolve("restore")
        Files.createDirectories(restoreDir)
        Files.write(restoreDir.resolve(DesktopStagedRestoreApplier.STAGED_FILE_NAME), byteArrayOf(9))
        Files.write(
            restoreDir.resolve(DesktopStagedRestoreApplier.MARKER_FILE_NAME),
            "format=99\nphase=bogus\n".toByteArray(StandardCharsets.UTF_8),
        )

        val result = DesktopStagedRestoreApplier.apply(profile, NOW)

        assertEquals(DesktopStagedRestoreApplier.Result.BLOCK_STARTUP, result)
    }

    @Test
    fun noOpWhenRestoreDirectoryIsEmpty() {
        val profile = tempRoot()
        Files.createDirectories(profile.resolve("restore"))
        assertEquals(DesktopStagedRestoreApplier.Result.NO_OP, DesktopStagedRestoreApplier.apply(profile, NOW))
        // The empty restore directory is cleaned up.
        assertFalse(Files.exists(profile.resolve("restore")))
    }

    /** Builds a validated database file (as the validator would stage) with a value. */
    private fun buildValidatedDatabase(profile: Path, minutes: Int): Path {
        val builder = profile.resolve("source-profile/kanji_anki_simple.db")
        Files.createDirectories(builder.parent)
        writeStudyAhead(builder, minutes)
        // Snapshot + decompress to mimic a validated staged file on the same fs.
        val gz = profile.resolve("source.db.gz")
        DesktopBackupSnapshotter.snapshot(builder, gz)
        val staged = profile.resolve("validated.db")
        java.util.zip.GZIPInputStream(Files.newInputStream(gz)).use { input ->
            Files.newOutputStream(staged).use { output -> input.copyTo(output) }
        }
        return staged
    }

    private fun writeStudyAhead(databasePath: Path, minutes: Int) = runBlocking {
        Files.createDirectories(databasePath.parent)
        val opened = DesktopDatabaseFactory.open(
            databasePath.toString(),
            MigrationContext(clock = MigrationClock { NOW }),
        )
        try {
            val settings = SqlSettingsRepository(opened.database) { NOW }
            assertTrue(settings.save(SettingsSaveCommand.StudyAhead(minutes = minutes)).isOk())
        } finally {
            opened.database.close()
        }
    }

    private fun readStudyAhead(databasePath: Path): Int? = runBlocking {
        val opened = DesktopDatabaseFactory.open(
            databasePath.toString(),
            MigrationContext(clock = MigrationClock { NOW }),
        )
        try {
            SqlSettingsRepository(opened.database) { NOW }.load().valueOrNull()?.studyAheadMinutes
        } finally {
            opened.database.close()
        }
    }

    private fun tempRoot(): Path {
        val directory = Files.createTempDirectory("kani-desktop-apply-")
        temporaryDirectories.add(directory)
        return directory
    }

    private companion object {
        const val NOW = 1_770_050_000_000L
    }
}
