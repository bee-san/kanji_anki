package dev.bee.kanjianki.core

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object ReminderSnoozePolicy {
    private const val SNOOZE_DURATION_MILLIS: Long = 60L * 60L * 1000L

    @JvmStatic
    fun rearmTime(
        snoozeMillis: Long,
        quietStartMinuteOfDay: Int,
        quietEndMinuteOfDay: Int,
    ): Long {
        val raw = saturatingAdd(snoozeMillis, SNOOZE_DURATION_MILLIS)
        if (!ReminderAntiSpamPolicy.quietHoursActive(quietStartMinuteOfDay, quietEndMinuteOfDay)) {
            return raw
        }
        val rawMinuteOfDay = minuteOfDay(raw)
        if (isInsideQuietWindow(rawMinuteOfDay, quietStartMinuteOfDay, quietEndMinuteOfDay)) {
            return quietEndMillis(
                raw,
                rawMinuteOfDay,
                quietStartMinuteOfDay,
                quietEndMinuteOfDay,
            )
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

    private fun quietEndMillis(raw: Long, rawMinuteOfDay: Int, quietStart: Int, quietEnd: Int): Long {
        val zone = ZoneId.systemDefault()
        val rawInstant = Instant.ofEpochMilli(raw)
        val rawDate = rawInstant.atZone(zone).toLocalDate()
        val endDate = if (quietStart > quietEnd && rawMinuteOfDay >= quietStart) {
            rawDate.plusDays(1)
        } else {
            rawDate
        }
        var resolvedEnd = ZonedDateTime.of(
            endDate,
            LocalTime.of(quietEnd / 60, quietEnd % 60),
            zone,
        )
        if (!resolvedEnd.toInstant().isAfter(rawInstant)) {
            resolvedEnd = resolvedEnd.withLaterOffsetAtOverlap()
        }
        return try {
            maxOf(raw, resolvedEnd.toInstant().toEpochMilli())
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }
    }

    private fun isInsideQuietWindow(minute: Int, start: Int, end: Int): Boolean {
        return if (start < end) {
            minute in start until end
        } else {
            minute >= start || minute < end
        }
    }
}
