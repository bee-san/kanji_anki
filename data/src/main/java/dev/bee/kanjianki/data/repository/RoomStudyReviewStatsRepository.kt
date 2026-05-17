package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.study.ReviewLogDao
import dev.bee.kanjianki.data.study.ReviewDayAggregate
import dev.bee.kanjianki.data.study.ReviewStatsAggregate
import dev.bee.kanjianki.domain.repository.StudyReviewStatsRepository
import dev.bee.kanjianki.domain.repository.StudyStreak
import dev.bee.kanjianki.domain.scheduler.AdaptiveReviewStats
import java.util.Calendar

class RoomStudyReviewStatsRepository(
    private val reviewLogs: ReviewLogDao,
) : StudyReviewStatsRepository {
    override suspend fun reviewStatsSince(sinceMillis: Long): AdaptiveReviewStats {
        return reviewLogs.reviewStatsSince(sinceMillis).toDomain()
    }

    override suspend fun studiedKanjiSince(sinceMillis: Long): Set<String> =
        reviewLogs.distinctKanjiSince(sinceMillis).toSet()

    override suspend fun studyStreak(nowMillis: Long): StudyStreak {
        val today = localDayStart(nowMillis)
        val days = reviewLogs.listReviewDaysDescending()
        if (days.isEmpty()) {
            return StudyStreak.empty
        }
        val studiedToday = days.first().dayStart == today
        return StudyStreak(
            currentDays = currentStreak(days, today),
            bestDays = bestStreak(days),
            studiedToday = studiedToday,
            reviewsToday = days.firstOrNull { it.dayStart == today }?.reviewCount ?: 0,
            lastStudyAtMillis = days.first().lastReviewedAt,
        )
    }

    private fun ReviewStatsAggregate.toDomain(): AdaptiveReviewStats =
        AdaptiveReviewStats(
            total = total,
            again = again,
            hard = hard,
            good = good,
            easy = easy,
            writingRequired = writingRequired,
            writingFailed = writingFailed,
        )

    private fun currentStreak(days: List<ReviewDayAggregate>, today: Long): Int {
        val yesterday = moveLocalDays(today, -1)
        val firstDay = days.first().dayStart
        if (firstDay != today && firstDay != yesterday) {
            return 0
        }
        var expected = if (firstDay == today) today else yesterday
        var current = 0
        for (day in days) {
            if (day.dayStart != expected) {
                break
            }
            current++
            expected = moveLocalDays(expected, -1)
        }
        return current
    }

    private fun bestStreak(days: List<ReviewDayAggregate>): Int {
        var best = 0
        var run = 0
        var expectedPrevious = Long.MIN_VALUE
        for (index in days.indices.reversed()) {
            val day = days[index].dayStart
            run = if (run == 0 || day == moveLocalDays(expectedPrevious, 1)) {
                run + 1
            } else {
                1
            }
            best = maxOf(best, run)
            expectedPrevious = day
        }
        return best
    }

    private companion object {
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
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar.timeInMillis
        }
    }
}
