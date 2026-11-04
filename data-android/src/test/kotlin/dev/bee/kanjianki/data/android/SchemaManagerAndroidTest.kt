package dev.bee.kanjianki.data.android

import dev.bee.kanjianki.data.sql.DedicatedWriterSqlDatabase
import dev.bee.kanjianki.data.sql.MigrationClock
import dev.bee.kanjianki.data.sql.MigrationContext
import dev.bee.kanjianki.data.sql.SchemaManager
import dev.bee.kanjianki.data.sql.SchemaTransition
import dev.bee.kanjianki.data.sql.SchemaTransitionKind
import dev.bee.kanjianki.data.sql.SqlDatabase
import dev.bee.kanjianki.data.sql.SqlDatabaseConfiguration
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class SchemaManagerAndroidTest {
    private val temporaryDirectories = ArrayList<Path>()

    @After
    fun tearDown() {
        temporaryDirectories.asReversed().forEach(::deleteRecursively)
    }

    @Test
    @Config(sdk = [26, 35])
    fun frameworkDriverMigratesTheFrozenV1DatabaseAtomically() = runBlocking {
        val path = installV1Fixture()
        openDatabase(path).use { database ->
            assertEquals(
                SchemaTransition(1, 34, SchemaTransitionKind.UPGRADED),
                manager().initialize(database),
            )
            assertEquals(34L, scalarLong(database, "PRAGMA user_version"))
            assertEquals(
                "goal178-v1",
                scalarText(
                    database,
                    "SELECT value FROM settings WHERE key='goal178.fixture'",
                ),
            )
            assertEquals(
                3L,
                scalarLong(
                    database,
                    "SELECT COUNT(*) FROM kanji_timeline_events WHERE kanji='F'",
                ),
            )
            assertEquals(
                "ok",
                scalarText(database, "PRAGMA integrity_check"),
            )
        }
    }

    private fun manager(): SchemaManager =
        SchemaManager(
            MigrationContext(
                clock = MigrationClock { FIXED_NOW },
            ),
        )

    private fun openDatabase(path: Path): DedicatedWriterSqlDatabase =
        DedicatedWriterSqlDatabase(
            driver = AndroidFrameworkSqlDriver(path.toString()),
            configuration = SqlDatabaseConfiguration(
                busyTimeoutMillis = 1_000,
                writerThreadName = "android-schema-test",
            ),
        )

    private fun installV1Fixture(): Path {
        val directory = Files.createTempDirectory("kani-android-schema-")
        temporaryDirectories.add(directory)
        val path = directory.resolve("kani.db")
        GZIPInputStream(
            Files.newInputStream(
                resourceRoot().resolve("historical-v1.db.gz"),
            ),
        ).use { input ->
            Files.newOutputStream(path).use(input::copyTo)
        }
        return path
    }

    private fun resourceRoot(): Path =
        Path.of(requireNotNull(System.getProperty("kani.goal178.resources")))

    private suspend fun scalarLong(
        database: SqlDatabase,
        sql: String,
    ): Long =
        database.readSnapshot {
            prepare(sql).use { statement ->
                statement.query().use { rows ->
                    check(rows.next()) { "Scalar query returned no row: $sql" }
                    rows.row.long(0)
                }
            }
        }

    private suspend fun scalarText(
        database: SqlDatabase,
        sql: String,
    ): String =
        database.readSnapshot {
            prepare(sql).use { statement ->
                statement.query().use { rows ->
                    check(rows.next()) { "Scalar query returned no row: $sql" }
                    rows.row.text(0)
                }
            }
        }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private companion object {
        const val FIXED_NOW = 179_000L
    }
}
