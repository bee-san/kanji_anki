package dev.bee.kanjianki.core

import kotlin.math.max

object StudyStreakPolicy {
    @JvmStatic
    fun summarize(
        daysDescending: List<Long>?,
        today: Long,
        reviewsToday: Int,
        lastStudyAtMillis: Long,
    ): Streak {
        val days = daysDescending ?: emptyList()
        if (days.isEmpty()) {
            return Streak(0, 0, false, 0, 0L)
        }
        val studiedToday = days[0] == today
        return Streak(
            currentStreak(days, today),
            bestStreak(days),
            studiedToday,
            reviewsToday,
            lastStudyAtMillis,
        )
    }

    private fun currentStreak(days: List<Long>, today: Long): Int {
        val yesterday = LocalDayPolicy.moveLocalDays(today, -1)
        val studiedToday = days[0] == today
        if (!studiedToday && days[0] != yesterday) {
            return 0
        }
        var expected = if (studiedToday) today else yesterday
        var current = 0
        for (day in days) {
            if (day != expected) {
                break
            }
            current++
            expected = LocalDayPolicy.moveLocalDays(expected, -1)
        }
        return current
    }

    private fun bestStreak(days: List<Long>): Int {
        var best = 0
        var run = 0
        var expectedPrevious = Long.MIN_VALUE
        for (index in days.indices.reversed()) {
            val day = days[index]
            if (run == 0 || day == LocalDayPolicy.moveLocalDays(expectedPrevious, 1)) {
                run++
            } else {
                run = 1
            }
            best = max(best, run)
            expectedPrevious = day
        }
        return best
    }

    @JvmRecord
    data class Streak(
        val currentDays: Int,
        val bestDays: Int,
        val studiedToday: Boolean,
        val reviewsToday: Int,
        val lastStudyAtMillis: Long,
    )
}
