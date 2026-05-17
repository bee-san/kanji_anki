package dev.bee.kanjianki.domain.sync

import dev.bee.kanjianki.domain.model.importing.ImportSettings
import dev.bee.kanjianki.domain.model.source.SourceCard
import dev.bee.kanjianki.domain.model.source.SourceNote
import dev.bee.kanjianki.domain.model.sync.SyncErrorCode

interface CollectionGateway {
    @Throws(CollectionGatewayException::class)
    suspend fun readCollection(settings: ImportSettings): CollectionSnapshot
}

data class CollectionSnapshot(
    val notes: List<SourceNote>,
    val cards: List<SourceCard>,
)

class CollectionGatewayException(
    val errorCode: SyncErrorCode,
    val permanent: Boolean,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
