package dev.bee.kanjianki.syncdomain;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class SyncMirrorPolicyTest {
    @Test
    public void activeCardIndexTracksOnlyUnsuspendedCards() {
        SyncMirrorPolicy.ActiveCardIndex index = SyncMirrorPolicy.activeCardIndex(Arrays.asList(
                card(10L, 1L, false),
                card(11L, 1L, true),
                card(20L, 2L, false),
                card(21L, 2L, false)
        ));

        assertEquals(set(1L, 2L), index.noteIds());
        assertEquals(set(10L, 20L, 21L), index.cardIds());
        assertEquals(3, index.activeCardCount());
    }

    @Test
    public void selectedSuspendedCardIdsIgnoresActiveSources() {
        assertNull(SyncMirrorPolicy.selectedSuspendedCardIds(null));

        Set<Long> ids = SyncMirrorPolicy.selectedSuspendedCardIds(Arrays.asList(
                source(10L, true),
                source(11L, false),
                source(10L, true)
        ));

        assertEquals(set(10L), ids);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void activeCardIndexExposesImmutableSets() {
        SyncMirrorPolicy.activeCardIndex(Collections.singletonList(card(10L, 1L, false))).cardIds().add(20L);
    }

    private static SyncMirrorPolicy.Card card(long cardId, long noteId, boolean suspended) {
        return new SyncMirrorPolicy.Card(cardId, noteId, suspended);
    }

    private static SyncMirrorPolicy.SelectedSource source(long cardId, boolean suspended) {
        return new SyncMirrorPolicy.SelectedSource(cardId, suspended);
    }

    private static Set<Long> set(Long... values) {
        return new java.util.LinkedHashSet<>(Arrays.asList(values));
    }
}
