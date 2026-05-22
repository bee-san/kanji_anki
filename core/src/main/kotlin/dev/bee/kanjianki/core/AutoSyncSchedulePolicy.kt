package dev.bee.kanjianki.core

import java.util.Calendar
import kotlin.math.max

object AutoSyncSchedulePolicy {
    const val MIN_DELAY_MILLIS: Long = 10_000L
    const val DEADLINE_WINDOW_MILLIS: Long = 6L * 60L * 60L * 1000L

    @JvmStatic
    fun plan(
        enabled: Boolean,
        hour: Int,
        minute: Int,
        nowMillis: Long,
        alreadySyncedToday: Boolean,
    ): SchedulePlan {
        if (!enabled) {
            return SchedulePlan.disabled()
        }
        val triggerAt = nextTriggerMillis(hour, minute, nowMillis, alreadySyncedToday)
        val minimumLatency = max(MIN_DELAY_MILLIS, triggerAt - nowMillis)
        return planWithLatency(triggerAt, minimumLatency)
    }

    @JvmStatic
    fun planAt(triggerAtMillis: Long, nowMillis: Long): SchedulePlan {
        return planWithLatency(triggerAtMillis, max(MIN_DELAY_MILLIS, triggerAtMillis - nowMillis))
    }

    @JvmStatic
    fun nextTriggerMillis(hour: Int, minute: Int, nowMillis: Long): Long {
        return nextTriggerMillis(hour, minute, nowMillis, false)
    }

    @JvmStatic
    fun nextTriggerMillis(hour: Int, minute: Int, nowMillis: Long, alreadySyncedToday: Boolean): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = nowMillis
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        var trigger = calendar.timeInMillis
        if (trigger <= nowMillis || alreadySyncedToday) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            trigger = calendar.timeInMillis
        }
        return trigger
    }

    @JvmStatic
    fun localDayStart(nowMillis: Long): Long {
        return LocalDayPolicy.localDayStart(nowMillis)
    }

    private fun planWithLatency(triggerAtMillis: Long, minimumLatencyMillis: Long): SchedulePlan {
        return SchedulePlan(
            true,
            triggerAtMillis,
            minimumLatencyMillis,
            minimumLatencyMillis + DEADLINE_WINDOW_MILLIS,
        )
    }

    @JvmRecord
    data class SchedulePlan(
        val enabled: Boolean,
        val triggerAtMillis: Long,
        val minimumLatencyMillis: Long,
        val overrideDeadlineMillis: Long,
    ) {
        override fun toString(): String {
            return "SchedulePlan[enabled=$enabled, triggerAtMillis=$triggerAtMillis, " +
                "minimumLatencyMillis=$minimumLatencyMillis, overrideDeadlineMillis=$overrideDeadlineMillis]"
        }

        companion object {
            @JvmStatic
            fun disabled(): SchedulePlan {
                return SchedulePlan(false, 0L, 0L, 0L)
            }
        }
    }
}
