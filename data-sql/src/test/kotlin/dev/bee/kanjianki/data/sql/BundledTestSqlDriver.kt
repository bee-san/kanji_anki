package dev.bee.kanjianki.data.sql

import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLITE_DATA_TEXT
import androidx.sqlite.SQLiteConnection as AndroidxConnection
import androidx.sqlite.SQLiteException as AndroidxSqliteException
import androidx.sqlite.SQLiteStatement as AndroidxStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.util.concurrent.atomic.AtomicBoolean

internal class BundledTestSqlDriver(
    private val path: String,
) : SqlDriver {
    private val driver = BundledSQLiteDriver()
    private val closed = AtomicBoolean()

    override fun openConnection(mode: SqlConnectionMode): SqlConnection {
        if (closed.get()) {
            throw SqlConnectionClosedException("Bundled SQLite driver is closed")
        }
        return translateSqlFailure("open SQLite connection") {
            val connection = BundledTestSqlConnection(driver.open(path), mode)
            try {
                if (mode == SqlConnectionMode.READ_ONLY) {
                    connection.execute("PRAGMA query_only = ON")
                }
                connection
            } catch (failure: Throwable) {
                try {
                    connection.close()
                } catch (closeFailure: Throwable) {
                    failure.addSuppressed(closeFailure)
                }
                throw failure
            }
        }
    }

    override fun close() {
        closed.set(true)
    }
}

private class BundledTestSqlConnection(
    private val delegate: AndroidxConnection,
    private val mode: SqlConnectionMode,
) : SqlConnection {
    private val closed = AtomicBoolean()

    override val isOpen: Boolean
        get() = !closed.get()

    override val pragmas: SqlPragmaAccess = BundledPragmaAccess(this)

    override fun beginTransaction(mode: SqlTransactionMode) {
        val requiredMode =
            when (mode) {
                SqlTransactionMode.IMMEDIATE -> SqlConnectionMode.READ_WRITE
                SqlTransactionMode.DEFERRED -> SqlConnectionMode.READ_ONLY
            }
        if (this.mode != requiredMode) {
            throw SqlException(
                "$mode transactions require a $requiredMode bundled SQLite connection",
            )
        }
        execute(
            when (mode) {
                SqlTransactionMode.IMMEDIATE -> "BEGIN IMMEDIATE"
                SqlTransactionMode.DEFERRED -> "BEGIN DEFERRED"
            },
        )
    }

    override fun commitTransaction() {
        execute("COMMIT")
    }

    override fun rollbackTransaction() {
        execute("ROLLBACK")
    }

    override fun interrupt() {
        // AndroidX does not expose sqlite3_interrupt. Closing is its only
        // cooperative cancellation primitive for an in-flight connection.
        close()
    }

    override fun prepare(sql: String): SqlStatement {
        checkOpen()
        return translateSqlFailure("prepare statement") {
            BundledTestSqlStatement(delegate.prepare(sql))
        }
    }

    override fun execute(sql: String) {
        prepare(sql).use { it.execute() }
    }

    override fun changes(): Long = scalarLong("SELECT changes()")

    override fun lastInsertRowId(): Long = scalarLong("SELECT last_insert_rowid()")

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            translateSqlFailure("close SQLite connection") {
                delegate.close()
            }
        }
    }

    private fun scalarLong(sql: String): Long =
        prepare(sql).use { statement ->
            statement.query().use { rows ->
                check(rows.next()) { "SQLite scalar query returned no row: $sql" }
                rows.row.long(0)
            }
        }

    private fun checkOpen() {
        if (!isOpen) {
            throw SqlConnectionClosedException("Bundled SQLite connection is closed")
        }
    }
}

private class BundledPragmaAccess(
    private val connection: SqlConnection,
) : SqlPragmaAccess {
    override fun readLong(pragma: SqlPragma): Long =
        connection.prepare("PRAGMA ${pragma.sqlName}").use { statement ->
            statement.query().use { rows ->
                check(rows.next()) { "PRAGMA ${pragma.sqlName} returned no row" }
                rows.row.long(0)
            }
        }

    override fun readText(pragma: SqlPragma): String =
        connection.prepare("PRAGMA ${pragma.sqlName}").use { statement ->
            statement.query().use { rows ->
                check(rows.next()) { "PRAGMA ${pragma.sqlName} returned no row" }
                rows.row.text(0)
            }
        }

    override fun writeLong(pragma: SqlPragma, value: Long) {
        connection.execute("PRAGMA ${pragma.sqlName} = $value")
    }

    override fun writeText(pragma: SqlPragma, value: String) {
        val quoted = value.replace("'", "''")
        connection.execute("PRAGMA ${pragma.sqlName} = '$quoted'")
    }
}

private class BundledTestSqlStatement(
    private val delegate: AndroidxStatement,
) : SqlStatement {
    private val closed = AtomicBoolean()

    override fun bindNull(index: Int) = call("bind null") { delegate.bindNull(index) }

    override fun bindText(index: Int, value: String) =
        call("bind text") { delegate.bindText(index, value) }

    override fun bindLong(index: Int, value: Long) =
        call("bind integer") { delegate.bindLong(index, value) }

    override fun bindDouble(index: Int, value: Double) =
        call("bind real") { delegate.bindDouble(index, value) }

    override fun bindBlob(index: Int, value: ByteArray) =
        call("bind blob") { delegate.bindBlob(index, value) }

    override fun execute() {
        call("execute statement") {
            while (delegate.step()) {
                // Consume any rows returned by PRAGMA or RETURNING statements.
            }
        }
    }

    override fun query(): SqlRows {
        checkOpen()
        return BundledTestSqlRows(delegate)
    }

    override fun reset() = call("reset statement") { delegate.reset() }

    override fun clearBindings() = call("clear statement bindings") {
        delegate.clearBindings()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            translateSqlFailure("close SQLite statement") {
                delegate.close()
            }
        }
    }

    private inline fun call(operation: String, block: () -> Unit) {
        checkOpen()
        translateSqlFailure(operation, block)
    }

    private fun checkOpen() {
        if (closed.get()) {
            throw SqlConnectionClosedException("Bundled SQLite statement is closed")
        }
    }
}

private class BundledTestSqlRows(
    private val statement: AndroidxStatement,
) : SqlRows {
    private var closed = false
    private var positioned = false

    override val row: SqlRow = BundledTestSqlRow(statement) {
        check(!closed && positioned) { "SQLite row is not positioned" }
    }

    override fun next(): Boolean {
        check(!closed) { "SQLite rows are closed" }
        positioned = translateSqlFailure("step query") { statement.step() }
        return positioned
    }

    override fun close() {
        closed = true
        positioned = false
    }
}

private class BundledTestSqlRow(
    private val statement: AndroidxStatement,
    private val checkPositioned: () -> Unit,
) : SqlRow {
    override val columnCount: Int
        get() = value("read column count") { statement.getColumnCount() }

    override fun columnName(index: Int): String =
        value("read column name") { statement.getColumnName(index) }

    override fun valueType(index: Int): SqlValueType =
        when (value("read column type") { statement.getColumnType(index) }) {
            SQLITE_DATA_NULL -> SqlValueType.NULL
            SQLITE_DATA_INTEGER -> SqlValueType.INTEGER
            SQLITE_DATA_FLOAT -> SqlValueType.REAL
            SQLITE_DATA_TEXT -> SqlValueType.TEXT
            SQLITE_DATA_BLOB -> SqlValueType.BLOB
            else -> error("Unknown SQLite column type at index $index")
        }

    override fun isNull(index: Int): Boolean =
        value("read null column") { statement.isNull(index) }

    override fun text(index: Int): String =
        value("read text column") { statement.getText(index) }

    override fun long(index: Int): Long =
        value("read integer column") { statement.getLong(index) }

    override fun double(index: Int): Double =
        value("read real column") { statement.getDouble(index) }

    override fun blob(index: Int): ByteArray =
        value("read blob column") { statement.getBlob(index) }

    private inline fun <T> value(operation: String, block: () -> T): T {
        checkPositioned()
        return translateSqlFailure(operation, block)
    }
}

private inline fun <T> translateSqlFailure(
    operation: String,
    block: () -> T,
): T {
    try {
        return block()
    } catch (failure: SqlException) {
        throw failure
    } catch (failure: AndroidxSqliteException) {
        val message = failure.message.orEmpty()
        val lower = message.lowercase()
        val detail = "$operation failed: $message"
        val resultCode = sqliteResultCode(message)
        when {
            "database is locked" in lower ||
                "database is busy" in lower ||
                resultCode?.and(0xff) == SQLITE_BUSY -> throw SqlBusyException(detail, failure)
            resultCode == SQLITE_CONSTRAINT_PRIMARY_KEY ->
                throw SqlConstraintException(SqlConstraintKind.PRIMARY_KEY, detail, failure)
            resultCode == SQLITE_CONSTRAINT_UNIQUE ->
                throw SqlConstraintException(SqlConstraintKind.UNIQUE, detail, failure)
            resultCode == SQLITE_CONSTRAINT_NOT_NULL ->
                throw SqlConstraintException(SqlConstraintKind.NOT_NULL, detail, failure)
            resultCode == SQLITE_CONSTRAINT_FOREIGN_KEY ->
                throw SqlConstraintException(SqlConstraintKind.FOREIGN_KEY, detail, failure)
            resultCode == SQLITE_CONSTRAINT_CHECK ->
                throw SqlConstraintException(SqlConstraintKind.CHECK, detail, failure)
            "not null constraint failed" in lower ->
                throw SqlConstraintException(SqlConstraintKind.NOT_NULL, detail, failure)
            "foreign key constraint failed" in lower ->
                throw SqlConstraintException(SqlConstraintKind.FOREIGN_KEY, detail, failure)
            "check constraint failed" in lower ->
                throw SqlConstraintException(SqlConstraintKind.CHECK, detail, failure)
            "unique constraint failed" in lower ->
                throw SqlConstraintException(
                    if ("primary key" in lower) {
                        SqlConstraintKind.PRIMARY_KEY
                    } else {
                        SqlConstraintKind.UNIQUE
                    },
                    detail,
                    failure,
                )
            "constraint failed" in lower ->
                throw SqlConstraintException(SqlConstraintKind.UNKNOWN, detail, failure)
            "closed" in lower -> throw SqlConnectionClosedException(detail, failure)
            else -> throw SqlException(detail, failure)
        }
    } catch (failure: IllegalStateException) {
        if ("closed" in failure.message.orEmpty().lowercase()) {
            throw SqlConnectionClosedException("$operation failed", failure)
        }
        throw failure
    }
}

private fun sqliteResultCode(message: String): Int? =
    Regex("""(?:error|result) code:\s*(\d+)""", RegexOption.IGNORE_CASE)
        .find(message)
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()

private const val SQLITE_BUSY = 5
private const val SQLITE_CONSTRAINT_CHECK = 275
private const val SQLITE_CONSTRAINT_FOREIGN_KEY = 787
private const val SQLITE_CONSTRAINT_NOT_NULL = 1299
private const val SQLITE_CONSTRAINT_PRIMARY_KEY = 1555
private const val SQLITE_CONSTRAINT_UNIQUE = 2067
