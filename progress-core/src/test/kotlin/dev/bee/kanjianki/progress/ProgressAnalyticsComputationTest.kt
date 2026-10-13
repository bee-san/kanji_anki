package dev.bee.kanjianki.progress

import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.data.ReviewDaySummarySnapshot
import dev.bee.kanjianki.data.StatsSnapshot
import dev.bee.kanjianki.data.fakes.emptyStatsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The portable progress-analytics computation, driven from a hand-built snapshot.
 *
 * The Android host proves this path against a real store under Robolectric; this is
 * the pure-JVM proof that the same computation runs with no Android beneath it — which
 * is what lets the desktop host compute identical analytics. It also fills the
 * coverage the store-backed test used to provide before the computation moved here.
 */
class ProgressAnalyticsComputationTest {
    private val ladder = RecordsBase.StudyLadderSettings.defaults()

    @Test
    fun anEmptySnapshotStillProducesAWholeDashboard() {
        val state = progressAnalyticsSnapshot(emptyStatsSnapshot(nowMillis = NOW), NOW, ladder)

        // Every section is present even with no data — the dashboard renders empty
        // states, not a null screen.
        assertEquals(NOW, state.generatedAtMillis)
        assertNotNull(state.overview)
        assertNotNull(state.reviewsAnalytics)
        assertNotNull(state.accuracyRetention)
        assertNotNull(state.progressByLevel)
        assertNotNull(state.weaknessInsights)
    }

    @Test
    fun reviewDaySummariesFlowIntoTheReviewsAnalytics() {
        val snapshot = emptyStatsSnapshot(nowMillis = NOW, reviewDaySummaries = last30Days(NOW))

        val state = progressAnalyticsSnapshot(snapshot, NOW, ladder)

        // The per-day reviews reach the bar chart, and the range selector offers the
        // three windows the analytics computes.
        assertTrue(
            "seeded review days produce non-zero bars",
            state.reviewsAnalytics.reviewsPerDay.values.any { it > 0 },
        )
        assertTrue(state.reviewsAnalytics.availableRanges.isNotEmpty())
    }

    @Test
    fun theSourceOverloadRecomputesWhenNoCacheIsFresh() {
        // The source-taking overload is the one the hosts wire to a store. Driving it
        // with a fake source exercises the cached-vs-recompute branch without SQLite.
        var recomputed = false
        val source = object : ProgressAnalyticsStatsSource {
            override fun cachedStatsSnapshotOrNull(nowMillis: Long): StatsSnapshot? = null
            override fun recomputeStatsSnapshotSynchronously(nowMillis: Long): StatsSnapshot {
                recomputed = true
                return emptyStatsSnapshot(nowMillis = nowMillis)
            }
            override fun reviewDaySummaries(nowMillis: Long, days: Int): List<ReviewDaySummary> = emptyList()
        }

        val state = progressAnalyticsSnapshot(source, NOW, ladderSettings = ladder)

        assertTrue("a missing cache forces a recompute", recomputed)
        assertNotNull(state.overview)
    }

    @Test
    fun aFreshCacheIsUsedWithoutRecomputing() {
        var recomputed = false
        val source = object : ProgressAnalyticsStatsSource {
            override fun cachedStatsSnapshotOrNull(nowMillis: Long): StatsSnapshot =
                emptyStatsSnapshot(nowMillis = nowMillis, reviewDaySummaries = last30Days(nowMillis))
            override fun recomputeStatsSnapshotSynchronously(nowMillis: Long): StatsSnapshot {
                recomputed = true
                return emptyStatsSnapshot(nowMillis = nowMillis)
            }
            override fun reviewDaySummaries(nowMillis: Long, days: Int): List<ReviewDaySummary> = emptyList()
        }

        progressAnalyticsSnapshot(source, NOW, ladderSettings = ladder)

        assertTrue("a fresh cache is not recomputed", !recomputed)
    }

    private fun last30Days(now: Long): List<ReviewDaySummarySnapshot> {
        val todayStart = LocalDayPolicy.localDayStart(now)
        return (-29..0).map { offset ->
            val dayStart = LocalDayPolicy.moveLocalDays(todayStart, offset)
            val total = if (offset % 3 == 0) 4 else 1
            ReviewDaySummarySnapshot(
                dayStartMillis = dayStart,
                total = total,
                again = 0,
                hard = 0,
                good = total,
                easy = 0,
                writingRequired = 0,
                writingFailed = 0,
            )
        }
    }

    private companion object {
        const val NOW = 1_747_000_000_000L
    }
}
