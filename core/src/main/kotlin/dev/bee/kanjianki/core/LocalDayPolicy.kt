package dev.bee.kanjianki.core

import java.util.Calendar
import java.util.TimeZone

object LocalDayPolicy {
    @JvmStatic
    @JvmOverloads
    fun localDayStart(millis: Long, zone: TimeZone = TimeZone.getDefault()): Long {
        val calendar = Calendar.getInstance(zone)
        calendar.timeInMillis = millis
        clearTimeOfDay(calendar)
        return calendar.timeInMillis
    }

    @JvmStatic
    @JvmOverloads
    fun moveLocalDays(localDayStart: Long, days: Int, zone: TimeZone = TimeZone.getDefault()): Long {
        val calendar = Calendar.getInstance(zone)
        calendar.timeInMillis = localDayStart
        calendar.add(Calendar.DAY_OF_YEAR, days)
        clearTimeOfDay(calendar)
        return calendar.timeInMillis
    }

    @JvmStatic
    @JvmOverloads
    fun nextLocalDayStart(millis: Long, zone: TimeZone = TimeZone.getDefault()): Long {
        return moveLocalDays(localDayStart(millis, zone), 1, zone)
    }

    @JvmStatic
    @JvmOverloads
    fun sameLocalDay(leftMillis: Long, rightMillis: Long, zone: TimeZone = TimeZone.getDefault()): Boolean {
        val left = Calendar.getInstance(zone)
        left.timeInMillis = leftMillis
        val right = Calendar.getInstance(zone)
        right.timeInMillis = rightMillis
        return left.get(Calendar.ERA) == right.get(Calendar.ERA) &&
            left.get(Calendar.YEAR) == right.get(Calendar.YEAR) &&
            left.get(Calendar.DAY_OF_YEAR) == right.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * Number of local calendar days between the local-day-start of [fromMillis] and the
     * local-day-start of [toMillis], counting day boundaries rather than 24-hour blocks.
     * Clamps to 0 for a backwards clock. This matches Anki's collection-day elapsed
     * accounting, where a review slightly before a full day still elapses one day.
     */
    @JvmStatic
    @JvmOverloads
    fun localDaysBetween(fromMillis: Long, toMillis: Long, zone: TimeZone = TimeZone.getDefault()): Int {
        val fromStart = localDayStart(fromMillis, zone)
        val toStart = localDayStart(toMillis, zone)
        if (toStart <= fromStart) {
            return 0
        }
        // Walk day boundaries so DST transitions (23h/25h days) still count as one day
        // each rather than being over/under-counted by a fixed 86.4M-ms divisor.
        var days = 0
        var cursor = fromStart
        while (cursor < toStart) {
            cursor = moveLocalDays(cursor, 1, zone)
            days++
        }
        return days
    }

    private fun clearTimeOfDay(calendar: Calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    }
}
