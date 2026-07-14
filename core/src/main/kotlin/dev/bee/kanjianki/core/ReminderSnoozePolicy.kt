package dev.bee.kanjianki.core

object ReminderSnoozePolicy {
    private const val SNOOZE_DURATION_MILLIS: Long = 60L * 60L * 1000L
    private const val MINUTES_PER_DAY = 24 * 60

    @JvmStatic
    fun rearmTime(
        snoozeMillis: Long,
        quietStartMinuteOfDay: Int,
        quietEndMinuteOfDay: Int,
    ): Long {
        val raw = snoozeMillis + SNOOZE_DURATION_MILLIS
        if (!ReminderAntiSpamPolicy.quietHoursActive(quietStartMinuteOfDay, quietEndMinuteOfDay)) {
            return raw
        }
        val rawMinuteOfDay = minuteOfDay(raw)
        if (isInsideQuietWindow(rawMinuteOfDay, quietStartMinuteOfDay, quietEndMinuteOfDay)) {
            val minutesPastStart = (rawMinuteOfDay - quietEndMinuteOfDay + MINUTES_PER_DAY) % MINUTES_PER_DAY
            val minutesToEnd = (quietEndMinuteOfDay - rawMinuteOfDay + MINUTES_PER_DAY) % MINUTES_PER_DAY
            return raw + minutesToEnd.toLong() * 60L * 1000L
        }
        return raw
    }

    @JvmStatic
    fun isRepostAllowed(
        postsTodayCount: Int,
        maxPerDay: Int,
    ): Boolean {
        return postsTodayCount < maxPerDay
    }

    private fun minuteOfDay(millis: Long): Int {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = millis
        return calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
    }

    private fun isInsideQuietWindow(minute: Int, start: Int, end: Int): Boolean {
        return if (start < end) {
            minute in start until end
        } else {
            minute >= start || minute < end
        }
    }
}
