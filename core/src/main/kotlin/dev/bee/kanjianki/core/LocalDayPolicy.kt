package dev.bee.kanjianki.core

import java.util.Calendar

object LocalDayPolicy {
    @JvmStatic
    fun localDayStart(millis: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = millis
        clearTimeOfDay(calendar)
        return calendar.timeInMillis
    }

    @JvmStatic
    fun moveLocalDays(localDayStart: Long, days: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = localDayStart
        calendar.add(Calendar.DAY_OF_YEAR, days)
        clearTimeOfDay(calendar)
        return calendar.timeInMillis
    }

    @JvmStatic
    fun nextLocalDayStart(millis: Long): Long {
        return moveLocalDays(localDayStart(millis), 1)
    }

    @JvmStatic
    fun sameLocalDay(leftMillis: Long, rightMillis: Long): Boolean {
        val left = Calendar.getInstance()
        left.timeInMillis = leftMillis
        val right = Calendar.getInstance()
        right.timeInMillis = rightMillis
        return left.get(Calendar.ERA) == right.get(Calendar.ERA) &&
            left.get(Calendar.YEAR) == right.get(Calendar.YEAR) &&
            left.get(Calendar.DAY_OF_YEAR) == right.get(Calendar.DAY_OF_YEAR)
    }

    private fun clearTimeOfDay(calendar: Calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    }
}
