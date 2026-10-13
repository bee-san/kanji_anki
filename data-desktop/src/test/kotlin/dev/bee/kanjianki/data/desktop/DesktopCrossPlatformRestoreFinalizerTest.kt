package dev.bee.kanjianki.data.desktop

import dev.bee.kanjianki.backup.core.CrossPlatformRestorePlanner.Host
import dev.bee.kanjianki.data.sql.MigrationClock
import dev.bee.kanjianki.data.sql.MigrationContext
import dev.bee.kanjianki.data.sql.SqlDatabase
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopCrossPlatformRestoreFinalizerTest {
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
    fun crossHostRestoreDropsDeviceLocalRowsAndKeepsPortableOnes() = runBlocking {
        withDatabase { database ->
            seedSettings(
                database,
                "study_ladder_order" to "a,b,c",
                "reminder_enabled" to "true",
                "provider_endpoint" to "content://foreign",
            )

            val outcome = DesktopCrossPlatformRestoreFinalizer.finalize(
                database,
                backupHost = Host.ANDROID,
                destinationHost = Host.DESKTOP,
            )

            assertEquals(setOf("reminder_enabled", "provider_endpoint"), outcome.resetKeys)
            assertTrue(outcome.requiresProviderRevalidation)
            assertEquals(setOf("study_ladder_order"), readKeys(database))
        }
    }

    @Test
    fun sameHostCleanRestoreDeletesNothing() = runBlocking {
        withDatabase { database ->
            seedSettings(database, "study_ladder_order" to "x,y")

            val outcome = DesktopCrossPlatformRestoreFinalizer.finalize(
                database,
                backupHost = Host.DESKTOP,
                destinationHost = Host.DESKTOP,
            )

            assertTrue(outcome.resetKeys.isEmpty())
            assertFalse(outcome.requiresProviderRevalidation)
            assertEquals(setOf("study_ladder_order"), readKeys(database))
        }
    }

    private fun seedSettings(database: SqlDatabase, vararg rows: Pair<String, String>) = runBlocking {
        database.write {
            prepare("INSERT INTO settings(key, value, updated_at) VALUES (?, ?, 0)").use { statement ->
                for ((key, value) in rows) {
                    statement.reset()
                    statement.clearBindings()
                    statement.bindText(1, key)
                    statement.bindText(2, value)
                    statement.execute()
                }
            }
        }
    }

    private fun readKeys(database: SqlDatabase): Set<String> = runBlocking {
        database.readSnapshot {
            val keys = LinkedHashSet<String>()
            prepare("SELECT key FROM settings").use { statement ->
                statement.query().use { rows ->
                    while (rows.next()) keys.add(rows.row.text(0))
                }
            }
            keys
        }
    }

    private fun withDatabase(block: suspend (SqlDatabase) -> Unit) = runBlocking {
        val directory = Files.createTempDirectory("kani-desktop-finalize-")
        temporaryDirectories.add(directory)
        val opened = DesktopDatabaseFactory.open(
            directory.resolve("kanji_anki_simple.db").toString(),
            MigrationContext(clock = MigrationClock { FIXED_CLOCK }),
        )
        try {
            block(opened.database)
        } finally {
            opened.database.close()
        }
    }

    private companion object {
        const val FIXED_CLOCK = 1_770_050_000_000L
    }
}
