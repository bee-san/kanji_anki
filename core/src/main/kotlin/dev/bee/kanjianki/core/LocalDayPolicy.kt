package dev.bee.kanjianki.core

import java.time.DateTimeException
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.SimpleTimeZone
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
        if (toMillis <= fromMillis) {
            return 0
        }
        val fromDay = localEpochDay(fromMillis, zone)
        val toDay = localEpochDay(toMillis, zone)
        val nominalDays = toDay - fromDay
        if (nominalDays <= 0L) {
            return 0
        }
        if (zone is SimpleTimeZone && simpleTimeZoneResultDefinitelySaturates(nominalDays, zone)) {
            return Int.MAX_VALUE
        }
        val collapsedDateStarts = if (zone is SimpleTimeZone) {
            simpleTimeZoneCollapsedDateStarts(fromMillis, toMillis, zone)
        } else {
            knownZoneCollapsedDateStarts(fromMillis, toMillis, fromDay, toDay, zone)
        }
        val actualDays = nominalDays - collapsedDateStarts
        return actualDays
            .coerceIn(0L, Int.MAX_VALUE.toLong())
            .toInt()
    }

    private fun localEpochDay(millis: Long, zone: TimeZone): Long {
        val utcDay = Math.floorDiv(millis, DAY_MILLIS)
        val utcMillisOfDay = Math.floorMod(millis, DAY_MILLIS)
        val localMillisOfDay = utcMillisOfDay + zone.getOffset(millis).toLong()
        return utcDay + Math.floorDiv(localMillisOfDay, DAY_MILLIS)
    }

    private fun knownZoneCollapsedDateStarts(
        fromMillis: Long,
        toMillis: Long,
        fromDay: Long,
        toDay: Long,
        zone: TimeZone,
    ): Long {
        if (!usesPlatformTimeZoneImplementation(zone)) {
            return customTimeZoneCollapsedDateStarts(fromMillis, toMillis, zone)
        }
        val zoneId = try {
            zone.toZoneId()
        } catch (_: DateTimeException) {
            return customTimeZoneCollapsedDateStarts(fromMillis, toMillis, zone)
        }
        var collapsed = 0L
        for (transition in zoneId.rules.transitions) {
            val transitionMillis = transition.instant.toEpochMilli()
            val beforeOffset = zone.getOffset(transitionMillis - 1L).toLong()
            val afterOffset = zone.getOffset(transitionMillis).toLong()
            if (afterOffset - beforeOffset < DAY_MILLIS) {
                continue
            }
            val wallTimeBefore = transitionMillis + beforeOffset
            val wallTimeAfter = transitionMillis + afterOffset
            val firstCollapsedDay = ceilingDiv(wallTimeBefore, DAY_MILLIS)
            val lastCollapsedDay = Math.floorDiv(wallTimeAfter - DAY_MILLIS, DAY_MILLIS)
            val firstIncludedDay = maxOf(firstCollapsedDay, fromDay + 1L)
            val lastIncludedDay = minOf(lastCollapsedDay, toDay)
            if (firstIncludedDay <= lastIncludedDay) {
                collapsed += lastIncludedDay - firstIncludedDay + 1L
            }
        }
        return collapsed
    }

    private fun usesPlatformTimeZoneImplementation(zone: TimeZone): Boolean {
        return zone.javaClass == TimeZone.getTimeZone(zone.id).javaClass
    }

    private fun customTimeZoneCollapsedDateStarts(
        fromMillis: Long,
        toMillis: Long,
        zone: TimeZone,
    ): Long {
        val from = calendarDay(fromMillis, zone)
        val to = calendarDay(toMillis, zone)
        val fromYear = from.prolepticYear()
        val toYear = to.prolepticYear()
        if (fromYear == toYear) {
            return collapsedDateStartsInYear(zone, fromYear, from.dayOfYear + 1, to.dayOfYear).toLong()
        }

        var collapsed = collapsedDateStartsInYear(
            zone,
            fromYear,
            from.dayOfYear + 1,
            daysInYear(zone, fromYear),
        ).toLong()
        var year = fromYear + 1L
        while (year < toYear) {
            collapsed += collapsedDateStartsInYear(zone, year, 1, daysInYear(zone, year))
            year++
        }
        return collapsed + collapsedDateStartsInYear(zone, toYear, 1, to.dayOfYear)
    }

    private fun simpleTimeZoneResultDefinitelySaturates(nominalDays: Long, zone: SimpleTimeZone): Boolean {
        val maximumCollapsedPerYear = zone.dstSavings.toLong() / DAY_MILLIS
        if (maximumCollapsedPerYear <= 0L) {
            return nominalDays >= Int.MAX_VALUE.toLong()
        }
        val maximumSpannedYears = nominalDays / MIN_DAYS_PER_YEAR + 2L
        val maximumCollapsedDays = maximumSpannedYears * maximumCollapsedPerYear
        return nominalDays - maximumCollapsedDays >= Int.MAX_VALUE.toLong()
    }

    private fun simpleTimeZoneCollapsedDateStarts(
        fromMillis: Long,
        toMillis: Long,
        zone: SimpleTimeZone,
    ): Long {
        if (!zone.useDaylightTime() || zone.dstSavings < DAY_MILLIS) {
            return 0L
        }
        val from = calendarDay(fromMillis, zone)
        val to = calendarDay(toMillis, zone)
        val fromYear = from.prolepticYear()
        val toYear = to.prolepticYear()
        if (fromYear == toYear) {
            return collapsedDateStartsInYear(zone, toYear, from.dayOfYear + 1, to.dayOfYear).toLong()
        }

        var collapsed = collapsedDateStartsInYear(
            zone,
            fromYear,
            from.dayOfYear + 1,
            daysInYear(zone, fromYear),
        ).toLong()
        collapsed += collapsedDateStartsInYear(zone, toYear, 1, to.dayOfYear)

        val firstInteriorYear = fromYear + 1L
        val lastInteriorYear = toYear - 1L
        if (firstInteriorYear > lastInteriorYear) {
            return collapsed
        }
        val recurring = zone.clone() as SimpleTimeZone
        recurring.setStartYear(0)
        val collapsedPerActiveYear = collapsedDateStartsInYear(
            recurring,
            REFERENCE_YEAR.toLong(),
            1,
            daysInYear(recurring, REFERENCE_YEAR.toLong()),
        )
        if (collapsedPerActiveYear == 0) {
            return collapsed
        }
        val firstActiveYear = firstYearWithCollapsedDateStart(zone, firstInteriorYear, lastInteriorYear)
            ?: return collapsed
        return collapsed +
            (lastInteriorYear - firstActiveYear + 1L) * collapsedPerActiveYear.toLong()
    }

    private fun firstYearWithCollapsedDateStart(
        zone: SimpleTimeZone,
        firstYear: Long,
        lastYear: Long,
    ): Long? {
        if (!yearHasCollapsedDateStart(zone, lastYear)) {
            return null
        }
        if (yearHasCollapsedDateStart(zone, firstYear)) {
            return firstYear
        }
        var low = firstYear + 1L
        var high = lastYear
        while (low < high) {
            val middle = low + (high - low) / 2L
            if (yearHasCollapsedDateStart(zone, middle)) {
                high = middle
            } else {
                low = middle + 1L
            }
        }
        return low
    }

    private fun yearHasCollapsedDateStart(zone: SimpleTimeZone, year: Long): Boolean {
        return collapsedDateStartsInYear(zone, year, 1, daysInYear(zone, year)) > 0
    }

    private fun collapsedDateStartsInYear(
        zone: TimeZone,
        prolepticYear: Long,
        firstDayOfYear: Int,
        lastDayOfYear: Int,
    ): Int {
        if (firstDayOfYear > lastDayOfYear) {
            return 0
        }
        var previousStart = dateStart(zone, prolepticYear, firstDayOfYear - 1)
        var collapsed = 0
        for (dayOfYear in firstDayOfYear..lastDayOfYear) {
            val currentStart = dateStart(zone, prolepticYear, dayOfYear)
            if (currentStart <= previousStart) {
                collapsed += ((previousStart - currentStart) / DAY_MILLIS).toInt() + 1
            }
            previousStart = currentStart
        }
        return collapsed
    }

    private fun dateStart(zone: TimeZone, prolepticYear: Long, dayOfYear: Int): Long {
        val calendar = GregorianCalendar(zone)
        calendar.clear()
        setProlepticYear(calendar, prolepticYear)
        calendar.set(Calendar.DAY_OF_YEAR, dayOfYear)
        clearTimeOfDay(calendar)
        return calendar.timeInMillis
    }

    private fun daysInYear(zone: TimeZone, prolepticYear: Long): Int {
        val calendar = GregorianCalendar(zone)
        calendar.clear()
        setProlepticYear(calendar, prolepticYear)
        calendar.set(Calendar.DAY_OF_YEAR, 1)
        return calendar.getActualMaximum(Calendar.DAY_OF_YEAR)
    }

    private fun setProlepticYear(calendar: Calendar, prolepticYear: Long) {
        if (prolepticYear >= 1L) {
            calendar.set(Calendar.ERA, GregorianCalendar.AD)
            calendar.set(Calendar.YEAR, prolepticYear.toInt())
        } else {
            calendar.set(Calendar.ERA, GregorianCalendar.BC)
            calendar.set(Calendar.YEAR, (1L - prolepticYear).toInt())
        }
    }

    private fun calendarDay(millis: Long, zone: TimeZone): CalendarDay {
        val calendar = Calendar.getInstance(zone)
        calendar.timeInMillis = millis
        return CalendarDay(
            calendar.get(Calendar.ERA),
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.DAY_OF_YEAR),
        )
    }

    private fun ceilingDiv(value: Long, divisor: Long): Long {
        val quotient = Math.floorDiv(value, divisor)
        return if (Math.floorMod(value, divisor) == 0L) quotient else quotient + 1L
    }

    private fun clearTimeOfDay(calendar: Calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    }

    private data class CalendarDay(
        val era: Int,
        val year: Int,
        val dayOfYear: Int,
    ) {
        fun prolepticYear(): Long {
            return if (era == GregorianCalendar.AD) year.toLong() else 1L - year.toLong()
        }
    }

    private const val DAY_MILLIS = 86_400_000L
    private const val MIN_DAYS_PER_YEAR = 365L
    private const val REFERENCE_YEAR = 2000
}
