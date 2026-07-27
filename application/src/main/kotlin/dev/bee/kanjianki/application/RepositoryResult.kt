package dev.bee.kanjianki.application

import dev.bee.kanjianki.data.StoreResult

enum class RepositoryFailureKind {
    TRANSIENT,
    PERMANENT,
}

class RepositoryOperationException(
    val operation: String,
    val kind: RepositoryFailureKind,
    cause: Exception,
) : RuntimeException("$operation failed (${kind.name.lowercase()})", cause)

internal fun <T> StoreResult<T>.valueOrThrow(operation: String): T = when (this) {
    is StoreResult.Ok -> value
    is StoreResult.TransientError -> throw RepositoryOperationException(
        operation,
        RepositoryFailureKind.TRANSIENT,
        cause,
    )
    is StoreResult.PermanentError -> throw RepositoryOperationException(
        operation,
        RepositoryFailureKind.PERMANENT,
        cause,
    )
}
