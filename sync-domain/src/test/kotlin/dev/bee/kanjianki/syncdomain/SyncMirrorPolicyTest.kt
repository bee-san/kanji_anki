package dev.bee.kanjianki.syncdomain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncMirrorPolicyTest {
    @Test
    fun activeCardIndexTracksOnlyUnsuspendedCards() {
        val index = SyncMirrorPolicy.activeCardIndex(
            listOf(
                card(10L, 1L, false),
                card(11L, 1L, true),
                card(20L, 2L, false),
                card(21L, 2L, false),
            )
        )

        assertEquals(setOf(1L, 2L), index.noteIds)
        assertEquals(setOf(10L, 20L, 21L), index.cardIds)
        assertEquals(3, index.activeCardCount)
    }

    @Test
    fun selectedSuspendedCardIdsIgnoresActiveSources() {
        assertNull(SyncMirrorPolicy.selectedSuspendedCardIds(null))

        val ids = SyncMirrorPolicy.selectedSuspendedCardIds(
            listOf(
                source(10L, true),
                source(11L, false),
                source(10L, true),
            )
        )

        assertEquals(setOf(10L), ids)
    }

    @Test
    fun activeCardIndexExposesImmutableSets() {
        assertThrows(UnsupportedOperationException::class.java) {
            (SyncMirrorPolicy.activeCardIndex(listOf(card(10L, 1L, false))).cardIds as MutableSet<Long>).add(20L)
        }
    }
}

private fun card(cardId: Long, noteId: Long, suspended: Boolean): SyncMirrorPolicy.Card {
    return SyncMirrorPolicy.Card(cardId, noteId, suspended)
}

private fun source(cardId: Long, suspended: Boolean): SyncMirrorPolicy.SelectedSource {
    return SyncMirrorPolicy.SelectedSource(cardId, suspended)
}
