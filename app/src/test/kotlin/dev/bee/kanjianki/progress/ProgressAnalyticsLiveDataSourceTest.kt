package dev.bee.kanjianki.progress

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreSchema
import dev.bee.kanjianki.data.STATS_CACHE_FORMAT_VERSION
import dev.bee.kanjianki.data.StatsCacheStore
import dev.bee.kanjianki.data.StudyStatsStore
import org.junit.After
import org.junit.Assert.assertEquals
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
        assertEquals("5 / 6", snapshot.progressByLevel.overallLearned.valueLabel)
        assertEquals(83, snapshot.progressByLevel.overallLearned.percent)

        val reviews = snapshot.reviewsAnalytics
        assertEquals(4, reviews.totalReviews.value)
        assertEquals(3, reviews.correct.value)
        assertEquals(1, reviews.incorrect.value)
        assertEquals(listOf(1, 1, 1, 0, 0, 0, 1), reviews.reviewsPerDay.values)
        assertEquals(1, reviews.reviewsPerDay.values.last())
        assertTrue(reviews.accessibilitySummary.contains("4 total reviews"))

        assertEquals(listOf("Meaning", "Reading", "Writing", "Similar kanji"), snapshot.overview.cardTypeBreakdown.segments.map { it.label })
        assertEquals(listOf(3, 1, 2, 2), snapshot.overview.cardTypeBreakdown.segments.map { it.value })
        assertEquals(listOf("Accuracy %", "7-day avg"), snapshot.accuracyRetention.accuracyTrend.series.map { it.label })
        assertEquals(listOf("Meaning", "Reading", "Writing", "Similar kanji"), snapshot.accuracyRetention.retentionByCardType.map { it.label })
        assertEquals(listOf("痛", "弱"), snapshot.weaknessInsights.mostMissedKanji.map { it.kanji })
        assertEquals("Needs improvement", snapshot.weaknessInsights.focusScore.status)
    }

    private fun writeFreshStatsSnapshot(now: Long, reviewDaySummaries: List<StatsCacheStore.ReviewDaySummarySnapshot>) {
        val sourceVersion = statsCache.currentSourceVersion(db)
        statsCache.write(
            db,
            StatsCacheStore.Snapshot(
                outcomeStats = StudyStatsStore.KaniOutcomeStats.empty(),
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
                studyStreak = StudyStatsStore.StudyStreak(
                    currentDays = 2,
                    bestDays = 5,
                    studiedToday = true,
                    reviewsToday = 1,
                    lastStudyAtMillis = now,
                ),
                studyTaskTimeStats = StudyStatsStore.StudyTaskTimeStats(
                    todayMillis = 180_000L,
                    lastSevenDaysMillis = 720_000L,
                    answeredTasks = 8,
                ),
                cacheFormatVersion = STATS_CACHE_FORMAT_VERSION,
                reviewDaySummaries = reviewDaySummaries,
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
}
