package dev.bee.kanjianki.syncapi

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Collections

enum class CollectionProviderKind {
    ANKIDROID,
    ANKI_CONNECT,
    TEST,
}

/**
 * Transient provider identity evidence. Raw source keys and stable IDs are
 * intentionally private and never appear in diagnostics or persistence DTOs.
 */
class CollectionSourceIdentity private constructor(
    private val providerKind: CollectionProviderKind,
    private val sourceKey: String,
    stableNoteIds: Collection<Long>,
    stableCardIds: Collection<Long>,
) {
    private val noteIds: List<Long> = stableSample(stableNoteIds)
    private val cardIds: List<Long> = stableSample(stableCardIds)

    internal fun opaqueEvidence(bindingSalt: String): OpaqueSourceEvidence {
        require(bindingSalt.isNotBlank()) { "binding salt must not be blank" }
        return OpaqueSourceEvidence(
            providerKindDigest = digest(bindingSalt, "provider-kind", providerKind.name),
            sourceKeyDigest = digest(bindingSalt, "source-key", sourceKey),
            noteIdDigests = noteIds.map { digest(bindingSalt, "note-id", it.toULong().toString()) },
            cardIdDigests = cardIds.map { digest(bindingSalt, "card-id", it.toULong().toString()) },
        )
    }

    internal fun hasStableIds(): Boolean = noteIds.isNotEmpty() || cardIds.isNotEmpty()

    /**
     * Reuses transient provider metadata with a different read-only ID sample.
     * This lets compatibility migration compare a committed mirror with a live
     * candidate without exposing or persisting the raw provider/source key.
     */
    fun withStableIds(
        stableNoteIds: Collection<Long>,
        stableCardIds: Collection<Long>,
    ): CollectionSourceIdentity =
        CollectionSourceIdentity(
            providerKind,
            sourceKey,
            stableNoteIds,
            stableCardIds,
        )

    override fun toString(): String =
        "CollectionSourceIdentity(redacted, noteSample=${noteIds.size}, cardSample=${cardIds.size})"

    companion object {
        const val MAX_IDS_PER_KIND: Int = 64

        @JvmStatic
        fun create(
            providerKind: CollectionProviderKind,
            sourceKey: String,
            stableNoteIds: Collection<Long>,
            stableCardIds: Collection<Long>,
        ): CollectionSourceIdentity {
            require(sourceKey.isNotBlank()) { "source key must not be blank" }
            return CollectionSourceIdentity(
                providerKind,
                sourceKey,
                stableNoteIds,
                stableCardIds,
            )
        }

        private fun stableSample(ids: Collection<Long>): List<Long> {
            val sorted = ids.toSet().sortedWith { left, right ->
                java.lang.Long.compareUnsigned(left, right)
            }
            return Collections.unmodifiableList(sorted.take(MAX_IDS_PER_KIND))
        }

        private fun digest(salt: String, domain: String, value: String): String {
            val hash = MessageDigest.getInstance("SHA-256")
            hash.update(salt.toByteArray(StandardCharsets.UTF_8))
            hash.update(0)
            hash.update(domain.toByteArray(StandardCharsets.UTF_8))
            hash.update(0)
            hash.update(value.toByteArray(StandardCharsets.UTF_8))
            return hash.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }
    }
}

internal data class OpaqueSourceEvidence(
    val providerKindDigest: String,
    val sourceKeyDigest: String,
    val noteIdDigests: List<String>,
    val cardIdDigests: List<String>,
)
