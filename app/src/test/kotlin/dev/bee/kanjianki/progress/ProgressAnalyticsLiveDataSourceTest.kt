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
    fun liveSnapshotCombinesFreshCacheWithReviewBuckets() {
        val now = System.currentTimeMillis()
        val todayStart = LocalDayPolicy.localDayStart(now)
        writeFreshStatsSnapshot(now)
        insertReview(dayStart = LocalDayPolicy.moveLocalDays(todayStart, -6), rating = "good", token = "week-good")
        insertReview(dayStart = LocalDayPolicy.moveLocalDays(todayStart, -5), rating = "again", token = "week-again")
        insertReview(dayStart = LocalDayPolicy.moveLocalDays(todayStart, -4), rating = "easy", token = "week-easy")
        insertReview(dayStart = todayStart, rating = "hard", token = "today-hard", writingRequired = true, writingPassed = false)

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
        assertEquals(1, reviews.reviewsPerDay.values.last())
        assertTrue(reviews.accessibilitySummary.contains("4 total reviews"))

        assertEquals(listOf("Meaning", "Reading", "Writing", "Similar kanji"), snapshot.overview.cardTypeBreakdown.segments.map { it.label })
        assertEquals(listOf(3, 1, 2, 2), snapshot.overview.cardTypeBreakdown.segments.map { it.value })
        assertEquals(listOf("Accuracy %", "7-day avg"), snapshot.accuracyRetention.accuracyTrend.series.map { it.label })
        assertEquals(listOf("Meaning", "Reading", "Writing", "Similar kanji"), snapshot.accuracyRetention.retentionByCardType.map { it.label })
        assertEquals(listOf("痛", "弱"), snapshot.weaknessInsights.mostMissedKanji.map { it.kanji })
        assertEquals("Needs improvement", snapshot.weaknessInsights.focusScore.status)
    }

    private fun writeFreshStatsSnapshot(now: Long) {
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
            ),
        )
    }

    private fun insertReview(
        dayStart: Long,
        rating: String,
        token: String,
        writingRequired: Boolean = false,
        writingPassed: Boolean = true,
    ) {
        db.execSQL(
            "INSERT INTO review_log " +
                "(kanji, token, rating, writing_required, writing_passed, manual_override, reviewed_at, review_day_start) " +
                "VALUES (?, ?, ?, ?, ?, 0, ?, ?)",
            arrayOf<Any>(
                token.take(1),
                token,
                rating,
                if (writingRequired) 1 else 0,
                if (writingPassed) 1 else 0,
                dayStart + 60_000L,
                dayStart,
            ),
        )
    }
}
