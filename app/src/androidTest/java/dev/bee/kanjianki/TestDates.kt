package dev.bee.kanjianki

import dev.bee.kanjianki.core.LocalDayPolicy

object TestDates {
    @JvmStatic
    fun localDayStart(millis: Long): Long = LocalDayPolicy.localDayStart(millis)

    @JvmStatic
    fun moveLocalDays(localDayStart: Long, days: Int): Long =
        LocalDayPolicy.moveLocalDays(localDayStart, days)
}
