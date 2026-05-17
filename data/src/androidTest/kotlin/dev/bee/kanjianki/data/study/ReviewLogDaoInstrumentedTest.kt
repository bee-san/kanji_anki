package dev.bee.kanjianki.data.study

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bee.kanjianki.data.KaniRoomDatabase
import dev.bee.kanjianki.data.repository.RoomStudyReviewStatsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class ReviewLogDaoInstrumentedTest {
    private lateinit var database: KaniRoomDatabase
    private lateinit var dao: ReviewLogDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KaniRoomDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.reviewLogDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aggregateQueriesUseRoomSql() = runBlocking {
        dao.insert(log("日", "again", 100L, writingRequired = 1, writingPassed = 0))
        dao.insert(log("本", "hard", 110L, writingRequired = 1, writingPassed = 1))
        dao.insert(log("火", "good", 120L))
        dao.insert(log("水", "easy", 130L, writingRequired = 1, writingPassed = 0, manualOverride = 1))
        dao.insert(log("古", "again", 10L, writingRequired = 1, writingPassed = 0))

        val stats = dao.reviewStatsSince(100L)
        val studiedKanji = dao.distinctKanjiSince(100L).toSet()

        assertEquals(4, stats.total)
        assertEquals(1, stats.again)
        assertEquals(1, stats.hard)
        assertEquals(1, stats.good)
        assertEquals(1, stats.easy)
        assertEquals(3, stats.writingRequired)
        assertEquals(1, stats.writingFailed)
        assertEquals(setOf("日", "本", "火", "水"), studiedKanji)
    }

    @Test
    fun streakRepositoryReadsRoomDayAggregates() = runBlocking {
        val today = localDayStart(1_700_000_000_000L)
        dao.insert(log("日", "good", today + 1_000L, reviewDayStart = today))
        dao.insert(log("本", "hard", today + 2_000L, reviewDayStart = today))
        dao.insert(log("火", "good", today - DAY_MILLIS + 3_000L, reviewDayStart = moveLocalDays(today, -1)))
        dao.insert(log("水", "good", today - 3 * DAY_MILLIS + 4_000L, reviewDayStart = moveLocalDays(today, -3)))
        dao.insert(log("土", "good", today - 4 * DAY_MILLIS + 5_000L, reviewDayStart = moveLocalDays(today, -4)))

        val days = dao.listReviewDaysDescending()
        val streak = RoomStudyReviewStatsRepository(dao).studyStreak(today + 6_000L)

        assertEquals(listOf(today, moveLocalDays(today, -1), moveLocalDays(today, -3), moveLocalDays(today, -4)), days.map { it.dayStart })
        assertEquals(2, streak.currentDays)
        assertEquals(2, streak.bestDays)
        assertTrue(streak.studiedToday)
        assertEquals(2, streak.reviewsToday)
        assertEquals(today + 2_000L, streak.lastStudyAtMillis)
    }

    @Test
    fun emptyRoomReviewLogReturnsEmptyStreak() = runBlocking {
        val streak = RoomStudyReviewStatsRepository(dao).studyStreak(1_700_000_000_000L)

        assertEquals(0, streak.currentDays)
        assertEquals(0, streak.bestDays)
        assertFalse(streak.studiedToday)
        assertEquals(0, streak.reviewsToday)
        assertEquals(0L, streak.lastStudyAtMillis)
    }

    private companion object {
        const val DAY_MILLIS = 86_400_000L

        fun log(
            kanji: String,
            rating: String,
            reviewedAt: Long,
            reviewDayStart: Long = localDayStart(reviewedAt),
            writingRequired: Int = 0,
            writingPassed: Int = 0,
            manualOverride: Int = 0,
        ): ReviewLogEntity = ReviewLogEntity(
            kanji = kanji,
            token = "token-$kanji-$reviewedAt",
            rating = rating,
            writingRequired = writingRequired,
            writingPassed = writingPassed,
            manualOverride = manualOverride,
            reviewedAt = reviewedAt,
            reviewDayStart = reviewDayStart,
            taskType = "",
            answerSignature = "",
            prompt = "",
            hintsUsed = 0,
            writingClean = 0,
            memoryBefore = "",
            memoryAfter = "",
            schedulerStateAfterJson = "",
        )

        fun localDayStart(millis: Long): Long {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = millis
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar.timeInMillis
        }

        fun moveLocalDays(dayStartMillis: Long, days: Int): Long {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = dayStartMillis
            calendar.add(Calendar.DAY_OF_YEAR, days)
            return localDayStart(calendar.timeInMillis)
        }
    }
}
