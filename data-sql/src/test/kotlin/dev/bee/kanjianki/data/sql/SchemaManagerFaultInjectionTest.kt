package dev.bee.kanjianki.data.sql

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SchemaManagerFaultInjectionTest {
    private val temporaryDirectories = ArrayList<Path>()

    @After
    fun tearDown() {
        temporaryDirectories.asReversed().forEach(::deleteRecursively)
    }

    @Test
    fun everyMigrationWriteRollsBackBeforePublishingUserVersion() {
        val baselinePath = installV1Fixture("baseline")
        val baselineDriver = FaultInjectingSqlDriver(baselinePath.toString())
        openDatabase(baselineDriver).use { database ->
            runBlocking {
                manager().initialize(database)
            }
        }

        assertTrue("migration must execute multiple writes", baselineDriver.actions.size > 50)
        assertEquals(
            "pragma:user_version=34",
            baselineDriver.actions.last(),
        )

        for (failureIndex in 1..baselineDriver.actions.size) {
            val path = installV1Fixture("failure-$failureIndex")
            val driver = FaultInjectingSqlDriver(
                path = path.toString(),
                failAt = failureIndex,
            )
            openDatabase(driver).use { database ->
                val failure = assertThrows(InjectedMigrationFailure::class.java) {
                    runBlocking {
                        manager().initialize(database)
                    }
                }
                assertEquals(failureIndex, failure.actionIndex)
            }

            assertV1DatabaseIntact(path, failureIndex)
        }
    }

    private fun assertV1DatabaseIntact(
        path: Path,
        failureIndex: Int,
    ) {
        BundledTestSqlDriver(path.toString()).use { driver ->
            driver.openConnection(SqlConnectionMode.READ_WRITE).use { connection ->
                assertEquals(
                    "failure $failureIndex user_version",
                    1L,
                    connection.pragmas.readLong(SqlPragma.USER_VERSION),
                )
                assertFalse(
                    "failure $failureIndex leaked v2 table",
                    tableExists(connection, "kanji_timeline_events"),
                )
                assertEquals(
                    "failure $failureIndex original row",
                    "goal178-v1",
                    scalarText(
                        connection,
                        "SELECT value FROM settings WHERE key='goal178.fixture'",
                    ),
                )
                assertEquals(
                    "failure $failureIndex integrity",
                    "ok",
                    scalarText(connection, "PRAGMA integrity_check"),
                )
            }
        }
    }

    private fun manager(): SchemaManager =
        SchemaManager(
            MigrationContext(
                clock = MigrationClock { FIXED_NOW },
            ),
        )

    private fun openDatabase(driver: SqlDriver): DedicatedWriterSqlDatabase =
        DedicatedWriterSqlDatabase(
            driver = driver,
            configuration = SqlDatabaseConfiguration(
                busyTimeoutMillis = 1_000,
                writerThreadName = "schema-fault-test",
            ),
        )

    private fun installV1Fixture(label: String): Path {
        val directory = Files.createTempDirectory("kani-schema-fault-$label-")
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

    private fun tableExists(
        connection: SqlConnection,
        table: String,
    ): Boolean =
        connection.prepare(
            "SELECT 1 FROM sqlite_schema WHERE type='table' AND name=?",
        ).use { statement ->
            statement.bindText(1, table)
            statement.query().use(SqlRows::next)
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

    private companion object {
        const val FIXED_NOW = 179_000L
    }
}

private class FaultInjectingSqlDriver(
    private val path: String,
    failAt: Int? = null,
) : SqlDriver {
    private val delegate = BundledTestSqlDriver(path)
    private val controller = MigrationFaultController(failAt)

    val actions: List<String>
        get() = controller.actions

    override fun openConnection(mode: SqlConnectionMode): SqlConnection =
        FaultInjectingSqlConnection(
            delegate = delegate.openConnection(mode),
            controller = controller,
        )

    override fun close() {
        delegate.close()
    }
}

private class FaultInjectingSqlConnection(
    private val delegate: SqlConnection,
    private val controller: MigrationFaultController,
) : SqlConnection {
    private var inTransaction = false

    override val isOpen: Boolean
        get() = delegate.isOpen

    override val pragmas: SqlPragmaAccess =
        FaultInjectingPragmas(
            delegate = delegate.pragmas,
            controller = controller,
            inTransaction = { inTransaction },
        )

    override fun beginTransaction(mode: SqlTransactionMode) {
        delegate.beginTransaction(mode)
        inTransaction = true
    }

    override fun commitTransaction() {
        delegate.commitTransaction()
        inTransaction = false
    }

    override fun rollbackTransaction() {
        try {
            delegate.rollbackTransaction()
        } finally {
            inTransaction = false
        }
    }

    override fun interrupt() {
        delegate.interrupt()
    }

    override fun prepare(sql: String): SqlStatement =
        FaultInjectingSqlStatement(
            delegate = delegate.prepare(sql),
            sql = sql,
            controller = controller,
            inTransaction = { inTransaction },
        )

    override fun execute(sql: String) {
        if (inTransaction) {
            controller.beforeAction(sql)
        }
        delegate.execute(sql)
    }

    override fun changes(): Long = delegate.changes()

    override fun lastInsertRowId(): Long = delegate.lastInsertRowId()

    override fun close() {
        delegate.close()
    }
}

private class FaultInjectingPragmas(
    private val delegate: SqlPragmaAccess,
    private val controller: MigrationFaultController,
    private val inTransaction: () -> Boolean,
) : SqlPragmaAccess {
    override fun readLong(pragma: SqlPragma): Long =
        delegate.readLong(pragma)

    override fun readText(pragma: SqlPragma): String =
        delegate.readText(pragma)

    override fun writeLong(pragma: SqlPragma, value: Long) {
        if (inTransaction()) {
            controller.beforeAction("pragma:${pragma.sqlName}=$value")
        }
        delegate.writeLong(pragma, value)
    }

    override fun writeText(pragma: SqlPragma, value: String) {
        if (inTransaction()) {
            controller.beforeAction("pragma:${pragma.sqlName}=$value")
        }
        delegate.writeText(pragma, value)
    }
}

private class FaultInjectingSqlStatement(
    private val delegate: SqlStatement,
    private val sql: String,
    private val controller: MigrationFaultController,
    private val inTransaction: () -> Boolean,
) : SqlStatement by delegate {
    override fun execute() {
        if (inTransaction()) {
            controller.beforeAction(sql)
        }
        delegate.execute()
    }

    override fun query(): SqlRows {
        if (inTransaction()) {
            controller.beforeAction(sql)
        }
        return delegate.query()
    }
}

private class MigrationFaultController(
    private val failAt: Int?,
) {
    private val recordedActions = ArrayList<String>()

    val actions: List<String>
        get() = recordedActions.toList()

    fun beforeAction(action: String) {
        recordedActions += action
        val actionIndex = recordedActions.size
        if (actionIndex == failAt) {
            throw InjectedMigrationFailure(actionIndex, action)
        }
    }
}

private class InjectedMigrationFailure(
    val actionIndex: Int,
    action: String,
) : RuntimeException("Injected failure at migration action $actionIndex: $action")
