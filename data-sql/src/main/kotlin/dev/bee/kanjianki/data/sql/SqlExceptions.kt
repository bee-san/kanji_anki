package dev.bee.kanjianki.data.sql

open class SqlException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class SqlBusyException(
    message: String,
    cause: Throwable? = null,
) : SqlException(message, cause)

class SqlConnectionClosedException(
    message: String,
    cause: Throwable? = null,
) : SqlException(message, cause)

class SqlConnectionLostException(
    message: String,
    cause: Throwable? = null,
) : SqlException(message, cause)

class SqlWriterUnavailableException(
    message: String,
    cause: Throwable? = null,
) : SqlException(message, cause)

class SqlReentrancyException(message: String) : SqlException(message)

class SqlConstraintException(
    val kind: SqlConstraintKind,
    message: String,
    cause: Throwable? = null,
) : SqlException(message, cause)

enum class SqlConstraintKind {
    PRIMARY_KEY,
    UNIQUE,
    NOT_NULL,
    FOREIGN_KEY,
    CHECK,
    UNKNOWN,
}
