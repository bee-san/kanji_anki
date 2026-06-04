package dev.bee.kanjianki.syncdomain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncMirrorPolicyBridgeCoverageTest {
    @Test
    fun coversStaticBridgeMethods() {
        assertEquals(
            3,
            SyncMirrorPolicy.activeCardIndex(
                listOf(
                    SyncMirrorPolicy.Card(10L, 1L, false),
                    SyncMirrorPolicy.Card(11L, 1L, true),
                    SyncMirrorPolicy.Card(20L, 2L, false),
                    SyncMirrorPolicy.Card(21L, 2L, false),
                ),
            ).activeCardCount,
        )
        assertNull(SyncMirrorPolicy.selectedSuspendedCardIds(null))
        assertEquals(
            setOf(10L),
            SyncMirrorPolicy.selectedSuspendedCardIds(
                listOf(
                    SyncMirrorPolicy.SelectedSource(10L, true),
                    SyncMirrorPolicy.SelectedSource(11L, false),
                    SyncMirrorPolicy.SelectedSource(10L, true),
                ),
            ),
        )
    }
}
