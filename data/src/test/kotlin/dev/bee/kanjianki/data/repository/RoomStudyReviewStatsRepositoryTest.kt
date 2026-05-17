package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.study.ReviewLogDao
import dev.bee.kanjianki.data.study.ReviewLogEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class RoomStudyReviewStatsRepositoryTest {
    @Test
    fun reviewStatsSinceMatchesLegacyRatingAndWritingBuckets() = runBlocking {
        val repository = RoomStudyReviewStatsRepository(
            FakeReviewLogDao(
                log("日", "again", reviewedAt = 100L, writingRequired = 1, writingPassed = 0),
                log("本", "hard", reviewedAt = 110L, writingRequired = 1, writingPassed = 1),
                log("火", "good", reviewedAt = 120L),
                log("水", "easy", reviewedAt = 130L, writingRequired = 1, writingPassed = 0, manualOverride = 1),
                log("古", "again", reviewedAt = 10L, writingRequired = 1, writingPassed = 0),
            ),
        )

        val stats = repository.reviewStatsSince(100L)

        assertEquals(4, stats.total)
        assertEquals(1, stats.again)
        assertEquals(1, stats.hard)
        assertEquals(1, stats.good)
        assertEquals(1, stats.easy)
        assertEquals(3, stats.writingRequired)
        assertEquals(1, stats.writingFailed)
    }

    @Test
    fun studiedKanjiSinceReturnsDistinctKanjiInReviewOrder() = runBlocking {
        val repository = RoomStudyReviewStatsRepository(
            FakeReviewLogDao(
                log("日", "good", reviewedAt = 100L),
                log("本", "good", reviewedAt = 110L),
                log("日", "hard", reviewedAt = 120L),
                log("古", "good", reviewedAt = 10L),
            ),
        )

        assertEquals(setOf("日", "本"), repository.studiedKanjiSince(100L))
    }

    @Test
    fun currentStreakCountsConsecutiveReviewDaysFromTodayOrYesterday() = runBlocking {
        val today = localDayStart(1_700_000_000_000L)
        val repository = RoomStudyReviewStatsRepository(
            FakeReviewLogDao(
                log("日", "good", reviewedAt = today + 1_000L, reviewDayStart = today),
                log("本", "good", reviewedAt = today - DAY_MILLIS + 2_000L, reviewDayStart = moveLocalDays(today, -1)),
                log("火", "good", reviewedAt = today - 2 * DAY_MILLIS + 3_000L, reviewDayStart = moveLocalDays(today, -2)),
                log("水", "good", reviewedAt = today - 4 * DAY_MILLIS + 4_000L, reviewDayStart = moveLocalDays(today, -4)),
            ),
        )

        assertEquals(3, repository.currentStreakDays(today + 5_000L))
    }

    @Test
    fun currentStreakCanContinueFromYesterdayButStopsAfterAGap() = runBlocking {
        val today = localDayStart(1_700_000_000_000L)
        val continuing = RoomStudyReviewStatsRepository(
            FakeReviewLogDao(
                log("日", "good", reviewedAt = today - DAY_MILLIS + 1_000L, reviewDayStart = moveLocalDays(today, -1)),
                log("本", "good", reviewedAt = today - 2 * DAY_MILLIS + 2_000L, reviewDayStart = moveLocalDays(today, -2)),
            ),
        )
        val stale = RoomStudyReviewStatsRepository(
            FakeReviewLogDao(
                log("日", "good", reviewedAt = today - 2 * DAY_MILLIS + 1_000L, reviewDayStart = moveLocalDays(today, -2)),
            ),
        )

        assertEquals(2, continuing.currentStreakDays(today + 5_000L))
        assertEquals(0, stale.currentStreakDays(today + 5_000L))
    }

    private class FakeReviewLogDao(
        private vararg val logs: ReviewLogEntity,
    ) : ReviewLogDao {
        override suspend fun listForKanji(kanji: String): List<ReviewLogEntity> =
            logs.filter { it.kanji == kanji }

        override suspend fun listSince(fromMillis: Long): List<ReviewLogEntity> =
            logs.filter { it.reviewedAt >= fromMillis }.sortedWith(compareBy({ it.reviewedAt }, { it.id ?: 0L }))

        override suspend fun insert(log: ReviewLogEntity): Long = -1L
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
            id = reviewedAt,
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
            return calendar.timeInMillis
        }
    }
}
