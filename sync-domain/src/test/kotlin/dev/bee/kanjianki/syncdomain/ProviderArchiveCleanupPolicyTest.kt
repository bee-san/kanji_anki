package dev.bee.kanjianki.syncdomain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderArchiveCleanupPolicyTest {
    @Test
    fun allSuspendedSelectedCardsOnFullySuspendedNotesCanBeTagged() {
        val plan = ProviderArchiveCleanupPolicy.plan(
            listOf(
                card(10L, 1L, true),
                card(11L, 1L, true),
                card(20L, 2L, true),
            ),
            setOf(10L, 11L, 20L),
        )

        assertEquals(3, plan.sourceCards)
        assertEquals(setOf(1L, 2L), plan.notesToTag)
        assertEquals(0, plan.alreadyFailedCards)
        assertTrue(plan.hasSuspendedCards())
    }

    @Test
    fun noSelectionUsesEverySuspendedCard() {
        val plan = ProviderArchiveCleanupPolicy.plan(
            listOf(card(20L, 2L, true)),
            null,
        )

        assertEquals(1, plan.sourceCards)
        assertEquals(setOf(2L), plan.notesToTag)
        assertEquals(0, plan.alreadyFailedCards)
    }

    @Test
    fun partiallySuspendedNotesStayLocal() {
        val plan = ProviderArchiveCleanupPolicy.plan(
            listOf(
                card(10L, 1L, true),
                card(11L, 1L, false),
                card(20L, 2L, true),
            ),
            setOf(10L, 20L),
        )

        assertEquals(2, plan.sourceCards)
        assertEquals(setOf(2L), plan.notesToTag)
        assertEquals(1, plan.alreadyFailedCards)
    }

    @Test
    fun unselectedSuspendedSiblingKeepsSelectedCardLocal() {
        val plan = ProviderArchiveCleanupPolicy.plan(
            listOf(
                card(10L, 1L, true),
                card(11L, 1L, true),
            ),
            setOf(10L),
        )

        assertEquals(1, plan.sourceCards)
        assertTrue(plan.notesToTag.isEmpty())
        assertEquals(1, plan.alreadyFailedCards)
    }

    @Test
    fun emptySelectionProducesNoCleanupWork() {
        val plan = ProviderArchiveCleanupPolicy.plan(
            listOf(card(10L, 1L, true)),
            emptySet(),
        )

        assertEquals(0, plan.sourceCards)
        assertTrue(plan.notesToTag.isEmpty())
        assertEquals(0, plan.alreadyFailedCards)
        assertFalse(plan.hasSuspendedCards())
    }

    @Test
    fun selectedSuspendedCardIdsIgnoreActiveSources() {
        assertNull(ProviderArchiveCleanupPolicy.selectedSuspendedCardIds(null))

        val ids = ProviderArchiveCleanupPolicy.selectedSuspendedCardIds(
            listOf(
                ProviderArchiveCleanupPolicy.SelectedSource(10L, true),
                ProviderArchiveCleanupPolicy.SelectedSource(11L, false),
                ProviderArchiveCleanupPolicy.SelectedSource(10L, true),
            ),
        )

        assertEquals(setOf(10L), ids)
    }

    @Test
    fun removalMessagePreservesProviderCleanupCopy() {
        assertEquals(
            "Archived suspended notes were tagged in AnkiDroid and hidden from future syncs.",
            ProviderArchiveCleanupPolicy.removalMessage(2, 0),
        )
        assertEquals(
            "Archived suspended notes were partly tagged in AnkiDroid; any leftovers stay in the local archive.",
            ProviderArchiveCleanupPolicy.removalMessage(1, 1),
        )
        assertEquals(
            "Archived suspended cards were kept in the local archive; AnkiDroid did not allow provider tagging.",
            ProviderArchiveCleanupPolicy.removalMessage(0, 1),
        )
    }

    @Test
    fun staticWrappersStayAvailableForJavaInterop() {
        val plan = ProviderArchiveCleanupPolicy::class.java.getMethod(
            "plan",
            List::class.java,
            Set::class.java,
        ).invoke(null, listOf(card(10L, 1L, true)), setOf(10L)) as ProviderArchiveCleanupPolicy.CleanupPlan
        val selectedSuspendedCardIds = ProviderArchiveCleanupPolicy::class.java.getMethod(
            "selectedSuspendedCardIds",
            List::class.java,
        )
        val removalMessage = ProviderArchiveCleanupPolicy::class.java.getMethod(
            "removalMessage",
            Integer.TYPE,
            Integer.TYPE,
        )

        assertEquals(1, plan.sourceCards)
        assertEquals(
            setOf(10L),
            selectedSuspendedCardIds.invoke(null, listOf(ProviderArchiveCleanupPolicy.SelectedSource(10L, true))),
        )
        assertEquals(
            "Archived suspended notes were tagged in AnkiDroid and hidden from future syncs.",
            removalMessage.invoke(null, 2, 0),
        )
    }

    private fun card(cardId: Long, noteId: Long, suspended: Boolean): ProviderArchiveCleanupPolicy.Card {
        return ProviderArchiveCleanupPolicy.Card(cardId, noteId, suspended)
    }
}
