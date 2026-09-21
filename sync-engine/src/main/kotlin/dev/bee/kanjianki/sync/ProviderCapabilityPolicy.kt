package dev.bee.kanjianki.sync

import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.syncapi.CollectionCapability
import dev.bee.kanjianki.syncapi.ProviderCollectionSnapshot

/**
 * Applies provider-level capability claims before collection evidence reaches
 * import, analysis, or persistence. Interval, repetition, and lapse evidence
 * remains portable; provider FSRS memory is usable only when explicitly
 * declared for the snapshot.
 */
object ProviderCapabilityPolicy {
    enum class MemoryEvidence {
        PROVIDER_FSRS,
        INTERVAL_LAPSE_FALLBACK,
    }

    data class NormalizedCollection(
        val snapshot: RecordsSyncModels.CollectionSnapshot,
        val memoryEvidence: MemoryEvidence,
    )

    @JvmStatic
    fun normalize(provider: ProviderCollectionSnapshot): NormalizedCollection {
        if (CollectionCapability.FSRS_MEMORY_STATE in provider.capabilities) {
            return NormalizedCollection(provider.snapshot, MemoryEvidence.PROVIDER_FSRS)
        }
        if (provider.snapshot.cards.none(::hasFsrsMemory)) {
            return NormalizedCollection(
                provider.snapshot,
                MemoryEvidence.INTERVAL_LAPSE_FALLBACK,
            )
        }
        return NormalizedCollection(
            RecordsSyncModels.CollectionSnapshot(
                provider.snapshot.notes,
                provider.snapshot.cards.map(::withoutFsrsMemory),
            ),
            MemoryEvidence.INTERVAL_LAPSE_FALLBACK,
        )
    }

    private fun hasFsrsMemory(card: RecordsSyncModels.Card): Boolean =
        card.fsrsStability != null ||
            card.fsrsDifficulty != null ||
            card.fsrsRetrievability != null

    private fun withoutFsrsMemory(card: RecordsSyncModels.Card): RecordsSyncModels.Card =
        RecordsSyncModels.Card(
            card.cardId,
            card.noteId,
            card.ord,
            card.deckId,
            card.deckName,
            card.queue,
            card.type,
            card.due,
            card.intervalDays,
            card.reps,
            card.lapses,
            card.suspended,
            null,
            null,
            null,
        ).withBrowserQueryMatched(card.browserQueryMatched)
}
