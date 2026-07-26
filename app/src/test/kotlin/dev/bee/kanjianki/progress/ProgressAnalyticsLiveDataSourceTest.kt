package dev.bee.kanjianki.progress

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.ChartAxisPolicy
import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.LadderCompletionForecastPolicy
import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.StatsDashboardCopy
import dev.bee.kanjianki.core.StatsValueFormatter
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
import java.util.Locale
import java.util.TimeZone

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
    fun explicitNowMillisControlsCacheFreshness() {
        val historicalNow = 1_700_000_000_000L
        writeFreshStatsSnapshot(historicalNow, cachedReviewDaySummaries(historicalNow))

        val snapshot = progressAnalyticsSnapshot(localStore!!, historicalNow)

        assertEquals(historicalNow, snapshot.generatedAtMillis)
        assertEquals(12, snapshot.overview.totalReviews.value)
    }

    @Test
    fun accuracyDeltaUsesPercentagePointsAcrossDisjointThirtyDayWindows() {
        val now = 1_780_000_000_000L
        val today = LocalDayPolicy.localDayStart(now)
        val reviewDays = (-59..0).map { offset ->
            reviewDaySnapshot(
                LocalDayPolicy.moveLocalDays(today, offset),
                total = 10,
                again = if (offset < -29) 2 else 1,
            )
        }
        writeFreshStatsSnapshot(now, reviewDays)

        val snapshot = progressAnalyticsSnapshot(localStore!!, now)

        assertEquals(90, snapshot.overview.accuracy.value)
        assertEquals("+10% vs previous 30d", snapshot.overview.accuracy.deltaLabel)
    }

    @Test
    fun cumulativeWeeklyDeltaUsesSevenInclusiveLocalDatesAndChartUsesNinetyDays() {
        val now = 1_780_000_000_000L
        val today = LocalDayPolicy.localDayStart(now)
        val oldDay = LocalDayPolicy.moveLocalDays(today, -100)
        val rangeStart = LocalDayPolicy.moveLocalDays(today, -89)
        val points = listOf(
            StatsCacheStore.CumulativeKanjiSnapshot(oldDay, 1),
            StatsCacheStore.CumulativeKanjiSnapshot(LocalDayPolicy.moveLocalDays(today, -7), 2),
            StatsCacheStore.CumulativeKanjiSnapshot(LocalDayPolicy.moveLocalDays(today, -6), 3),
            StatsCacheStore.CumulativeKanjiSnapshot(today, 8),
        )
        writeFreshStatsSnapshot(
            now,
            cachedReviewDaySummaries(now),
            cumulativeKanjiPracticed = points,
        )

        val snapshot = progressAnalyticsSnapshot(localStore!!, now)

        assertEquals("+6 this week", snapshot.overview.kanjiLearned.deltaLabel)
        assertEquals(
            StatsValueFormatter.date(rangeStart, "MMM d"),
            snapshot.progressByLevel.cumulativeProgress.xAxisLabels.first(),
        )
        assertFalse(snapshot.progressByLevel.cumulativeProgress.xAxisLabels.contains(StatsValueFormatter.date(oldDay, "MMM d")))
    }

    @Test
    fun cumulativeChartSamplesTheWholeNinetyDayWindow() {
        val now = 1_780_000_000_000L
        val today = LocalDayPolicy.localDayStart(now)
        val rangeStart = LocalDayPolicy.moveLocalDays(today, -89)
        val points = (-89..0).mapIndexed { index, offset ->
            StatsCacheStore.CumulativeKanjiSnapshot(
                LocalDayPolicy.moveLocalDays(today, offset),
                index + 1,
            )
        }
        writeFreshStatsSnapshot(
            now,
            cachedReviewDaySummaries(now),
            cumulativeKanjiPracticed = points,
        )

        val chart = progressAnalyticsSnapshot(localStore!!, now).progressByLevel.cumulativeProgress

        assertEquals(12, chart.xAxisLabels.size)
        assertEquals(StatsValueFormatter.date(rangeStart, "MMM d"), chart.xAxisLabels.first())
        assertEquals(StatsValueFormatter.date(today, "MMM d"), chart.xAxisLabels.last())
        assertEquals(1, chart.series.single().values.first())
        assertEquals(90, chart.series.single().values.last())
    }

    @Test
    fun cumulativeChartUsesElapsedLocalDaysWhenReviewHistoryIsSparse() {
        val now = 1_780_000_000_000L
        val today = LocalDayPolicy.localDayStart(now)
        val rangeStart = LocalDayPolicy.moveLocalDays(today, -89)
        val points = listOf(
            StatsCacheStore.CumulativeKanjiSnapshot(rangeStart, 1),
            StatsCacheStore.CumulativeKanjiSnapshot(LocalDayPolicy.moveLocalDays(rangeStart, 1), 2),
            StatsCacheStore.CumulativeKanjiSnapshot(today, 3),
        )
        writeFreshStatsSnapshot(
            now,
            cachedReviewDaySummaries(now),
            cumulativeKanjiPracticed = points,
        )

        val chart = progressAnalyticsSnapshot(localStore!!, now).progressByLevel.cumulativeProgress

        assertEquals(12, chart.xAxisLabels.size)
        assertEquals(
            StatsValueFormatter.date(LocalDayPolicy.moveLocalDays(rangeStart, 8), "MMM d"),
            chart.xAxisLabels[1],
        )
        assertEquals(listOf(1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3), chart.series.single().values)
    }

    @Test
    fun japaneseDatesAndUtcForecastMonthsStayLocalizedInWesternTimeZone() {
        val previousLocale = Locale.getDefault()
        val previousTimeZone = TimeZone.getDefault()
        try {
            Locale.setDefault(Locale.JAPAN)
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            val now = 1_784_635_200_000L
            val marchUtc = 1_803_859_200_000L
            val forecast = LadderCompletionForecastPolicy.Forecast(
                totalItems = 2,
                burnDown = listOf(LadderCompletionForecastPolicy.MonthPoint(marchUtc, 1, 1)),
                projectedCompletionMonthMillis = marchUtc,
                beyondHorizon = false,
                alreadyAtCeiling = 0,
                alreadyParked = 0,
                alreadyRetired = 0,
                assumptionCopyIds = emptyList(),
            )
            writeFreshStatsSnapshot(
                now,
                cachedReviewDaySummaries(now),
                ladderForecast = forecast,
            )

            val snapshot = progressAnalyticsSnapshot(localStore!!, now)

            assertTrue(snapshot.accuracyRetention.accuracyTrend.xAxisLabels.all { " " !in it })
            assertTrue(snapshot.accuracyRetention.accuracyTrend.tooltipLabel!!.contains("、"))
            assertTrue(snapshot.forecast!!.headline.contains("2027年3月"))
            assertEquals(listOf("3月"), snapshot.forecast.burnDown.xAxisLabels)
        } finally {
            Locale.setDefault(previousLocale)
            TimeZone.setDefault(previousTimeZone)
        }
    }

    @Test
    fun malformedDailyCountsAreClampedMergedAndBoundedToTheRequestedRange() {
        val now = 1_780_000_000_000L
        val today = LocalDayPolicy.localDayStart(now)
        val tomorrow = LocalDayPolicy.moveLocalDays(today, 1)
        writeFreshStatsSnapshot(
            now,
            listOf(
                reviewDaySnapshot(today, total = Int.MAX_VALUE, good = Int.MAX_VALUE),
                reviewDaySnapshot(today, total = Int.MAX_VALUE, good = Int.MAX_VALUE),
                reviewDaySnapshot(LocalDayPolicy.moveLocalDays(today, -1), total = -5, again = -3),
                reviewDaySnapshot(tomorrow, total = 20, good = 20),
            ),
        )

        val reviews = progressAnalyticsSnapshot(localStore!!, now).reviewsAnalytics

        assertEquals(Int.MAX_VALUE, reviews.totalReviews.value)
        assertEquals(Int.MAX_VALUE, reviews.correct.value)
        assertEquals(0, reviews.incorrect.value)
        assertEquals(Int.MAX_VALUE, reviews.reviewsPerDay.values.last())
        assertTrue(reviews.reviewsPerDay.values.all { it >= 0 })
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
        localStore!!.saveStudyLadderSettings(configured)
        writeFreshStatsSnapshot(
            now,
            cachedReviewDaySummaries(now),
            outcomeStats = StudyStatsStore.KaniOutcomeStats(
                StudyStatsStore.WeakKanjiImprovedMetric.empty(),
                StudyStatsStore.MatureSupportGainedMetric.empty(),
                ladderHealth,
            ),
        )
        var refreshesScheduled = 0

        val rows = progressAnalyticsSnapshot(
            localStore!!,
            now,
            scheduleRefresh = { refreshesScheduled += 1 },
        ).progressByLevel.levelRows
        val copy = StatsDashboardCopy.forLocale()

        assertEquals(0, refreshesScheduled)
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
    fun invalidatedSnapshotRecomputesSynchronouslyInsteadOfRenderingStaleStats() {
        val now = System.currentTimeMillis()
        val source = CountingProgressStatsSource(
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

        assertEquals(listOf(now), source.cachedReads)
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
        cumulativeKanjiPracticed: List<StatsCacheStore.CumulativeKanjiSnapshot> =
            listOf(StatsCacheStore.CumulativeKanjiSnapshot(now, 5)),
        ladderForecast: LadderCompletionForecastPolicy.Forecast? = null,
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
                cumulativeKanjiPracticed = cumulativeKanjiPracticed,
                wrongPickCounts = mapOf("徴" to mapOf("微" to 2)),
                confusionMeanings = mapOf("徴" to "sign", "微" to "minute"),
                ladderForecast = ladderForecast,
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
        private val recomputed: StatsCacheStore.Snapshot,
    ) : ProgressAnalyticsStatsSource {
        val cachedReads = mutableListOf<Long>()
        val recomputeReads = mutableListOf<Long>()

        override fun cachedStatsSnapshotOrNull(nowMillis: Long): StatsCacheStore.Snapshot? {
            cachedReads += nowMillis
            return null
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
