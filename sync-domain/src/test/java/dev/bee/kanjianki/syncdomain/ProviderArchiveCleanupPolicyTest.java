package dev.bee.kanjianki.syncdomain;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ProviderArchiveCleanupPolicyTest {
    @Test
    public void allSuspendedSelectedCardsOnFullySuspendedNotesCanBeTagged() {
        ProviderArchiveCleanupPolicy.CleanupPlan plan = ProviderArchiveCleanupPolicy.plan(
                Arrays.asList(
                        card(10L, 1L, true),
                        card(11L, 1L, true),
                        card(20L, 2L, true)
                ),
                set(10L, 11L, 20L)
        );

        assertEquals(3, plan.sourceCards());
        assertEquals(set(1L, 2L), plan.notesToTag());
        assertEquals(0, plan.alreadyFailedCards());
        assertTrue(plan.hasSuspendedCards());
    }

    @Test
    public void noSelectionUsesEverySuspendedCard() {
        ProviderArchiveCleanupPolicy.CleanupPlan plan = ProviderArchiveCleanupPolicy.plan(
                Collections.singletonList(card(20L, 2L, true)),
                null
        );

        assertEquals(1, plan.sourceCards());
        assertEquals(set(2L), plan.notesToTag());
        assertEquals(0, plan.alreadyFailedCards());
    }

    @Test
    public void partiallySuspendedNotesStayLocal() {
        ProviderArchiveCleanupPolicy.CleanupPlan plan = ProviderArchiveCleanupPolicy.plan(
                Arrays.asList(
                        card(10L, 1L, true),
                        card(11L, 1L, false),
                        card(20L, 2L, true)
                ),
                set(10L, 20L)
        );

        assertEquals(2, plan.sourceCards());
        assertEquals(set(2L), plan.notesToTag());
        assertEquals(1, plan.alreadyFailedCards());
    }

    @Test
    public void unselectedSuspendedSiblingKeepsSelectedCardLocal() {
        ProviderArchiveCleanupPolicy.CleanupPlan plan = ProviderArchiveCleanupPolicy.plan(
                Arrays.asList(
                        card(10L, 1L, true),
                        card(11L, 1L, true)
                ),
                set(10L)
        );

        assertEquals(1, plan.sourceCards());
        assertTrue(plan.notesToTag().isEmpty());
        assertEquals(1, plan.alreadyFailedCards());
    }

    @Test
    public void emptySelectionProducesNoCleanupWork() {
        ProviderArchiveCleanupPolicy.CleanupPlan plan = ProviderArchiveCleanupPolicy.plan(
                Collections.singletonList(card(10L, 1L, true)),
                Collections.emptySet()
        );

        assertEquals(0, plan.sourceCards());
        assertTrue(plan.notesToTag().isEmpty());
        assertEquals(0, plan.alreadyFailedCards());
        assertFalse(plan.hasSuspendedCards());
    }

    @Test
    public void selectedSuspendedCardIdsIgnoreActiveSources() {
        assertNull(ProviderArchiveCleanupPolicy.selectedSuspendedCardIds(null));

        Set<Long> ids = ProviderArchiveCleanupPolicy.selectedSuspendedCardIds(Arrays.asList(
                new ProviderArchiveCleanupPolicy.SelectedSource(10L, true),
                new ProviderArchiveCleanupPolicy.SelectedSource(11L, false),
                new ProviderArchiveCleanupPolicy.SelectedSource(10L, true)
        ));

        assertEquals(set(10L), ids);
    }

    @Test
    public void removalMessagePreservesProviderCleanupCopy() {
        assertEquals(
                "Archived suspended notes were tagged in AnkiDroid and hidden from future syncs.",
                ProviderArchiveCleanupPolicy.removalMessage(2, 0)
        );
        assertEquals(
                "Archived suspended notes were partly tagged in AnkiDroid; any leftovers stay in the local archive.",
                ProviderArchiveCleanupPolicy.removalMessage(1, 1)
        );
        assertEquals(
                "Archived suspended cards were kept in the local archive; AnkiDroid did not allow provider tagging.",
                ProviderArchiveCleanupPolicy.removalMessage(0, 1)
        );
    }

    private static ProviderArchiveCleanupPolicy.Card card(long cardId, long noteId, boolean suspended) {
        return new ProviderArchiveCleanupPolicy.Card(cardId, noteId, suspended);
    }

    private static Set<Long> set(Long... values) {
        return new java.util.LinkedHashSet<>(Arrays.asList(values));
    }
}
