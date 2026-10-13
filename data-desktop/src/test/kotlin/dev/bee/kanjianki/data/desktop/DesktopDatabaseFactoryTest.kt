package dev.bee.kanjianki.data.desktop

import dev.bee.kanjianki.data.SettingsSaveCommand
import dev.bee.kanjianki.data.sql.MigrationClock
import dev.bee.kanjianki.data.sql.MigrationContext
import dev.bee.kanjianki.data.sql.SchemaTransitionKind
import dev.bee.kanjianki.data.sql.SqlSettingsRepository
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end proof that the desktop stack opens a profile database on the
 * production bundled SQLite driver, migrates it to the canonical schema, and
 * drives a real `:data-sql` repository — the desktop equivalent of the Android
 * framework-driver contract test.
 */
class DesktopDatabaseFactoryTest {
    private val temporaryDirectories = ArrayList<Path>()

    @After
    fun tearDown() {
        temporaryDirectories.asReversed().forEach(::deleteRecursively)
    }

    @Test
    fun freshProfileDatabaseCreatesSchemaAndRunsARepository() = runBlocking {
        val path = profileDatabase()
        val opened = DesktopDatabaseFactory.open(path, migrationContext())
        try {
            assertEquals(SchemaTransitionKind.CREATED, opened.transition.kind)
            val settings = SqlSettingsRepository(opened.database) { FIXED_CLOCK }
            assertTrue(
                settings.save(SettingsSaveCommand.StudyAhead(minutes = 45)).isOk(),
            )
            assertEquals(45, settings.load().valueOrNull()?.studyAheadMinutes)
        } finally {
            opened.database.close()
        }
    }

    @Test
    fun reopeningAnExistingProfileLeavesTheSchemaUnchanged() = runBlocking {
        val path = profileDatabase()
        DesktopDatabaseFactory.open(path, migrationContext()).database.close()
        val reopened = DesktopDatabaseFactory.open(path, migrationContext())
        try {
            assertEquals(SchemaTransitionKind.UNCHANGED, reopened.transition.kind)
        } finally {
            reopened.database.close()
        }
    }

    private fun migrationContext(): MigrationContext =
        MigrationContext(clock = MigrationClock { FIXED_CLOCK })

    private fun profileDatabase(): String {
        val directory = Files.createTempDirectory("kani-desktop-db-")
        temporaryDirectories.add(directory)
        return directory.resolve("kanji_anki_simple.db").toString()
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private companion object {
        const val FIXED_CLOCK = 1_770_050_000_000L
    }
}
