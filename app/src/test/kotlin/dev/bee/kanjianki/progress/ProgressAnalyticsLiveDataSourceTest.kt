package dev.bee.kanjianki.progress

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.ChartAxisPolicy
import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.StatsDashboardCopy
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreSchema
import dev.bee.kanjianki.data.STATS_CACHE_FORMAT_VERSION
import dev.bee.kanjianki.data.StatsCacheStore
import dev.bee.kanjianki.data.StudyStatsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProgressAnalyticsLiveDataSourceTest {
    private lateinit var context: Context
    private var localStore: LocalStore? = null
    private lateinit var db: SQLiteDatabase
    private lateinit var statsCache: StatsCacheStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        localStore = LocalStore(context)
        db = localStore!!.writableDatabase
        statsCache = StatsCacheStore(localStore!!)
    }

    @After
    fun tearDown() {
        localStore?.close()
        localStore = null
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @Test
    fun freshCachedSnapshotUsesStoredReviewDaySummariesWithoutReviewLogQueries() {
        val now = System.currentTimeMillis()
        writeFreshStatsSnapshot(now, cachedReviewDaySummaries(now))

        val snapshot = progressAnalyticsSnapshot(localStore!!, now)

        assertEquals(now, snapshot.generatedAtMillis)
        assertEquals("Stats overview", snapshot.overview.title)
        assertEquals(12, snapshot.overview.totalReviews.value)
        assertEquals("12", snapshot.overview.totalReviews.valueLabel)
        assertEquals("0 active items", snapshot.progressByLevel.overallLearned.valueLabel)
        assertEquals(0, snapshot.progressByLevel.overallLearned.percent)
        assertEquals(5, snapshot.overview.kanjiLearned.value)
        assertEquals(2, snapshot.overview.currentStreak.currentDays)
        assertEquals(5, snapshot.overview.currentStreak.bestDays)
        assertEquals("2 days", snapshot.overview.currentStreak.valueLabel)

        val reviews = snapshot.reviewsAnalytics
        assertEquals(4, reviews.totalReviews.value)
        assertEquals(3, reviews.correct.value)
        assertEquals(1, reviews.incorrect.value)
        assertEquals(listOf(1, 1, 1, 0, 0, 0, 1), reviews.reviewsPerDay.values)
        assertEquals(1, reviews.reviewsPerDay.values.last())
        assertTrue(reviews.accessibilitySummary.contains("4 total reviews"))
        assertEquals(2, reviews.currentStreak.currentDays)
        assertEquals(5, reviews.currentStreak.bestDays)
        assertEquals("Best 5 days", reviews.currentStreak.detailLabel)
        assertEquals("Today's streak is secure.", reviews.tip)

        assertEquals(listOf("Meaning", "Reading", "Writing", "Discrimination"), snapshot.overview.cardTypeBreakdown.segments.map { it.label })
        assertEquals(listOf(3, 1, 2, 2), snapshot.overview.cardTypeBreakdown.segments.map { it.value })
        assertEquals(listOf("Accuracy %"), snapshot.accuracyRetention.accuracyTrend.series.map { it.label })
        assertEquals(listOf("Meaning", "Reading", "Writing", "Discrimination"), snapshot.accuracyRetention.retentionByCardType.map { it.label })
        val seriesValues = snapshot.accuracyRetention.accuracyTrend.series.flatMap { it.values }
        assertEquals(ChartAxisPolicy.forValues(seriesValues), snapshot.accuracyRetention.accuracyTrend.axis)
        assertEquals(listOf("痛", "弱"), snapshot.weaknessInsights.mostMissedKanji.map { it.kanji })
        assertEquals(listOf("徴"), snapshot.weaknessInsights.confusionPairs.map { it.firstKanji })
        assertEquals("Needs improvement", snapshot.weaknessInsights.focusScore.status)
        assertNull(snapshot.overview.accuracy.deltaLabel)
        assertEquals("Last 7 days", snapshot.overview.focusSessions.detailLabel)
    }

    @Test
    fun legacyLadderRowsFollowStoredCustomOrder() {
        val now = System.currentTimeMillis()
        val defaults = RecordsBase.StudyLadderSettings.defaults()
        val configured = RecordsBase.StudyLadderSettings(
            defaults.orderedRungs.reversed(),
            defaults.enabledRungs,
        )
        val rungCounts = configured.orderedRungs.mapIndexed { index, rung -> rung to index + 1 }.toMap()
        val ladderHealth = StudyStatsStore.LadderHealthMetric(
            rungCounts,
            rungCounts.values.sum(),
            3,
            0,
            0,
            0,
        )
        writeFreshStatsSnapshot(
            now,
            cachedReviewDaySummaries(now),
            outcomeStats = StudyStatsStore.KaniOutcomeStats(
                StudyStatsStore.WeakKanjiImprovedMetric.empty(),
                StudyStatsStore.MatureSupportGainedMetric.empty(),
                ladderHealth,
            ),
        )
        localStore!!.saveStudyLadderSettings(configured)
        var refreshesScheduled = 0

        val rows = progressAnalyticsSnapshot(
            localStore!!,
            now,
            scheduleRefresh = { refreshesScheduled += 1 },
        ).progressByLevel.levelRows
        val copy = StatsDashboardCopy.forLocale()

        assertEquals(1, refreshesScheduled)
        assertEquals(
            configured.orderedRungs.map { copy.rung(it.wireName()) },
            rows.map { it.level },
        )
        assertEquals(
            configured.orderedRungs.map { ladderHealth.countFor(it) },
            rows.map { it.learned },
        )
    }

    @Test
    fun legacyLatestSnapshotRecomputesSynchronouslyInsteadOfRenderingMissingExtras() {
        val now = System.currentTimeMillis()
        val source = CountingProgressStatsSource(
            latest = progressSnapshot(
                now = now,
                totalReviews = 0,
                cacheFormatVersion = STATS_CACHE_FORMAT_VERSION - 1,
            ),
            recomputed = progressSnapshot(
                now = now,
                totalReviews = 17,
                cacheFormatVersion = STATS_CACHE_FORMAT_VERSION,
            ),
        )

        val snapshot = progressAnalyticsSnapshot(
            source,
            nowMillis = now,
            ladderSettings = RecordsBase.StudyLadderSettings.defaults(),
        )

        assertEquals(1, source.cachedReads)
        assertEquals(1, source.latestReads)
        assertEquals(listOf(now), source.recomputeReads)
        assertEquals(17, snapshot.overview.totalReviews.value)
        assertEquals("17", snapshot.overview.totalReviews.valueLabel)
        assertFalse(snapshot.weaknessInsights.focusScoreAvailable)
        assertEquals(0, snapshot.weaknessInsights.focusScore.value)
        assertTrue(snapshot.weaknessInsights.weaknessRows.isEmpty())
    }

    @Test
    fun streakTipDistinguishesSafeActiveAndNotStartedStates() {
        val now = System.currentTimeMillis()
        val reviewDays = cachedReviewDaySummaries(now)
        writeFreshStatsSnapshot(
            now,
            reviewDays,
            StudyStatsStore.StudyStreak(3, 8, false, 0, now - 86_400_000L),
        )

        assertEquals(
            "Keep the streak going with a short review session today.",
            progressAnalyticsSnapshot(localStore!!, now).reviewsAnalytics.tip,
        )

        writeFreshStatsSnapshot(
            now,
            reviewDays,
            StudyStatsStore.StudyStreak(0, 8, false, 0, now - 172_800_000L),
        )

        assertEquals(
            "Start a short review session today to build momentum.",
            progressAnalyticsSnapshot(localStore!!, now).reviewsAnalytics.tip,
        )
    }

    private fun writeFreshStatsSnapshot(
        now: Long,
        reviewDaySummaries: List<StatsCacheStore.ReviewDaySummarySnapshot>,
        studyStreak: StudyStatsStore.StudyStreak = StudyStatsStore.StudyStreak(
            currentDays = 2,
            bestDays = 5,
            studiedToday = true,
            reviewsToday = 1,
            lastStudyAtMillis = now,
        ),
        outcomeStats: StudyStatsStore.KaniOutcomeStats = StudyStatsStore.KaniOutcomeStats.empty(),
    ) {
        val sourceVersion = statsCache.currentSourceVersion(db)
        statsCache.write(
            db,
            StatsCacheStore.Snapshot(
                outcomeStats = outcomeStats,
                impactReport = KanjiImpactAnalyzer.Report(
                    3,
                    1,
                    2,
                    listOf(
                        KanjiImpactAnalyzer.Row.create(
                            "痛",
                            KanjiImpactAnalyzer.BUCKET_NOT_HELPING,
                            7.0,
                            7.5,
                            0.35,
                            0.42,
                            0,
                            1,
                            1,
                            2,
                            3,
                            4,
                            "Needs more focused review",
                        ),
                    ),
                ),
                generatedAtMillis = now,
                sourceVersion = sourceVersion,
                studyImpactStats = StudyStatsStore.StudyImpactStats(
                    totalReviews = 12,
                    distinctReviewedKanji = 5,
                    writingRequired = 4,
                    writingPassed = 1,
                    writingFailed = 2,
                    manualOverrides = 0,
                ),
                recentMistakes = listOf(
                    StudyStatsStore.RecentMistake("痛", "again", now - 2_000L),
                    StudyStatsStore.RecentMistake("痛", "hard", now - 1_000L),
                    StudyStatsStore.RecentMistake("弱", "again", now - 3_000L),
                ),
                studyStreak = studyStreak,
                studyTaskTimeStats = StudyStatsStore.StudyTaskTimeStats(
                    todayMillis = 180_000L,
                    lastSevenDaysMillis = 720_000L,
                    answeredTasks = 8,
                ),
                cacheFormatVersion = STATS_CACHE_FORMAT_VERSION,
                reviewDaySummaries = reviewDaySummaries,
                taskTypeDaySummaries = listOf(
                    StatsCacheStore.TaskTypeDaySummarySnapshot(now, "kanji_meaning", 2, 3),
                    StatsCacheStore.TaskTypeDaySummarySnapshot(now, "word_reading", 1, 1),
                    StatsCacheStore.TaskTypeDaySummarySnapshot(now, "write_kanji", 1, 2),
                    StatsCacheStore.TaskTypeDaySummarySnapshot(now, "similar_kanji", 1, 2),
                ),
                cumulativeKanjiPracticed = listOf(StatsCacheStore.CumulativeKanjiSnapshot(now, 5)),
                wrongPickCounts = mapOf("徴" to mapOf("微" to 2)),
                confusionMeanings = mapOf("徴" to "sign", "微" to "minute"),
            ),
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

    private class CountingProgressStatsSource(
        private val latest: StatsCacheStore.Snapshot?,
        private val recomputed: StatsCacheStore.Snapshot,
    ) : ProgressAnalyticsStatsSource {
        var cachedReads = 0
        var latestReads = 0
        val recomputeReads = mutableListOf<Long>()

        override fun cachedStatsSnapshotOrNull(): StatsCacheStore.Snapshot? {
            cachedReads += 1
            return null
        }

        override fun latestStatsSnapshotOrNull(): StatsCacheStore.Snapshot? {
            latestReads += 1
            return latest
        }

        override fun recomputeStatsSnapshotSynchronously(nowMillis: Long): StatsCacheStore.Snapshot {
            recomputeReads += nowMillis
            return recomputed
        }

        override fun reviewDaySummaries(nowMillis: Long, days: Int): List<ReviewDaySummary> {
            return emptyList()
        }
    }

    private fun progressSnapshot(
        now: Long,
        totalReviews: Int,
        cacheFormatVersion: Int,
    ): StatsCacheStore.Snapshot {
        return StatsCacheStore.Snapshot(
            outcomeStats = StudyStatsStore.KaniOutcomeStats.empty(),
            impactReport = KanjiImpactAnalyzer.Report(0, 0, 0, emptyList()),
            generatedAtMillis = now,
            sourceVersion = 1L,
            studyImpactStats = StudyStatsStore.StudyImpactStats(
                totalReviews = totalReviews,
                distinctReviewedKanji = 0,
                writingRequired = 0,
                writingPassed = 0,
                writingFailed = 0,
                manualOverrides = 0,
            ),
            cacheFormatVersion = cacheFormatVersion,
            reviewDaySummaries = cachedReviewDaySummaries(now),
        )
    }
}
