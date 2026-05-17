package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.study.ReviewLogDao
import dev.bee.kanjianki.domain.repository.StudyReviewStatsRepository
import dev.bee.kanjianki.domain.scheduler.AdaptiveReviewStats
import java.util.Calendar

class RoomStudyReviewStatsRepository(
    private val reviewLogs: ReviewLogDao,
) : StudyReviewStatsRepository {
    override suspend fun reviewStatsSince(sinceMillis: Long): AdaptiveReviewStats {
        val logs = reviewLogs.listSince(sinceMillis)
        return AdaptiveReviewStats(
            total = logs.size,
            again = logs.count { it.rating == RATING_AGAIN },
            hard = logs.count { it.rating == RATING_HARD },
            good = logs.count { it.rating !in nonGoodRatings },
            easy = logs.count { it.rating == RATING_EASY },
            writingRequired = logs.count { it.writingRequired != 0 },
            writingFailed = logs.count {
                it.writingRequired != 0 && it.writingPassed == 0 && it.manualOverride == 0
            },
        )
    }

    override suspend fun studiedKanjiSince(sinceMillis: Long): Set<String> =
        reviewLogs.listSince(sinceMillis)
            .mapTo(linkedSetOf()) { it.kanji }

    override suspend fun currentStreakDays(nowMillis: Long): Int {
        val today = localDayStart(nowMillis)
        val days = reviewLogs.listSince(0L)
            .map { it.reviewDayStart }
            .filter { it > 0L }
            .distinct()
            .sortedDescending()
        if (days.isEmpty()) {
            return 0
        }
        val yesterday = moveLocalDays(today, -1)
        val startsToday = days.first() == today
        if (!startsToday && days.first() != yesterday) {
            return 0
        }
        var expected = if (startsToday) today else yesterday
        var current = 0
        for (day in days) {
            if (day != expected) {
                break
            }
            current++
            expected = moveLocalDays(expected, -1)
        }
        return current
    }

    private companion object {
        const val RATING_AGAIN = "again"
        const val RATING_HARD = "hard"
        const val RATING_EASY = "easy"
        val nonGoodRatings = setOf(RATING_AGAIN, RATING_HARD, RATING_EASY)

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
