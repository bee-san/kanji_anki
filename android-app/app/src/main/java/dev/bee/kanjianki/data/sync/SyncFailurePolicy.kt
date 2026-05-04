package dev.bee.kanjianki.data.sync

sealed class CollectionSyncException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class PermanentCollectionSyncException(
    message: String,
    cause: Throwable? = null,
) : CollectionSyncException(message, cause)

class TransientCollectionSyncException(
    message: String,
    cause: Throwable? = null,
) : CollectionSyncException(message, cause)

fun Throwable.isPermanentCollectionSyncFailure(): Boolean =
    generateSequence(this) { it.cause }
        .any { it is PermanentCollectionSyncException }
