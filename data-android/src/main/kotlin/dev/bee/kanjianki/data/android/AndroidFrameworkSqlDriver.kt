package dev.bee.kanjianki.data.android

import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteProgram
import android.database.sqlite.SQLiteTableLockedException
import android.os.Build
import android.os.CancellationSignal
import android.os.OperationCanceledException
import androidx.annotation.RequiresApi
import dev.bee.kanjianki.data.sql.SqlBusyException
import dev.bee.kanjianki.data.sql.SqlConnection
import dev.bee.kanjianki.data.sql.SqlConnectionClosedException
import dev.bee.kanjianki.data.sql.SqlConnectionMode
import dev.bee.kanjianki.data.sql.SqlConstraintException
import dev.bee.kanjianki.data.sql.SqlConstraintKind
import dev.bee.kanjianki.data.sql.SqlDriver
import dev.bee.kanjianki.data.sql.SqlException
import dev.bee.kanjianki.data.sql.SqlPragma
import dev.bee.kanjianki.data.sql.SqlPragmaAccess
import dev.bee.kanjianki.data.sql.SqlRow
import dev.bee.kanjianki.data.sql.SqlRows
import dev.bee.kanjianki.data.sql.SqlStatement
import dev.bee.kanjianki.data.sql.SqlTransactionMode
import dev.bee.kanjianki.data.sql.SqlValueType
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class AndroidFrameworkSqlDriver(
    private val path: String,
) : SqlDriver {
    private val closed = AtomicBoolean()

    override fun openConnection(mode: SqlConnectionMode): SqlConnection {
        if (closed.get()) {
            throw SqlConnectionClosedException("Android SQLite driver is closed")
        }
        return translateAndroidSqlFailure("open SQLite connection") {
            AndroidFrameworkSqlConnection(
                SQLiteDatabase.openDatabase(
                    path,
                    null,
                    when (mode) {
                        SqlConnectionMode.READ_WRITE ->
                            SQLiteDatabase.OPEN_READWRITE or
                                SQLiteDatabase.CREATE_IF_NECESSARY
                        SqlConnectionMode.READ_ONLY -> SQLiteDatabase.OPEN_READONLY
                    },
                ),
                mode,
            )
        }
    }

    override fun close() {
        closed.set(true)
    }
}

private class AndroidFrameworkSqlConnection(
    private val database: SQLiteDatabase,
    private val mode: SqlConnectionMode,
) : SqlConnection {
    private val closed = AtomicBoolean()
    private val activeCancellations = ConcurrentHashMap.newKeySet<CancellationSignal>()
    private var transactionControl = TransactionControl.NONE

    override val isOpen: Boolean
        get() = !closed.get() && database.isOpen

    override val pragmas: SqlPragmaAccess = AndroidFrameworkPragmas(this, database)

    override fun beginTransaction(mode: SqlTransactionMode) {
        checkOpen()
        if (transactionControl != TransactionControl.NONE) {
            throw SqlException("Android SQLite connection already has an active transaction")
        }
        translateAndroidSqlFailure("begin $mode transaction", ::isPrimaryKeyCollision) {
            when (mode) {
                SqlTransactionMode.IMMEDIATE -> {
                    requireConnectionMode(SqlConnectionMode.READ_WRITE, mode)
                    database.beginTransactionNonExclusive()
                    transactionControl = TransactionControl.FRAMEWORK
                }
                SqlTransactionMode.DEFERRED -> {
                    requireConnectionMode(SqlConnectionMode.READ_ONLY, mode)
                    transactionControl = beginDeferredTransaction(database)
                }
            }
        }
    }

    override fun commitTransaction() {
        checkOpen()
        translateAndroidSqlFailure("commit transaction", ::isPrimaryKeyCollision) {
            when (transactionControl) {
                TransactionControl.FRAMEWORK -> {
                    database.setTransactionSuccessful()
                    database.endTransaction()
                }
                TransactionControl.QUERY_SQL ->
                    executeReadOnlyTransactionSql(
                        database,
                        "/* kani read transaction */ COMMIT",
                    )
                TransactionControl.NONE ->
                    throw SqlException("Android SQLite connection has no active transaction")
            }
            transactionControl = TransactionControl.NONE
        }
    }

    override fun rollbackTransaction() {
        checkOpen()
        translateAndroidSqlFailure("rollback transaction", ::isPrimaryKeyCollision) {
            try {
                when (transactionControl) {
                    TransactionControl.FRAMEWORK -> database.endTransaction()
                    TransactionControl.QUERY_SQL ->
                        executeReadOnlyTransactionSql(
                            database,
                            "/* kani read transaction */ ROLLBACK",
                        )
                    TransactionControl.NONE ->
                        throw SqlException("Android SQLite connection has no active transaction")
                }
            } finally {
                transactionControl = TransactionControl.NONE
            }
        }
    }

    override fun interrupt() {
        activeCancellations.forEach(CancellationSignal::cancel)
    }

    override fun prepare(sql: String): SqlStatement {
        checkOpen()
        return AndroidFrameworkSqlStatement(
            database = database,
            sql = sql,
            activeCancellations = activeCancellations,
            primaryKeyCollision = ::isPrimaryKeyCollision,
        )
    }

    override fun execute(sql: String) {
        checkOpen()
        translateAndroidSqlFailure("execute statement", ::isPrimaryKeyCollision) {
            database.execSQL(sql)
        }
    }

    override fun changes(): Long = scalarLong("SELECT changes()")

    override fun lastInsertRowId(): Long = scalarLong("SELECT last_insert_rowid()")

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            activeCancellations.forEach(CancellationSignal::cancel)
            translateAndroidSqlFailure("close SQLite connection") {
                database.close()
            }
        }
    }

    internal fun scalarText(sql: String): String =
        prepare(sql).use { statement ->
            statement.query().use { rows ->
                check(rows.next()) { "SQLite scalar query returned no row: $sql" }
                rows.row.text(0)
            }
        }

    private fun scalarLong(sql: String): Long =
        prepare(sql).use { statement ->
            statement.query().use { rows ->
                check(rows.next()) { "SQLite scalar query returned no row: $sql" }
                rows.row.long(0)
            }
        }

    private fun isPrimaryKeyCollision(message: String): Boolean {
        val columns = UNIQUE_COLUMNS.find(message)
            ?.groupValues
            ?.get(1)
            ?.split(',')
            ?.map(String::trim)
            .orEmpty()
        if (columns.isEmpty()) {
            return false
        }
        val qualified = columns.mapNotNull { column ->
            val separator = column.lastIndexOf('.')
            if (separator <= 0 || separator == column.lastIndex) {
                null
            } else {
                column.substring(0, separator) to column.substring(separator + 1)
            }
        }
        if (qualified.size != columns.size) {
            return false
        }
        val table = qualified.first().first
        if (qualified.any { it.first != table }) {
            return false
        }
        return try {
            val primaryKey = LinkedHashSet<String>()
            database.rawQuery(
                "PRAGMA table_info(${quoteSqlString(table)})",
                null,
            ).use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val primaryKeyIndex = cursor.getColumnIndexOrThrow("pk")
                while (cursor.moveToNext()) {
                    if (cursor.getInt(primaryKeyIndex) > 0) {
                        primaryKey += cursor.getString(nameIndex)
                    }
                }
            }
            primaryKey.isNotEmpty() &&
                primaryKey == qualified.mapTo(LinkedHashSet()) { it.second }
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun checkOpen() {
        if (!isOpen) {
            throw SqlConnectionClosedException("Android SQLite connection is closed")
        }
    }

    private fun requireConnectionMode(
        required: SqlConnectionMode,
        transactionMode: SqlTransactionMode,
    ) {
        if (mode != required) {
            throw SqlException(
                "$transactionMode transactions require a $required Android SQLite connection",
            )
        }
    }

    private companion object {
        val UNIQUE_COLUMNS =
            Regex("""unique constraint failed:\s*([^\r\n(]+)""", RegexOption.IGNORE_CASE)
    }
}

private class AndroidFrameworkPragmas(
    private val connection: AndroidFrameworkSqlConnection,
    private val database: SQLiteDatabase,
) : SqlPragmaAccess {
    override fun readLong(pragma: SqlPragma): Long =
        connection.prepare("PRAGMA ${pragma.sqlName}").use { statement ->
            statement.query().use { rows ->
                check(rows.next()) { "PRAGMA ${pragma.sqlName} returned no row" }
                rows.row.long(0)
            }
        }

    override fun readText(pragma: SqlPragma): String =
        connection.scalarText("PRAGMA ${pragma.sqlName}")

    override fun writeLong(pragma: SqlPragma, value: Long) {
        translateAndroidSqlFailure("write PRAGMA ${pragma.sqlName}") {
            when (pragma) {
                SqlPragma.FOREIGN_KEYS ->
                    database.setForeignKeyConstraintsEnabled(value != 0L)
                SqlPragma.USER_VERSION -> database.version = value.toInt()
                else -> executePragma("PRAGMA ${pragma.sqlName} = $value")
            }
        }
    }

    override fun writeText(pragma: SqlPragma, value: String) {
        translateAndroidSqlFailure("write PRAGMA ${pragma.sqlName}") {
            if (pragma == SqlPragma.JOURNAL_MODE && value.equals("WAL", ignoreCase = true)) {
                if (!database.enableWriteAheadLogging() && readText(pragma) != "wal") {
                    throw SqlException("Android SQLite could not enable WAL mode")
                }
            } else {
                executePragma(
                    "PRAGMA ${pragma.sqlName} = ${quoteSqlString(value)}",
                )
            }
        }
    }

    private fun executePragma(sql: String) {
        connection.prepare(sql).use { statement ->
            statement.query().use { rows ->
                while (rows.next()) {
                    // Assignment-form PRAGMAs may return their effective value.
                }
            }
        }
    }
}

private class AndroidFrameworkSqlStatement(
    private val database: SQLiteDatabase,
    private val sql: String,
    private val activeCancellations: MutableSet<CancellationSignal>,
    private val primaryKeyCollision: (String) -> Boolean,
) : SqlStatement {
    private val closed = AtomicBoolean()
    private val bindings = LinkedHashMap<Int, AndroidSqlBinding>()

    override fun bindNull(index: Int) {
        bind(index, AndroidSqlBinding.Null)
    }

    override fun bindText(index: Int, value: String) {
        bind(index, AndroidSqlBinding.Text(value))
    }

    override fun bindLong(index: Int, value: Long) {
        bind(index, AndroidSqlBinding.Integer(value))
    }

    override fun bindDouble(index: Int, value: Double) {
        bind(index, AndroidSqlBinding.Real(value))
    }

    override fun bindBlob(index: Int, value: ByteArray) {
        bind(index, AndroidSqlBinding.Blob(value.copyOf()))
    }

    override fun execute() {
        checkOpen()
        translateAndroidSqlFailure("execute statement", primaryKeyCollision) {
            database.compileStatement(sql).use { statement ->
                bindings.forEach { (index, value) -> value.apply(statement, index) }
                statement.execute()
            }
        }
    }

    override fun query(): SqlRows {
        checkOpen()
        val cancellationSignal = CancellationSignal()
        activeCancellations.add(cancellationSignal)
        return try {
            val cursor = translateAndroidSqlFailure("query statement", primaryKeyCollision) {
                database.rawQueryWithFactory(
                    { _, driver, editTable, query ->
                        bindings.forEach { (index, value) -> value.apply(query, index) }
                        SQLiteCursor(driver, editTable, query)
                    },
                    sql,
                    EMPTY_STRING_ARGUMENTS,
                    "",
                    cancellationSignal,
                )
            }
            AndroidFrameworkSqlRows(
                cursor = cursor,
                onClose = {
                    activeCancellations.remove(cancellationSignal)
                },
                primaryKeyCollision = primaryKeyCollision,
            )
        } catch (failure: Throwable) {
            activeCancellations.remove(cancellationSignal)
            throw failure
        }
    }

    override fun reset() {
        checkOpen()
    }

    override fun clearBindings() {
        checkOpen()
        bindings.clear()
    }

    override fun close() {
        closed.set(true)
        bindings.clear()
    }

    private fun bind(
        index: Int,
        value: AndroidSqlBinding,
    ) {
        checkOpen()
        if (index < 1) {
            throw SqlException("SQLite bind indices are one-based")
        }
        bindings[index] = value
    }

    private fun checkOpen() {
        if (closed.get()) {
            throw SqlConnectionClosedException("Android SQLite statement is closed")
        }
    }

    private companion object {
        val EMPTY_STRING_ARGUMENTS = emptyArray<String>()
    }
}

private class AndroidFrameworkSqlRows(
    private val cursor: Cursor,
    private val onClose: () -> Unit,
    private val primaryKeyCollision: (String) -> Boolean,
) : SqlRows {
    private var closed = false
    private var positioned = false

    override val row: SqlRow = AndroidFrameworkSqlRow(cursor) {
        check(!closed && positioned) { "Android SQLite row is not positioned" }
    }

    override fun next(): Boolean {
        check(!closed) { "Android SQLite rows are closed" }
        positioned = translateAndroidSqlFailure("step query", primaryKeyCollision) {
            cursor.moveToNext()
        }
        return positioned
    }

    override fun close() {
        if (!closed) {
            closed = true
            positioned = false
            try {
                cursor.close()
            } finally {
                onClose()
            }
        }
    }
}

private class AndroidFrameworkSqlRow(
    private val cursor: Cursor,
    private val checkPositioned: () -> Unit,
) : SqlRow {
    override val columnCount: Int
        get() = value { cursor.columnCount }

    override fun columnName(index: Int): String =
        value { cursor.getColumnName(index) }

    override fun valueType(index: Int): SqlValueType =
        when (value { cursor.getType(index) }) {
            Cursor.FIELD_TYPE_NULL -> SqlValueType.NULL
            Cursor.FIELD_TYPE_INTEGER -> SqlValueType.INTEGER
            Cursor.FIELD_TYPE_FLOAT -> SqlValueType.REAL
            Cursor.FIELD_TYPE_STRING -> SqlValueType.TEXT
            Cursor.FIELD_TYPE_BLOB -> SqlValueType.BLOB
            else -> error("Unknown Android SQLite column type at index $index")
        }

    override fun isNull(index: Int): Boolean =
        value { cursor.isNull(index) }

    override fun text(index: Int): String =
        value { cursor.getString(index) }

    override fun long(index: Int): Long =
        value { cursor.getLong(index) }

    override fun double(index: Int): Double =
        value { cursor.getDouble(index) }

    override fun blob(index: Int): ByteArray =
        value { cursor.getBlob(index) }

    private inline fun <T> value(block: () -> T): T {
        checkPositioned()
        return block()
    }
}

private sealed interface AndroidSqlBinding {
    fun apply(program: SQLiteProgram, index: Int)

    data object Null : AndroidSqlBinding {
        override fun apply(program: SQLiteProgram, index: Int) {
            program.bindNull(index)
        }
    }

    data class Text(val value: String) : AndroidSqlBinding {
        override fun apply(program: SQLiteProgram, index: Int) {
            program.bindString(index, value)
        }
    }

    data class Integer(val value: Long) : AndroidSqlBinding {
        override fun apply(program: SQLiteProgram, index: Int) {
            program.bindLong(index, value)
        }
    }

    data class Real(val value: Double) : AndroidSqlBinding {
        override fun apply(program: SQLiteProgram, index: Int) {
            program.bindDouble(index, value)
        }
    }

    data class Blob(val value: ByteArray) : AndroidSqlBinding {
        override fun apply(program: SQLiteProgram, index: Int) {
            program.bindBlob(index, value)
        }
    }
}

private enum class TransactionControl {
    NONE,
    FRAMEWORK,
    QUERY_SQL,
}

private fun beginDeferredTransaction(database: SQLiteDatabase): TransactionControl {
    if (Build.VERSION.SDK_INT >= 35) {
        Api35Transactions.beginReadOnly(database)
        return TransactionControl.FRAMEWORK
    }
    // Before API 35, Android rewrites a leading BEGIN into BEGIN EXCLUSIVE.
    // The comment keeps the public query path on a read-only pooled connection
    // while SQLite itself still parses the requested deferred transaction.
    executeReadOnlyTransactionSql(
        database,
        "/* kani read transaction */ BEGIN DEFERRED",
    )
    return TransactionControl.QUERY_SQL
}

private fun executeReadOnlyTransactionSql(
    database: SQLiteDatabase,
    sql: String,
) {
    database.rawQuery(sql, null).use { cursor ->
        while (cursor.moveToNext()) {
            // Transaction-control statements return no rows.
        }
    }
}

@RequiresApi(35)
private object Api35Transactions {
    fun beginReadOnly(database: SQLiteDatabase) {
        database.beginTransactionReadOnly()
    }
}

private inline fun <T> translateAndroidSqlFailure(
    operation: String,
    noinline primaryKeyCollision: (String) -> Boolean = { false },
    block: () -> T,
): T {
    try {
        return block()
    } catch (failure: SqlException) {
        throw failure
    } catch (failure: OperationCanceledException) {
        throw CancellationException("$operation was cancelled").apply {
            initCause(failure)
        }
    } catch (failure: SQLiteDatabaseLockedException) {
        throw SqlBusyException("$operation failed: ${failure.message.orEmpty()}", failure)
    } catch (failure: SQLiteTableLockedException) {
        throw SqlBusyException("$operation failed: ${failure.message.orEmpty()}", failure)
    } catch (failure: SQLiteConstraintException) {
        val message = failure.message.orEmpty()
        throw SqlConstraintException(
            kind = constraintKind(message, primaryKeyCollision),
            message = "$operation failed: $message",
            cause = failure,
        )
    } catch (failure: android.database.sqlite.SQLiteException) {
        val message = failure.message.orEmpty()
        val lower = message.lowercase()
        val detail = "$operation failed: $message"
        when {
            "database is locked" in lower || "database is busy" in lower ->
                throw SqlBusyException(detail, failure)
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

private fun constraintKind(
    message: String,
    primaryKeyCollision: (String) -> Boolean,
): SqlConstraintKind {
    val lower = message.lowercase()
    return when {
        sqliteResultCode(message) == SQLITE_CONSTRAINT_PRIMARY_KEY ||
            "primary key constraint failed" in lower ||
            primaryKeyCollision(message) -> SqlConstraintKind.PRIMARY_KEY
        sqliteResultCode(message) == SQLITE_CONSTRAINT_UNIQUE ||
            "unique constraint failed" in lower -> SqlConstraintKind.UNIQUE
        sqliteResultCode(message) == SQLITE_CONSTRAINT_NOT_NULL ||
            "not null constraint failed" in lower -> SqlConstraintKind.NOT_NULL
        sqliteResultCode(message) == SQLITE_CONSTRAINT_FOREIGN_KEY ||
            "foreign key constraint failed" in lower -> SqlConstraintKind.FOREIGN_KEY
        sqliteResultCode(message) == SQLITE_CONSTRAINT_CHECK ||
            "check constraint failed" in lower -> SqlConstraintKind.CHECK
        else -> SqlConstraintKind.UNKNOWN
    }
}

private fun sqliteResultCode(message: String): Int? =
    Regex("""(?:error|result)?\s*code\s*[:=]?\s*(\d+)""", RegexOption.IGNORE_CASE)
        .find(message)
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()

private fun quoteSqlString(value: String): String =
    "'" + value.replace("'", "''") + "'"

private const val SQLITE_CONSTRAINT_CHECK = 275
private const val SQLITE_CONSTRAINT_FOREIGN_KEY = 787
private const val SQLITE_CONSTRAINT_NOT_NULL = 1299
private const val SQLITE_CONSTRAINT_PRIMARY_KEY = 1555
private const val SQLITE_CONSTRAINT_UNIQUE = 2067
