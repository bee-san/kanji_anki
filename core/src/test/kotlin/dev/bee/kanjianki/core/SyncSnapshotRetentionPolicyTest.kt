package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncSnapshotRetentionPolicyTest {
    @Test
    fun emptyInputPrunesNothing() {
        assertEquals(emptyList<Long>(), SyncSnapshotRetentionPolicy.snapshotSyncIdsToPrune(emptyList()))
    }

    @Test
    fun withinRetentionWindowPrunesNothing() {
        val ids = listOf(1L, 2L, 3L)
        assertEquals(emptyList<Long>(), SyncSnapshotRetentionPolicy.snapshotSyncIdsToPrune(ids, keepLatest = 8))
    }

    @Test
    fun keepsEarliestAndNewestWindowPrunesMiddle() {
        val ids = (1L..12L).toList()
        val pruned = SyncSnapshotRetentionPolicy.snapshotSyncIdsToPrune(ids, keepLatest = 3)
        // Keep earliest (1) + newest 3 (10, 11, 12); prune 2..9.
        assertEquals(listOf(2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L), pruned)
    }

    @Test
    fun defaultWindowKeepsEarliestPlusEightNewest() {
        val ids = (1L..20L).toList()
        val pruned = SyncSnapshotRetentionPolicy.snapshotSyncIdsToPrune(ids)
        // Earliest = 1, newest 8 = 13..20. Prune 2..12.
        assertEquals((2L..12L).toList(), pruned)
        assertTrue(pruned.none { it == 1L })
        assertTrue(pruned.none { it >= 13L })
    }

    @Test
    fun handlesUnsortedAndDuplicateInput() {
        val ids = listOf(5L, 1L, 3L, 5L, 2L, 4L)
        val pruned = SyncSnapshotRetentionPolicy.snapshotSyncIdsToPrune(ids, keepLatest = 2)
        // Distinct sorted = 1,2,3,4,5. Keep earliest 1 + newest 2 (4,5). Prune 2,3.
        assertEquals(listOf(2L, 3L), pruned)
    }

    @Test
    fun keepLatestBelowOneIsTreatedAsOne() {
        val ids = listOf(1L, 2L, 3L, 4L)
        val pruned = SyncSnapshotRetentionPolicy.snapshotSyncIdsToPrune(ids, keepLatest = 0)
        // Keep earliest 1 + newest 1 (4). Prune 2,3.
        assertEquals(listOf(2L, 3L), pruned)
    }

    @Test
    fun singleSyncPrunesNothing() {
        assertEquals(emptyList<Long>(), SyncSnapshotRetentionPolicy.snapshotSyncIdsToPrune(listOf(42L)))
    }
}
