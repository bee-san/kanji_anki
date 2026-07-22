package dev.bee.kanjianki.core

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

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
        val zone = ZoneId.systemDefault()
        val now = Instant.ofEpochMilli(nowMillis)
        val localNow = now.atZone(zone)
        val reminderTime = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        var trigger = ZonedDateTime.of(localNow.toLocalDate(), reminderTime, zone)
        if (!allowToday || !trigger.toInstant().isAfter(now)) {
            trigger = ZonedDateTime.of(localNow.toLocalDate().plusDays(1), reminderTime, zone)
        }
        return trigger.toInstant().toEpochMilli()
    }

    private fun localTimeMillis(referenceMillis: Long, hour: Int, minute: Int): Long {
        val zone = ZoneId.systemDefault()
        val localDate = Instant.ofEpochMilli(referenceMillis).atZone(zone).toLocalDate()
        return ZonedDateTime.of(
            localDate,
            LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)),
            zone,
        ).toInstant().toEpochMilli()
    }
}
