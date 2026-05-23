package dev.bee.kanjianki.core

import java.text.DateFormat
import java.util.Date

object DateTextPolicy {
    @JvmStatic
    fun humanSyncTime(timestampMillis: Long): String {
        return humanSyncTime(timestampMillis, System.currentTimeMillis())
    }

    @JvmStatic
    fun humanSyncTime(timestampMillis: Long, nowMillis: Long): String {
        if (timestampMillis <= 0L) {
            return "date unknown"
        }
        val date = Date(timestampMillis)
        val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
        if (LocalDayPolicy.sameLocalDay(timestampMillis, nowMillis)) {
            return "today at " + timeFormat.format(date)
        }
        val yesterday = LocalDayPolicy.moveLocalDays(LocalDayPolicy.localDayStart(nowMillis), -1)
        if (LocalDayPolicy.sameLocalDay(timestampMillis, yesterday)) {
            return "yesterday at " + timeFormat.format(date)
        }
        return shortDateTime(timestampMillis)
    }

    @JvmStatic
    fun dueText(dueAt: Long, now: Long): String {
        if (dueAt <= now) {
            return "due now"
        }
        val delta = dueAt - now
        val minutes = maxOf(1L, delta / 60_000L)
        if (minutes < 60L) {
            return "due in $minutes min"
        }
        val hours = maxOf(1L, delta / 3_600_000L)
        if (hours < 24L) {
            return "due in $hours hr"
        }
        return "due " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(dueAt))
    }

    @JvmStatic
    fun timelineDate(occurredAt: Long): String {
        if (occurredAt <= 0L) {
            return "Unknown time"
        }
        return shortDateTime(occurredAt)
    }

    @JvmStatic
    fun sameLocalDay(leftMillis: Long, rightMillis: Long): Boolean {
        return LocalDayPolicy.sameLocalDay(leftMillis, rightMillis)
    }

    @JvmStatic
    fun nextLocalDayStart(nowMillis: Long): Long {
        return LocalDayPolicy.nextLocalDayStart(nowMillis)
    }

    @JvmStatic
    fun shortDateTime(millis: Long): String {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
    }

    @JvmStatic
    fun autoUpdateLastCheckText(lastCheckAtMillis: Long): String {
        return if (lastCheckAtMillis <= 0L) "not yet" else shortDateTime(lastCheckAtMillis)
    }
}
