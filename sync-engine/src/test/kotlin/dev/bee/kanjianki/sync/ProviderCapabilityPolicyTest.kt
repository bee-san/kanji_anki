package dev.bee.kanjianki.sync

import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.syncapi.CollectionCapability
import dev.bee.kanjianki.syncapi.ProviderCollectionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCapabilityPolicyTest {
    @Test
    fun declaredFsrsCapabilityPreservesNullableProviderMemory() {
        val snapshot = snapshot(card(fsrs = arrayOf(12.5, null, 0.82)))

        val normalized = ProviderCapabilityPolicy.normalize(
            ProviderCollectionSnapshot(
                snapshot,
                setOf(CollectionCapability.READ_COLLECTION, CollectionCapability.FSRS_MEMORY_STATE),
                null,
            ),
        )

        assertSame(snapshot, normalized.snapshot)
        assertEquals(
            ProviderCapabilityPolicy.MemoryEvidence.PROVIDER_FSRS,
            normalized.memoryEvidence,
        )
        assertEquals(12.5, normalized.snapshot.cards.single().fsrsStability!!, 0.0)
        assertNull(normalized.snapshot.cards.single().fsrsDifficulty)
        assertEquals(0.82, normalized.snapshot.cards.single().fsrsRetrievability!!, 0.0)
    }

    @Test
    fun absentFsrsCapabilityStripsMemoryWithoutFabricatingPortableEvidence() {
        val original = card(fsrs = arrayOf(91.0, 9.5, 0.12))
            .withBrowserQueryMatched(true)

        val normalized = ProviderCapabilityPolicy.normalize(
            ProviderCollectionSnapshot(
                snapshot(original),
                setOf(CollectionCapability.READ_COLLECTION),
                null,
            ),
        )

        assertEquals(
            ProviderCapabilityPolicy.MemoryEvidence.INTERVAL_LAPSE_FALLBACK,
            normalized.memoryEvidence,
        )
        val card = normalized.snapshot.cards.single()
        assertEquals(original.cardId, card.cardId)
        assertEquals(original.noteId, card.noteId)
        assertEquals(original.ord, card.ord)
        assertEquals(original.deckId, card.deckId)
        assertEquals(original.deckName, card.deckName)
        assertEquals(original.queue, card.queue)
        assertEquals(original.type, card.type)
        assertEquals(original.due, card.due)
        assertEquals(30, card.intervalDays)
        assertEquals(10, card.reps)
        assertEquals(3, card.lapses)
        assertFalse(card.suspended)
        assertTrue(card.browserQueryMatched)
        assertNull(card.fsrsStability)
        assertNull(card.fsrsDifficulty)
        assertNull(card.fsrsRetrievability)
    }

    @Test
    fun absentCapabilityKeepsAlreadyPortableSnapshotInstance() {
        val snapshot = snapshot(card(fsrs = arrayOf(null, null, null)))

        val normalized = ProviderCapabilityPolicy.normalize(
            ProviderCollectionSnapshot(
                snapshot,
                setOf(CollectionCapability.READ_COLLECTION),
                null,
            ),
        )

        assertSame(snapshot, normalized.snapshot)
        assertEquals(
            ProviderCapabilityPolicy.MemoryEvidence.INTERVAL_LAPSE_FALLBACK,
            normalized.memoryEvidence,
        )
    }

    private fun snapshot(card: RecordsSyncModels.Card) =
        RecordsSyncModels.CollectionSnapshot(emptyList(), listOf(card))

    private fun card(fsrs: Array<Double?>) =
        RecordsSyncModels.Card(
            201L,
            101L,
            1,
            "deck-id",
            "Deck name",
            2,
            2,
            9,
            30,
            10,
            3,
            false,
            fsrs[0],
            fsrs[1],
            fsrs[2],
        )
}
