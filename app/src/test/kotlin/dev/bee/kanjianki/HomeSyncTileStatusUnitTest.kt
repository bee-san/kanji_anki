package dev.bee.kanjianki

import dev.bee.kanjianki.core.DailyStudyPlanPolicy
import dev.bee.kanjianki.core.DailyStudyPlanRequest
import dev.bee.kanjianki.core.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSyncTileStatusUnitTest {
    private val dayMillis = 24L * 60L * 60L * 1000L

    @Test
    fun staleSyncIsNotUpToDateEvenWhenLastSyncSucceeded() {
        // Reproduces the reported contradiction: the tile said "Up to date" for a
        // two-day-old sync while the Today card said "Sync needed before Kani can
        // judge progress".
        val now = 10L * dayMillis
        val stalePlan = DailyStudyPlanPolicy.plan(
            DailyStudyPlanRequest(
                nowMillis = now,
                dueAtMillis = listOf(now - 1L),
                lastSuccessfulSyncAtMillis = now - 2L * dayMillis,
            ),
        )

        assertEquals(SyncStatus.SYNC_NEEDED_TO_JUDGE_PROGRESS, stalePlan.syncStatus)
        assertFalse(syncTileUpToDate(canSync = true, lastSyncSucceeded = true, dailyPlan = stalePlan))
    }

    @Test
    fun freshSuccessfulSyncStaysUpToDate() {
        val now = 10L * dayMillis
        val freshPlan = DailyStudyPlanPolicy.plan(
            DailyStudyPlanRequest(
                nowMillis = now,
                dueAtMillis = listOf(now - 1L),
                lastSuccessfulSyncAtMillis = now - dayMillis / 2,
            ),
        )

        assertEquals(SyncStatus.CURRENT, freshPlan.syncStatus)
        assertTrue(syncTileUpToDate(canSync = true, lastSyncSucceeded = true, dailyPlan = freshPlan))
    }

    @Test
    fun missingPlanPreservesLegacyProviderAndSuccessGating() {
        assertTrue(syncTileUpToDate(canSync = true, lastSyncSucceeded = true, dailyPlan = null))
        assertFalse(syncTileUpToDate(canSync = false, lastSyncSucceeded = true, dailyPlan = null))
        assertFalse(syncTileUpToDate(canSync = true, lastSyncSucceeded = false, dailyPlan = null))
    }
}
