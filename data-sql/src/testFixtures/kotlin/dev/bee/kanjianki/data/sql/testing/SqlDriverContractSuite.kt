package dev.bee.kanjianki.data.sql.testing

import dev.bee.kanjianki.data.sql.SqlBusyException
import dev.bee.kanjianki.data.sql.SqlConnection
import dev.bee.kanjianki.data.sql.SqlConnectionClosedException
import dev.bee.kanjianki.data.sql.SqlConnectionMode
import dev.bee.kanjianki.data.sql.SqlConstraintException
import dev.bee.kanjianki.data.sql.SqlConstraintKind
import dev.bee.kanjianki.data.sql.SqlDriver
import dev.bee.kanjianki.data.sql.SqlException
import dev.bee.kanjianki.data.sql.SqlPragma
import dev.bee.kanjianki.data.sql.SqlTransactionMode
import dev.bee.kanjianki.data.sql.SqlValueType
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue

fun interface SqlDriverFactory {
    fun open(path: String): SqlDriver
}

class SqlDriverContractSuite(
    private val factory: SqlDriverFactory,
    private val temporaryRoot: Path? = null,
) : AutoCloseable {
    private val temporaryDirectories = ArrayList<Path>()

    fun runAll() {
        bindsEveryPortableTypeWithOneBasedIndexesAndReusableStatements()
        classifiesEverySupportedConstraintKind()
        insertOrIgnorePreservesTokenIdempotenceAndChangesCount()
        nestedSavepointRollbackKeepsOuterTransaction()
        outerRollbackDiscardsAllChanges()
        immediateWritersRespectBusyLockAndCommittedRowsAreDurable()
        readOnlySnapshotDoesNotBlockWalWriterAndRejectsWrites()
        vacuumIntoProducesAnIndependentReadableSnapshot()
        statementsConnectionsRowsAndDriversRejectUseAfterClose()
    }

    override fun close() {
        temporaryDirectories.asReversed().forEach(::deleteRecursively)
        temporaryDirectories.clear()
    }

    private fun bindsEveryPortableTypeWithOneBasedIndexesAndReusableStatements() {
        withConnection("binds") { connection ->
            connection.execute(
                """
                CREATE TABLE values_table(
                    id INTEGER PRIMARY KEY,
                    null_value TEXT,
                    text_value TEXT NOT NULL,
                    integer_value INTEGER NOT NULL,
                    real_value REAL NOT NULL,
                    blob_value BLOB NOT NULL
                )
                """.trimIndent(),
            )
            connection.prepare(
                """
                INSERT INTO values_table(
                    id, null_value, text_value, integer_value, real_value, blob_value
                ) VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.bindLong(1, 7)
                statement.bindNull(2)
                statement.bindText(3, "Japanese text")
                statement.bindLong(4, Long.MAX_VALUE)
                statement.bindDouble(5, 3.25)
                statement.bindBlob(6, byteArrayOf(0, 1, -1))
                statement.execute()
            }

            assertEquals(1L, connection.changes())
            assertEquals(7L, connection.lastInsertRowId())
            connection.prepare(
                """
                SELECT
                    null_value, text_value, integer_value, real_value, blob_value
                FROM values_table
                """.trimIndent(),
            ).use { statement ->
                statement.query().use { rows ->
                    assertTrue(rows.next())
                    val row = rows.row
                    assertEquals(5, row.columnCount)
                    assertEquals("null_value", row.columnName(0))
                    assertEquals(SqlValueType.NULL, row.valueType(0))
                    assertTrue(row.isNull(0))
                    assertEquals(SqlValueType.TEXT, row.valueType(1))
                    assertEquals("Japanese text", row.text(1))
                    assertEquals(SqlValueType.INTEGER, row.valueType(2))
                    assertEquals(Long.MAX_VALUE, row.long(2))
                    assertEquals(SqlValueType.REAL, row.valueType(3))
                    assertEquals(3.25, row.double(3), 0.0)
                    assertEquals(SqlValueType.BLOB, row.valueType(4))
                    assertArrayEquals(byteArrayOf(0, 1, -1), row.blob(4))
                    assertFalse(rows.next())
                }
            }

            connection.prepare("SELECT ? AS probe").use { statement ->
                statement.bindText(1, "first")
                statement.query().use { rows ->
                    assertTrue(rows.next())
                    assertEquals("first", rows.row.text(0))
                }
                statement.reset()
                statement.clearBindings()
                statement.bindNull(1)
                statement.query().use { rows ->
                    assertTrue(rows.next())
                    assertTrue(rows.row.isNull(0))
                }
                assertThrows(SqlException::class.java) {
                    statement.bindLong(0, 1)
                }
            }
        }
    }

    private fun classifiesEverySupportedConstraintKind() {
        withConnection("constraints") { connection ->
            connection.pragmas.writeLong(SqlPragma.FOREIGN_KEYS, 1)
            connection.execute(
                """
                CREATE TABLE parent(
                    id INTEGER PRIMARY KEY,
                    token TEXT NOT NULL UNIQUE,
                    score INTEGER NOT NULL CHECK(score > 0)
                )
                """.trimIndent(),
            )
            connection.execute(
                """
                CREATE TABLE child(
                    id INTEGER PRIMARY KEY,
                    parent_id INTEGER NOT NULL REFERENCES parent(id)
                )
                """.trimIndent(),
            )
            connection.execute(
                "INSERT INTO parent(id, token, score) VALUES (1, 'one', 1)",
            )

            assertConstraint(connection, SqlConstraintKind.PRIMARY_KEY) {
                execute("INSERT INTO parent(id, token, score) VALUES (1, 'two', 1)")
            }
            assertConstraint(connection, SqlConstraintKind.UNIQUE) {
                execute("INSERT INTO parent(id, token, score) VALUES (2, 'one', 1)")
            }
            assertConstraint(connection, SqlConstraintKind.NOT_NULL) {
                execute("INSERT INTO parent(id, token, score) VALUES (2, NULL, 1)")
            }
            assertConstraint(connection, SqlConstraintKind.CHECK) {
                execute("INSERT INTO parent(id, token, score) VALUES (2, 'two', 0)")
            }
            assertConstraint(connection, SqlConstraintKind.FOREIGN_KEY) {
                execute("INSERT INTO child(id, parent_id) VALUES (1, 404)")
            }
        }
    }

    private fun insertOrIgnorePreservesTokenIdempotenceAndChangesCount() {
        withConnection("ignore") { connection ->
            connection.execute(
                "CREATE TABLE tokens(id INTEGER PRIMARY KEY, token TEXT NOT NULL UNIQUE)",
            )
            connection.prepare(
                "INSERT OR IGNORE INTO tokens(token) VALUES (?)",
            ).use { statement ->
                statement.bindText(1, "stable-token")
                statement.execute()
                assertEquals(1L, connection.changes())

                statement.reset()
                statement.clearBindings()
                statement.bindText(1, "stable-token")
                statement.execute()
                assertEquals(0L, connection.changes())
            }
            assertEquals(1L, scalarLong(connection, "SELECT COUNT(*) FROM tokens"))
        }
    }

    private fun nestedSavepointRollbackKeepsOuterTransaction() {
        withConnection("savepoint") { connection ->
            connection.execute("CREATE TABLE events(value TEXT NOT NULL)")
            connection.beginTransaction(SqlTransactionMode.IMMEDIATE)
            try {
                connection.execute("INSERT INTO events(value) VALUES ('outer')")
                connection.execute("SAVEPOINT nested")
                connection.execute("INSERT INTO events(value) VALUES ('nested')")
                connection.execute("ROLLBACK TO nested")
                connection.execute("RELEASE nested")
                connection.commitTransaction()
            } catch (failure: Throwable) {
                connection.rollbackTransaction()
                throw failure
            }

            assertEquals(
                "outer",
                scalarText(connection, "SELECT group_concat(value, ',') FROM events"),
            )
        }
    }

    private fun outerRollbackDiscardsAllChanges() {
        withConnection("rollback") { connection ->
            connection.execute("CREATE TABLE events(value TEXT NOT NULL)")
            connection.beginTransaction(SqlTransactionMode.IMMEDIATE)
            connection.execute("INSERT INTO events(value) VALUES ('discarded')")
            connection.rollbackTransaction()

            assertEquals(0L, scalarLong(connection, "SELECT COUNT(*) FROM events"))
        }
    }

    private fun immediateWritersRespectBusyLockAndCommittedRowsAreDurable() {
        val path = temporaryDatabase("locking")
        factory.open(path.toString()).use { driver ->
            driver.openConnection(SqlConnectionMode.READ_WRITE).use { first ->
                driver.openConnection(SqlConnectionMode.READ_WRITE).use { second ->
                    first.pragmas.writeLong(SqlPragma.BUSY_TIMEOUT, 0)
                    second.pragmas.writeLong(SqlPragma.BUSY_TIMEOUT, 0)
                    first.execute("CREATE TABLE durable(value TEXT NOT NULL)")
                    first.beginTransaction(SqlTransactionMode.IMMEDIATE)
                    first.execute("INSERT INTO durable(value) VALUES ('committed')")

                    assertThrows(SqlBusyException::class.java) {
                        second.beginTransaction(SqlTransactionMode.IMMEDIATE)
                    }
                    first.commitTransaction()

                    second.beginTransaction(SqlTransactionMode.IMMEDIATE)
                    second.execute("INSERT INTO durable(value) VALUES ('second')")
                    second.commitTransaction()
                }
            }
        }

        factory.open(path.toString()).use { reopened ->
            reopened.openConnection(SqlConnectionMode.READ_WRITE).use { connection ->
                assertEquals(2L, scalarLong(connection, "SELECT COUNT(*) FROM durable"))
                assertEquals("ok", scalarText(connection, "PRAGMA integrity_check"))
            }
        }
    }

    private fun readOnlySnapshotDoesNotBlockWalWriterAndRejectsWrites() {
        val path = temporaryDatabase("read-only")
        factory.open(path.toString()).use { driver ->
            driver.openConnection(SqlConnectionMode.READ_WRITE).use { writer ->
                writer.pragmas.writeText(SqlPragma.JOURNAL_MODE, "WAL")
                writer.pragmas.writeLong(SqlPragma.BUSY_TIMEOUT, 0)
                writer.execute("CREATE TABLE state(value TEXT NOT NULL)")
                writer.execute("INSERT INTO state(value) VALUES ('before')")

                driver.openConnection(SqlConnectionMode.READ_ONLY).use { reader ->
                    reader.pragmas.writeLong(SqlPragma.BUSY_TIMEOUT, 0)
                    reader.beginTransaction(SqlTransactionMode.DEFERRED)
                    assertEquals("before", scalarText(reader, "SELECT value FROM state"))
                    assertThrows(SqlException::class.java) {
                        reader.execute("UPDATE state SET value='forbidden'")
                    }

                    writer.beginTransaction(SqlTransactionMode.IMMEDIATE)
                    writer.execute("UPDATE state SET value='after'")
                    writer.commitTransaction()

                    assertEquals("before", scalarText(reader, "SELECT value FROM state"))
                    reader.commitTransaction()
                    assertEquals("after", scalarText(reader, "SELECT value FROM state"))
                }
            }
        }
    }

    private fun vacuumIntoProducesAnIndependentReadableSnapshot() {
        val source = temporaryDatabase("vacuum-source")
        val destination = source.parent.resolve("snapshot.db")
        factory.open(source.toString()).use { driver ->
            driver.openConnection(SqlConnectionMode.READ_WRITE).use { connection ->
                connection.execute("CREATE TABLE durable(value TEXT NOT NULL)")
                connection.execute("INSERT INTO durable(value) VALUES ('snapshot-value')")
                connection.execute("VACUUM INTO ${quoteSqlString(destination.toString())}")
            }
        }

        assertTrue(Files.isRegularFile(destination))
        factory.open(destination.toString()).use { snapshot ->
            snapshot.openConnection(SqlConnectionMode.READ_WRITE).use { connection ->
                assertEquals(
                    "snapshot-value",
                    scalarText(connection, "SELECT value FROM durable"),
                )
                assertEquals("ok", scalarText(connection, "PRAGMA integrity_check"))
            }
        }
    }

    private fun statementsConnectionsRowsAndDriversRejectUseAfterClose() {
        val path = temporaryDatabase("closure")
        val driver = factory.open(path.toString())
        val connection = driver.openConnection(SqlConnectionMode.READ_WRITE)
        val statement = connection.prepare("SELECT 1")
        val rows = statement.query()
        assertTrue(rows.next())

        rows.close()
        assertThrows(IllegalStateException::class.java) {
            rows.next()
        }
        statement.close()
        statement.close()
        assertThrows(SqlConnectionClosedException::class.java) {
            statement.reset()
        }

        connection.close()
        connection.close()
        assertFalse(connection.isOpen)
        assertThrows(SqlConnectionClosedException::class.java) {
            connection.prepare("SELECT 1")
        }

        driver.close()
        driver.close()
        assertThrows(SqlConnectionClosedException::class.java) {
            driver.openConnection(SqlConnectionMode.READ_WRITE)
        }
    }

    private fun assertConstraint(
        connection: SqlConnection,
        expected: SqlConstraintKind,
        operation: SqlConnection.() -> Unit,
    ) {
        val failure = assertThrows(SqlConstraintException::class.java) {
            connection.operation()
        }
        assertEquals(failure.message, expected, failure.kind)
    }

    private fun withConnection(
        label: String,
        block: (SqlConnection) -> Unit,
    ) {
        val path = temporaryDatabase(label)
        factory.open(path.toString()).use { driver ->
            driver.openConnection(SqlConnectionMode.READ_WRITE).use(block)
        }
    }

    private fun temporaryDatabase(label: String): Path {
        val prefix = "kani-driver-contract-$label-"
        val directory = if (temporaryRoot == null) {
            Files.createTempDirectory(prefix)
        } else {
            Files.createDirectories(temporaryRoot)
            Files.createTempDirectory(temporaryRoot, prefix)
        }
        temporaryDirectories.add(directory)
        return directory.resolve("kani.db")
    }

    private fun scalarLong(
        connection: SqlConnection,
        sql: String,
    ): Long =
        connection.prepare(sql).use { statement ->
            statement.query().use { rows ->
                check(rows.next()) { "Scalar query returned no row: $sql" }
                rows.row.long(0)
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

    private fun quoteSqlString(value: String): String =
        "'" + value.replace("'", "''") + "'"

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
