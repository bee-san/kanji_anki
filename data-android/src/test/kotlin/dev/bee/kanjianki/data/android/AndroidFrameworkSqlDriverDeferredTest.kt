package dev.bee.kanjianki.data.android

import dev.bee.kanjianki.data.sql.SqlConnection
import dev.bee.kanjianki.data.sql.SqlException
import dev.bee.kanjianki.data.sql.SqlPragma
import dev.bee.kanjianki.data.sql.SqlTransactionMode
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AndroidFrameworkSqlDriverDeferredTest {
    private val temporaryDirectories = ArrayList<Path>()

    @After
    fun tearDown() {
        temporaryDirectories.asReversed().forEach(::deleteRecursively)
    }

    @Test
    @Config(sdk = [26, 35])
    fun deferredReaderKeepsSnapshotWithoutBlockingWalWriter() {
        val path = temporaryDatabase("deferred")
        AndroidFrameworkSqlDriver(path.toString()).use { driver ->
            driver.openConnection().use { writer ->
                driver.openConnection().use { reader ->
                    writer.pragmas.writeText(SqlPragma.JOURNAL_MODE, "WAL")
                    reader.pragmas.writeText(SqlPragma.JOURNAL_MODE, "WAL")
                    writer.pragmas.writeLong(SqlPragma.BUSY_TIMEOUT, 0)
                    reader.pragmas.writeLong(SqlPragma.BUSY_TIMEOUT, 0)
                    writer.execute("CREATE TABLE state(value TEXT NOT NULL)")
                    writer.execute("INSERT INTO state(value) VALUES ('before')")

                    reader.beginTransaction(SqlTransactionMode.DEFERRED)
                    assertEquals("before", scalarText(reader, "SELECT value FROM state"))

                    writer.beginTransaction(SqlTransactionMode.IMMEDIATE)
                    writer.execute("UPDATE state SET value='after'")
                    writer.commitTransaction()

                    assertEquals("before", scalarText(reader, "SELECT value FROM state"))
                    assertThrows(SqlException::class.java) {
                        reader.execute("UPDATE state SET value='forbidden'")
                    }
                    reader.commitTransaction()
                    assertEquals("after", scalarText(reader, "SELECT value FROM state"))
                }
            }
        }
    }

    @Test
    @Config(sdk = [35])
    fun interruptCancelsAnActiveCursor() {
        val path = temporaryDatabase("cancellation")
        AndroidFrameworkSqlDriver(path.toString()).use { driver ->
            driver.openConnection().use { connection ->
                connection.prepare(
                    """
                    WITH RECURSIVE numbers(value) AS (
                        SELECT 1
                        UNION ALL
                        SELECT value + 1 FROM numbers WHERE value < 1000000
                    )
                    SELECT sum(value) FROM numbers
                    """.trimIndent(),
                ).use { statement ->
                    statement.query().use { rows ->
                        connection.interrupt()
                        assertThrows(CancellationException::class.java) {
                            assertTrue(rows.next())
                        }
                    }
                }
            }
        }
    }

    private fun temporaryDatabase(label: String): Path {
        val directory = Files.createTempDirectory("kani-android-driver-$label-")
        temporaryDirectories.add(directory)
        return directory.resolve("kani.db")
    }

    private fun scalarText(
        connection: SqlConnection,
        sql: String,
    ): String =
        connection.prepare(sql).use { statement ->
            statement.query().use { rows ->
                check(rows.next()) { "Scalar query returned no row: $sql" }
                rows.row.text(0)
            }
        }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
