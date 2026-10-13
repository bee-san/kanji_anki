package dev.bee.kanjianki.data.android

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bee.kanjianki.data.sql.SqlConnection
import dev.bee.kanjianki.data.sql.SqlConnectionMode
import dev.bee.kanjianki.data.sql.SqlException
import dev.bee.kanjianki.data.sql.SqlPragma
import dev.bee.kanjianki.data.sql.SqlTransactionMode
import dev.bee.kanjianki.data.sql.testing.SqlDriverContractSuite
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidFrameworkSqlDriverInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val root: Path = context.cacheDir.toPath().resolve("sql-driver-contract")
    private val contract =
        SqlDriverContractSuite(
            factory = ::AndroidFrameworkSqlDriver,
            temporaryRoot = root,
        )

    @After
    fun tearDown() {
        contract.close()
    }

    @Test
    fun frameworkDriverPassesSharedContract() {
        assumeTrue("VACUUM INTO requires API 30+", Build.VERSION.SDK_INT >= 30)
        contract.runAll()
    }

    @Test
    fun deferredReaderDoesNotBlockWalWriter() {
        Files.createDirectories(root)
        val directory = Files.createTempDirectory(root, "deferred-")
        try {
            val path = directory.resolve("kani.db")
            AndroidFrameworkSqlDriver(path.toString()).use { driver ->
                driver.openConnection(SqlConnectionMode.READ_WRITE).use { writer ->
                    writer.pragmas.writeText(SqlPragma.JOURNAL_MODE, "WAL")
                    writer.pragmas.writeLong(SqlPragma.BUSY_TIMEOUT, 0)
                    writer.execute("CREATE TABLE state(value TEXT NOT NULL)")
                    writer.execute("INSERT INTO state(value) VALUES ('before')")

                    driver.openConnection(SqlConnectionMode.READ_ONLY).use { reader ->
                        reader.pragmas.writeLong(SqlPragma.BUSY_TIMEOUT, 0)

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
        } finally {
            deleteRecursively(directory)
        }
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
