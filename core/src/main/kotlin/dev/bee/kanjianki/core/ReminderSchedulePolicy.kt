package dev.bee.kanjianki.core

import java.util.Calendar

object ReminderSchedulePolicy {
    private const val REVIEW_CUTOFF_HOUR = 22

    @JvmStatic
    fun nextTriggerMillis(hour: Int, minute: Int, nowMillis: Long): Long {
        return nextDailyTriggerMillis(hour, minute, nowMillis, allowToday = true)
    }

    @JvmStatic
    fun nextTriggerMillis(hour: Int, minute: Int, nowMillis: Long, allowToday: Boolean): Long {
        return nextDailyTriggerMillis(hour, minute, nowMillis, allowToday)
    }

    @JvmStatic
    fun nextTriggerMillis(
        hour: Int,
        minute: Int,
        nowMillis: Long,
        studiedToday: Boolean,
        dueAtMillis: Iterable<Long>,
    ): Long {
        if (!studiedToday) {
            return nextDailyTriggerMillis(hour, minute, nowMillis, allowToday = true)
        }
        val cutoffMillis = localTimeMillis(nowMillis, REVIEW_CUTOFF_HOUR, 0)
        if (nowMillis >= cutoffMillis) {
            return nextDailyTriggerMillis(hour, minute, nowMillis, allowToday = false)
        }
        val latestDueMillis = dueAtMillis.asSequence()
            .filter { it > nowMillis && it < cutoffMillis }
            .maxOrNull()
        if (latestDueMillis == null) {
            return nextDailyTriggerMillis(hour, minute, nowMillis, allowToday = false)
        }
        return maxOf(nowMillis, latestDueMillis)
    }

    private fun nextDailyTriggerMillis(hour: Int, minute: Int, nowMillis: Long, allowToday: Boolean): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = nowMillis
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        if (!allowToday || calendar.timeInMillis <= nowMillis) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    private fun localTimeMillis(referenceMillis: Long, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = referenceMillis
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
