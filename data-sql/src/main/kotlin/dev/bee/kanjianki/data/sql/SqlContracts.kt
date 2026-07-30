package dev.bee.kanjianki.data.sql

/**
 * Opens independent physical SQLite connections. A connection is never safe
 * for concurrent use unless its adapter explicitly serializes that use.
 */
interface SqlDriver : AutoCloseable {
    fun openConnection(): SqlConnection

    override fun close()
}

/** A single physical connection and all state scoped to it. */
interface SqlConnection : SqlSession, AutoCloseable {
    val isOpen: Boolean

    fun beginTransaction(mode: SqlTransactionMode)

    fun commitTransaction()

    fun rollbackTransaction()

    /** Interrupts work currently executing on this connection, when supported. */
    fun interrupt()

    override fun close()
}

/** SQL operations which must stay on their owning physical connection. */
interface SqlSession {
    val pragmas: SqlPragmaAccess

    fun prepare(sql: String): SqlStatement

    fun execute(sql: String)

    fun changes(): Long

    fun lastInsertRowId(): Long
}

/**
 * A prepared statement. Bind parameters are one-based; result columns exposed
 * by [SqlRow] are zero-based.
 */
interface SqlStatement : AutoCloseable {
    fun bindNull(index: Int)

    fun bindText(index: Int, value: String)

    fun bindLong(index: Int, value: Long)

    fun bindDouble(index: Int, value: Double)

    fun bindBlob(index: Int, value: ByteArray)

    fun execute()

    fun query(): SqlRows

    fun reset()

    fun clearBindings()

    override fun close()
}

/** A forward-only result resource. */
interface SqlRows : AutoCloseable {
    val row: SqlRow

    fun next(): Boolean

    override fun close()
}

/** Values from the current row, addressed with zero-based column indices. */
interface SqlRow {
    val columnCount: Int

    fun columnName(index: Int): String

    fun valueType(index: Int): SqlValueType

    fun isNull(index: Int): Boolean

    fun text(index: Int): String

    fun long(index: Int): Long

    fun double(index: Int): Double

    fun blob(index: Int): ByteArray
}

enum class SqlValueType {
    NULL,
    INTEGER,
    REAL,
    TEXT,
    BLOB,
}

enum class SqlTransactionMode {
    IMMEDIATE,
    DEFERRED,
}

enum class SqlPragma(val sqlName: String) {
    BUSY_TIMEOUT("busy_timeout"),
    FOREIGN_KEYS("foreign_keys"),
    JOURNAL_MODE("journal_mode"),
    SYNCHRONOUS("synchronous"),
    USER_VERSION("user_version"),
}

interface SqlPragmaAccess {
    fun readLong(pragma: SqlPragma): Long

    fun readText(pragma: SqlPragma): String

    fun writeLong(pragma: SqlPragma, value: Long)

    fun writeText(pragma: SqlPragma, value: String)
}

/**
 * The suspendable outer boundary. Transaction callbacks are deliberately
 * non-suspending so no network, provider, or progress callback can suspend
 * while holding the database transaction.
 */
interface SqlDatabase : AutoCloseable {
    suspend fun <T> write(block: SqlTransactionScope.() -> T): T

    suspend fun <T> readSnapshot(block: SqlReadScope.() -> T): T

    override fun close()
}

interface SqlReadScope : SqlSession

interface SqlTransactionScope : SqlReadScope {
    fun <T> savepoint(block: SqlTransactionScope.() -> T): T
}
