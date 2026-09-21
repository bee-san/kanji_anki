package dev.bee.kanjianki.data.desktop

import dev.bee.kanjianki.backup.core.PortableBackupMetadata
import dev.bee.kanjianki.data.SettingsSaveCommand
import dev.bee.kanjianki.data.sql.MigrationClock
import dev.bee.kanjianki.data.sql.MigrationContext
import dev.bee.kanjianki.data.sql.SchemaManager
import dev.bee.kanjianki.data.sql.SqlDatabase
import dev.bee.kanjianki.data.sql.SqlSettingsRepository
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the cross-platform backup format contract end-to-end on real bundled
 * SQLite: a stamped desktop backup restores as a same-host clean restore, while
 * a legacy (unstamped) backup carrying foreign device-local rows takes the
 * unknown-origin path — resetting the device-local keys and requiring provider
 * revalidation. Desktop-to-Android compatibility is the mirror of this using the
 * shared `PortableBackupMetadata`/`CrossPlatformRestorePlanner`, which are pure
 * and covered in `:backup-core`.
 */
class CrossPlatformBackupCompatibilityTest {
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
    fun stampedDesktopBackupRestoresAsSameHostCleanRestore() = runBlocking {
        val profile = tempRoot()
        val databasePath = profile.resolve(DesktopStorageLayout.DATABASE_FILE_NAME)
        // Produce a stamped desktop backup carrying a portable setting.
        withDatabase(databasePath) { database ->
            SqlSettingsRepository(database) { NOW }.save(SettingsSaveCommand.StudyAhead(minutes = 25))
            DesktopPortableBackupStamper.stamp(database, SchemaManager.DATABASE_VERSION)
        }
        val backup = profile.resolve("desktop-backup.db.gz")
        DesktopBackupSnapshotter.snapshot(databasePath, backup)

        // Restore into a fresh profile and read provenance.
        val restored = restoreBackupInto(backup, profile.resolve("restored.db"))
        withDatabase(restored) { database ->
            val metadata = DesktopPortableBackupStamper.read(database)
            assertEquals(PortableBackupMetadata.Origin.DESKTOP, metadata.origin)
            assertEquals(SchemaManager.DATABASE_VERSION, metadata.schemaVersion)

            val outcome = DesktopCrossPlatformRestoreFinalizer.finalize(
                database,
                backupHost = PortableBackupMetadata.host(metadata.origin),
                destinationHost = PortableBackupMetadata.Origin.DESKTOP.let(PortableBackupMetadata::host),
            )
            assertTrue(outcome.resetKeys.isEmpty())
            assertFalse(outcome.requiresProviderRevalidation)
            // The portable setting survived.
            assertEquals(25, SqlSettingsRepository(database) { NOW }.load().valueOrNull()?.studyAheadMinutes)
        }
    }

    @Test
    fun legacyUnstampedBackupWithForeignKeysResetsDeviceStateOnRestore() = runBlocking {
        val profile = tempRoot()
        val databasePath = profile.resolve(DesktopStorageLayout.DATABASE_FILE_NAME)
        // A legacy backup: no portable metadata, but a foreign device-local row.
        withDatabase(databasePath) { database ->
            database.write {
                prepare("INSERT INTO settings(key, value, updated_at) VALUES (?, ?, 0)").use { statement ->
                    statement.bindText(1, "reminder_enabled")
                    statement.bindText(2, "true")
                    statement.execute()
                }
            }
        }
        val backup = profile.resolve("legacy-backup.db.gz")
        DesktopBackupSnapshotter.snapshot(databasePath, backup)

        val restored = restoreBackupInto(backup, profile.resolve("restored.db"))
        withDatabase(restored) { database ->
            val metadata = DesktopPortableBackupStamper.read(database)
            assertEquals(PortableBackupMetadata.Origin.UNKNOWN, metadata.origin)

            val outcome = DesktopCrossPlatformRestoreFinalizer.finalize(
                database,
                backupHost = PortableBackupMetadata.host(metadata.origin),
                destinationHost = PortableBackupMetadata.Origin.DESKTOP.let(PortableBackupMetadata::host),
            )
            // The foreign device-local key is reset rather than imported.
            assertTrue(outcome.resetKeys.contains("reminder_enabled"))
            assertTrue(outcome.requiresProviderRevalidation)
        }
    }

    private fun restoreBackupInto(backup: Path, destination: Path): Path {
        GZIPInputStream(Files.newInputStream(backup)).use { input ->
            Files.newOutputStream(destination).use { output -> input.copyTo(output) }
        }
        return destination
    }

    private fun withDatabase(databasePath: Path, block: suspend (SqlDatabase) -> Unit) = runBlocking {
        Files.createDirectories(databasePath.parent)
        val opened = DesktopDatabaseFactory.open(
            databasePath.toString(),
            MigrationContext(clock = MigrationClock { NOW }),
        )
        try {
            block(opened.database)
        } finally {
            opened.database.close()
        }
    }

    private fun tempRoot(): Path {
        val directory = Files.createTempDirectory("kani-desktop-compat-")
        temporaryDirectories.add(directory)
        return directory
    }

    private companion object {
        const val NOW = 1_770_050_000_000L
    }
}
