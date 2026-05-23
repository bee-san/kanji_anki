package dev.bee.kanjianki.core

import java.util.Calendar

object ReminderSchedulePolicy {
    @JvmStatic
    fun nextTriggerMillis(hour: Int, minute: Int, nowMillis: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = nowMillis
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        var trigger = calendar.timeInMillis
        if (trigger <= nowMillis) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            trigger = calendar.timeInMillis
        }
        return trigger
    }
}
