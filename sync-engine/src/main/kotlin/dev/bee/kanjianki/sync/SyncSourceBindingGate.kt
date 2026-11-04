package dev.bee.kanjianki.sync

import dev.bee.kanjianki.data.StoredSyncState
import dev.bee.kanjianki.syncapi.CollectionFailure
import dev.bee.kanjianki.syncapi.CollectionFailureKind
import dev.bee.kanjianki.syncapi.ProviderCollectionSnapshot
import dev.bee.kanjianki.syncapi.RedactedSourceIdentityEvidence
import dev.bee.kanjianki.syncapi.SourceBindingReason

fun interface SyncSourceBindingGate {
    @Throws(SourceBindingFailure::class)
    fun requireAccess(
        provider: ProviderCollectionSnapshot,
        storedState: StoredSyncState,
        nowMillis: Long,
    )

    companion object {
        @JvmField
        val ALLOW_ALL = SyncSourceBindingGate { _, _, _ -> }
    }
}

class SourceBindingFailure(
    @JvmField val reason: SourceBindingReason,
    message: String,
    @JvmField val evidence: SourceBindingEvidence? = null,
    cause: Throwable? = null,
) : CollectionFailure(
    kind = CollectionFailureKind.INVALID_CONFIGURATION,
    message = message,
    retryable = false,
    cause = cause,
)

data class SourceBindingEvidence(
    val candidate: RedactedSourceIdentityEvidence,
    val priorNoteSampleSize: Int,
    val priorCardSampleSize: Int,
) {
    init {
        require(priorNoteSampleSize >= 0) { "prior note sample size must not be negative" }
        require(priorCardSampleSize >= 0) { "prior card sample size must not be negative" }
    }
}
