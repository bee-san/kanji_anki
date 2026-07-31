package dev.bee.kanjianki.provider.ankiconnect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiConnectReadPlannerTest {
    @Test
    fun acceptsIdCountsWithinTheCap() {
        AnkiConnectReadPlanner.requireWithinIdCap(0)
        AnkiConnectReadPlanner.requireWithinIdCap(AnkiConnectReadPlanner.MAX_ID_COUNT)
    }

    @Test
    fun rejectsIdCountsOverTheCap() {
        val error = assertThrows(AnkiConnectReadPlanner.OversizeIdResponseException::class.java) {
            AnkiConnectReadPlanner.requireWithinIdCap(AnkiConnectReadPlanner.MAX_ID_COUNT + 1)
        }
        assertEquals(AnkiConnectReadPlanner.MAX_ID_COUNT + 1, error.count)
        assertEquals(AnkiConnectReadPlanner.MAX_ID_COUNT, error.cap)
    }

    @Test
    fun splitsIdsIntoClampedBatches() {
        val ids = (1..250).toList()
        val batches = AnkiConnectReadPlanner.batches(ids, batchSize = 100)
        assertEquals(listOf(100, 100, 50), batches.map { it.size })
        // Round-trips to the original list in order.
        assertEquals(ids, batches.flatten())
    }

    @Test
    fun clampsBatchSizeToBounds() {
        val ids = (1..40).toList()
        // Below MIN clamps up to MIN_BATCH.
        assertEquals(
            AnkiConnectReadPlanner.MIN_BATCH,
            AnkiConnectReadPlanner.batches(ids, batchSize = 1).first().size,
        )
        // Above MAX clamps down to MAX_BATCH (single batch here since 40 < 500).
        assertEquals(1, AnkiConnectReadPlanner.batches(ids, batchSize = 100_000).size)
    }

    @Test
    fun emptyInputYieldsNoBatches() {
        assertTrue(AnkiConnectReadPlanner.batches(emptyList<Long>()).isEmpty())
    }

    @Test
    fun adaptsBatchSizeDownWhenRowsAreLarge() {
        // 100 rows encoded to 5 MiB => ~52.4 KiB/row => target 1 MiB => ~19 rows.
        val next = AnkiConnectReadPlanner.adaptBatchSize(lastBatchSize = 100, lastBatchBytes = 5L * 1024 * 1024)
        assertTrue("expected shrink toward ~19 but was $next", next in AnkiConnectReadPlanner.MIN_BATCH..40)
    }

    @Test
    fun adaptsBatchSizeUpWhenRowsAreSmallButClampsToCeiling() {
        val next = AnkiConnectReadPlanner.adaptBatchSize(lastBatchSize = 100, lastBatchBytes = 10_000)
        assertEquals(AnkiConnectReadPlanner.MAX_BATCH, next)
    }

    @Test
    fun adaptationWithNoObservationKeepsClampedSize() {
        assertEquals(
            AnkiConnectReadPlanner.MIN_BATCH,
            AnkiConnectReadPlanner.adaptBatchSize(lastBatchSize = 0, lastBatchBytes = 0),
        )
        assertEquals(
            50,
            AnkiConnectReadPlanner.adaptBatchSize(lastBatchSize = 50, lastBatchBytes = 0),
        )
    }

    @Test
    fun multiGroupsAreBoundedAndCoverEveryAction() {
        val actions = (1..AnkiConnectReadPlanner.MAX_MULTI_ACTIONS + 4).toList()
        val groups = AnkiConnectReadPlanner.multiGroups(actions)

        assertEquals(2, groups.size)
        assertTrue(groups.all { it.size <= AnkiConnectReadPlanner.MAX_MULTI_ACTIONS })
        assertEquals(actions, groups.flatten())
    }

    @Test
    fun multiGroupsClampAnOutOfRangeGroupSizeAndSkipEmptyInput() {
        assertEquals(
            AnkiConnectReadPlanner.MAX_MULTI_ACTIONS,
            AnkiConnectReadPlanner.multiGroups(
                (1..AnkiConnectReadPlanner.MAX_MULTI_ACTIONS).toList(),
                groupSize = 10_000,
            ).single().size,
        )
        assertEquals(1, AnkiConnectReadPlanner.multiGroups(listOf(1, 2), groupSize = 0).first().size)
        assertTrue(AnkiConnectReadPlanner.multiGroups(emptyList<Int>()).isEmpty())
    }
}
