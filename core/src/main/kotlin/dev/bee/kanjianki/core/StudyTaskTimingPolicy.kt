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
        return Summary(todayMillis, lastSevenDaysMillis, answeredTasks)
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
        return max(0L, activeElapsedMillis) + max(0L, nowElapsedMillis - visibleSinceElapsedMillis)
    }

    @JvmStatic
    fun visibleSinceAfterResume(visibleSinceElapsedMillis: Long, nowElapsedMillis: Long): Long {
        return if (visibleSinceElapsedMillis <= 0L) nowElapsedMillis else visibleSinceElapsedMillis
    }

    class Window(
        private val todayStartMillis: Long,
        private val sevenDayStartMillis: Long,
        private val tomorrowStartMillis: Long,
    ) {
        fun todayStartMillis(): Long = todayStartMillis

        fun sevenDayStartMillis(): Long = sevenDayStartMillis

        fun tomorrowStartMillis(): Long = tomorrowStartMillis
    }

    class Summary(
        todayMillis: Long,
        lastSevenDaysMillis: Long,
        answeredTasks: Int,
    ) {
        private val todayMillis = max(0L, todayMillis)
        private val lastSevenDaysMillis = max(0L, lastSevenDaysMillis)
        private val answeredTasks = max(0, answeredTasks)

        fun todayMillis(): Long = todayMillis

        fun lastSevenDaysMillis(): Long = lastSevenDaysMillis

        fun answeredTasks(): Int = answeredTasks

        fun averageMillisPerTask(): Long {
            if (answeredTasks == 0) {
                return 0L
            }
            return lastSevenDaysMillis / answeredTasks
        }
    }
}
