package dev.bee.kanjianki.syncdomain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            ),
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
            ),
        )

        assertEquals(setOf(10L), ids)
    }

    @Test(expected = UnsupportedOperationException::class)
    fun activeCardIndexExposesImmutableSets() {
        @Suppress("UNCHECKED_CAST")
        val cardIds = SyncMirrorPolicy.activeCardIndex(listOf(card(10L, 1L, false))).cardIds as MutableSet<Long>

        cardIds.add(20L)
    }

    @Test
    fun staticWrappersStayAvailableForJavaInterop() {
        val activeCardIndex = SyncMirrorPolicy::class.java.getMethod(
            "activeCardIndex",
            List::class.java,
        )
        val selectedSuspendedCardIds = SyncMirrorPolicy::class.java.getMethod(
            "selectedSuspendedCardIds",
            List::class.java,
        )

        val index = activeCardIndex.invoke(null, listOf(card(10L, 1L, false))) as SyncMirrorPolicy.ActiveCardIndex

        assertEquals(1, index.activeCardCount)
        assertEquals(
            setOf(10L),
            selectedSuspendedCardIds.invoke(null, listOf(source(10L, true))),
        )
    }

    private fun card(cardId: Long, noteId: Long, suspended: Boolean): SyncMirrorPolicy.Card {
        return SyncMirrorPolicy.Card(cardId, noteId, suspended)
    }

    private fun source(cardId: Long, suspended: Boolean): SyncMirrorPolicy.SelectedSource {
        return SyncMirrorPolicy.SelectedSource(cardId, suspended)
    }
}
