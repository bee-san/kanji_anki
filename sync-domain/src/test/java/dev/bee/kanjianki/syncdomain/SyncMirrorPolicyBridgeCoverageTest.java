package dev.bee.kanjianki.syncdomain;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class SyncMirrorPolicyBridgeCoverageTest {
    @Test
    public void coversStaticBridgeMethods() {
        assertEquals(3, SyncMirrorPolicy.activeCardIndex(Arrays.asList(
                new SyncMirrorPolicy.Card(10L, 1L, false),
                new SyncMirrorPolicy.Card(11L, 1L, true),
                new SyncMirrorPolicy.Card(20L, 2L, false),
                new SyncMirrorPolicy.Card(21L, 2L, false)
        )).activeCardCount());
        assertNull(SyncMirrorPolicy.selectedSuspendedCardIds(null));
        assertEquals(Collections.singleton(10L), SyncMirrorPolicy.selectedSuspendedCardIds(Arrays.asList(
                new SyncMirrorPolicy.SelectedSource(10L, true),
                new SyncMirrorPolicy.SelectedSource(11L, false),
                new SyncMirrorPolicy.SelectedSource(10L, true)
        )));
    }
}
