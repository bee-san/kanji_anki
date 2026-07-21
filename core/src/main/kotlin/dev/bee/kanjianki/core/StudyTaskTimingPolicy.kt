package dev.bee.kanjianki.core

import kotlin.math.max
import kotlin.math.min

object StudyTaskTimingPolicy {
    @JvmStatic
    fun windowFor(nowMillis: Long): Window {
        val today = LocalDayPolicy.localDayStart(nowMillis)
        return Window(today, LocalDayPolicy.moveLocalDays(today, -6), LocalDayPolicy.moveLocalDays(today, 1))
    }

    @JvmStatic
    fun summarize(todayMillis: Long, lastSevenDaysMillis: Long, answeredTasks: Int): Summary {
        return Summary(max(0L, todayMillis), max(0L, lastSevenDaysMillis), max(0, answeredTasks))
    }

    @JvmStatic
    fun boundedElapsed(activeElapsedMillis: Long, maxElapsedMillis: Long): Long {
        return min(max(0L, maxElapsedMillis), max(0L, activeElapsedMillis))
    }

    @JvmStatic
    fun elapsedAfterPause(
        activeElapsedMillis: Long,
        visibleSinceElapsedMillis: Long,
        nowElapsedMillis: Long,
    ): Long {
        if (visibleSinceElapsedMillis <= 0L) {
            return max(0L, activeElapsedMillis)
        }
        return saturatingAdd(
            max(0L, activeElapsedMillis),
            nonNegativeDifference(nowElapsedMillis, visibleSinceElapsedMillis),
        )
    }

    @JvmStatic
    fun visibleSinceAfterResume(visibleSinceElapsedMillis: Long, nowElapsedMillis: Long): Long {
        return if (visibleSinceElapsedMillis <= 0L) nowElapsedMillis else visibleSinceElapsedMillis
    }

    @JvmRecord
    data class Window(
        val todayStartMillis: Long,
        val sevenDayStartMillis: Long,
        val tomorrowStartMillis: Long,
    )

    @JvmRecord
    data class Summary(
        val todayMillis: Long,
        val lastSevenDaysMillis: Long,
        val answeredTasks: Int,
    ) {
        fun averageMillisPerTask(): Long {
            if (answeredTasks == 0) {
                return 0L
            }
            return lastSevenDaysMillis / answeredTasks
        }
    }
}
