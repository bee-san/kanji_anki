package dev.bee.kanjianki.progress

import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.data.STATS_CACHE_FORMAT_VERSION
import dev.bee.kanjianki.data.StatsCacheStore
import dev.bee.kanjianki.data.StudyStatsStore
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProgressAnalyticsPerformancePathTest {
    @Test
    fun freshCachedSnapshotDoesNotUseLatestDirectOrLiveReviewQueries() {
        val now = 44_444L
        val source = GuardedProgressAnalyticsStatsSource(
            fresh = snapshot(sourceVersion = 7L, now = now),
            latest = snapshot(sourceVersion = 8L, now = now),
            direct = snapshot(sourceVersion = 9L, now = now),
        )
        var scheduled = 0

        val state = progressAnalyticsSnapshot(source, nowMillis = now, scheduleRefresh = { scheduled += 1 })

        assertEquals(now, state.generatedAtMillis)
        assertEquals(1, source.freshReads)
        assertEquals(0, source.latestReads)
        assertEquals(0, source.directRecomputes)
        assertEquals(0, scheduled)
        assertEquals(4, state.reviewsAnalytics.totalReviews.value)
        assertEquals(listOf(1, 1, 1, 0, 0, 0, 1), state.reviewsAnalytics.reviewsPerDay.values)
    }

    @Test
    fun staleLatestCachedSnapshotSchedulesRefreshExactlyOnce() {
        val now = 55_555L
        val source = GuardedProgressAnalyticsStatsSource(
            latest = snapshot(sourceVersion = 8L, now = now),
            direct = snapshot(sourceVersion = 9L, now = now),
        )
        var scheduled = 0

        val state = progressAnalyticsSnapshot(source, nowMillis = now, scheduleRefresh = { scheduled += 1 })

        assertEquals(now, state.generatedAtMillis)
        assertEquals(1, source.freshReads)
        assertEquals(1, source.latestReads)
        assertEquals(0, source.directRecomputes)
        assertEquals(1, scheduled)
        assertEquals(4, state.reviewsAnalytics.totalReviews.value)
        assertEquals(listOf(1, 1, 1, 0, 0, 0, 1), state.reviewsAnalytics.reviewsPerDay.values)
    }

    @Test
    fun noCachePathUsesDirectRecomputeWithoutLiveReviewQueries() {
        val now = 66_666L
        val source = GuardedProgressAnalyticsStatsSource(
            direct = snapshot(sourceVersion = 9L, now = now),
        )
        var scheduled = 0

        val state = progressAnalyticsSnapshot(source, nowMillis = now, scheduleRefresh = { scheduled += 1 })

        assertEquals(now, state.generatedAtMillis)
        assertEquals(1, source.freshReads)
        assertEquals(1, source.latestReads)
        assertEquals(1, source.directRecomputes)
        assertEquals(0, scheduled)
        assertEquals(4, state.reviewsAnalytics.totalReviews.value)
        assertEquals(listOf(1, 1, 1, 0, 0, 0, 1), state.reviewsAnalytics.reviewsPerDay.values)
    }

    private fun snapshot(sourceVersion: Long, now: Long): StatsCacheStore.Snapshot {
        return StatsCacheStore.Snapshot(
            outcomeStats = StudyStatsStore.KaniOutcomeStats.empty(),
            impactReport = KanjiImpactAnalyzer.Report(0, 0, 0, emptyList()),
            generatedAtMillis = 1_111L,
            sourceVersion = sourceVersion,
            studyImpactStats = StudyStatsStore.StudyImpactStats(12, 5, 4, 1, 2, 0),
            recentMistakes = listOf(
                StudyStatsStore.RecentMistake("痛", "again", now - 2_000L),
                StudyStatsStore.RecentMistake("痛", "hard", now - 1_000L),
                StudyStatsStore.RecentMistake("弱", "again", now - 3_000L),
            ),
            studyStreak = StudyStatsStore.StudyStreak(2, 5, true, 1, now),
            studyTaskTimeStats = StudyStatsStore.StudyTaskTimeStats(180_000L, 720_000L, 8),
            cacheFormatVersion = STATS_CACHE_FORMAT_VERSION,
            reviewDaySummaries = cachedReviewDaySummaries(now),
        )
    }

    private fun cachedReviewDaySummaries(now: Long): List<StatsCacheStore.ReviewDaySummarySnapshot> {
        val todayStart = LocalDayPolicy.localDayStart(now)
        return (-29..0).map { dayOffset ->
            val dayStart = LocalDayPolicy.moveLocalDays(todayStart, dayOffset)
            when (dayOffset) {
                -6 -> reviewDaySnapshot(dayStart, total = 1, good = 1)
                -5 -> reviewDaySnapshot(dayStart, total = 1, again = 1)
                -4 -> reviewDaySnapshot(dayStart, total = 1, easy = 1)
                0 -> reviewDaySnapshot(dayStart, total = 1, hard = 1, writingRequired = 1, writingFailed = 1)
                else -> reviewDaySnapshot(dayStart)
            }
        }
    }

    private fun reviewDaySnapshot(
        dayStart: Long,
        total: Int = 0,
        again: Int = 0,
        hard: Int = 0,
        good: Int = 0,
        easy: Int = 0,
        writingRequired: Int = 0,
        writingFailed: Int = 0,
    ): StatsCacheStore.ReviewDaySummarySnapshot {
        return StatsCacheStore.ReviewDaySummarySnapshot(
            dayStartMillis = dayStart,
            total = total,
            again = again,
            hard = hard,
            good = good,
            easy = easy,
            writingRequired = writingRequired,
            writingFailed = writingFailed,
        )
    }

    private class GuardedProgressAnalyticsStatsSource(
        private val fresh: StatsCacheStore.Snapshot? = null,
        private val latest: StatsCacheStore.Snapshot? = null,
        private val direct: StatsCacheStore.Snapshot,
    ) : ProgressAnalyticsStatsSource {
        var freshReads = 0
        var latestReads = 0
        var directRecomputes = 0

        override fun cachedStatsSnapshotOrNull(): StatsCacheStore.Snapshot? {
            freshReads += 1
            return fresh
        }

        override fun latestStatsSnapshotOrNull(): StatsCacheStore.Snapshot? {
            latestReads += 1
            return latest
        }

        override fun recomputeStatsSnapshotSynchronously(nowMillis: Long): StatsCacheStore.Snapshot {
            directRecomputes += 1
            return direct
        }

        override fun reviewDaySummaries(nowMillis: Long, days: Int): List<ReviewDaySummary> {
            error("unexpected live review-day SQL for $days days")
        }
    }
}
