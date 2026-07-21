package dev.bee.kanjianki.core

import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ReviewHeatmapPolicy {
    data class DaySummary(val dayStartMillis: Long, val reviews: Int)
    data class Cell(val dayStartMillis: Long, val reviews: Int, val intensity: Int)
    data class Week(val cells: List<Cell>, val monthLabel: String?)
    data class Grid(
        val weeks: List<Week>,
        val weekdayLabels: List<String>,
        val accessibilitySummary: String,
    )

    @JvmStatic
    @JvmOverloads
    fun build(
        summaries: List<DaySummary>?,
        nowMillis: Long,
        zone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault(),
    ): Grid {
        val today = LocalDayPolicy.localDayStart(nowMillis, zone)
        val firstDay = LocalDayPolicy.moveLocalDays(today, -364, zone)
        val firstCalendar = Calendar.getInstance(zone).apply { timeInMillis = firstDay }
        val leading = (firstCalendar.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY + 7) % 7
        val gridStart = LocalDayPolicy.moveLocalDays(firstDay, -leading, zone)
        val todayCalendar = Calendar.getInstance(zone).apply { timeInMillis = today }
        val trailing = (Calendar.SATURDAY - todayCalendar.get(Calendar.DAY_OF_WEEK) + 7) % 7
        val gridEnd = LocalDayPolicy.moveLocalDays(today, trailing, zone)
        val byDay = LinkedHashMap<Long, Int>()
        for (summary in summaries.orEmpty()) {
            byDay.merge(summary.dayStartMillis, summary.reviews.coerceAtLeast(0), ::saturatingAddNonNegative)
        }
        val nonZero = byDay.filterKeys { it in firstDay..today }.values.filter { it > 0 }.sorted()
        val cells = ArrayList<Cell>()
        var cursor = gridStart
        while (cursor <= gridEnd) {
            val reviews = if (cursor in firstDay..today) byDay[cursor] ?: 0 else 0
            cells += Cell(cursor, reviews, intensity(reviews, nonZero))
            cursor = LocalDayPolicy.moveLocalDays(cursor, 1, zone)
        }
        val monthFormat = SimpleDateFormat("MMM", locale).apply { timeZone = zone }
        var previousMonth = -1
        val weeks = cells.chunked(7).map { weekCells ->
            val visible = weekCells.firstOrNull { it.dayStartMillis >= firstDay }
            val calendar = Calendar.getInstance(zone).apply { timeInMillis = visible?.dayStartMillis ?: weekCells.first().dayStartMillis }
            val month = calendar.get(Calendar.MONTH)
            val label = if (month != previousMonth) monthFormat.format(calendar.time) else null
            previousMonth = month
            Week(weekCells, label)
        }
        val total = byDay.asSequence()
            .filter { (day, _) -> day in firstDay..today }
            .sumOf { (_, reviews) -> reviews.coerceAtLeast(0).toLong() }
        val studiedDays = byDay.count { (day, count) -> day in firstDay..today && count > 0 }
        val busiest = byDay.filterKeys { it in firstDay..today }.maxWithOrNull(compareBy<Map.Entry<Long, Int>> { it.value }.thenByDescending { it.key })
        val dayFormat = SimpleDateFormat("MMM d", locale).apply { timeZone = zone }
        val japanese = locale.language == Locale.JAPANESE.language
        val summary = if (busiest == null || busiest.value <= 0) {
            if (japanese) "過去1年の復習はまだありません。" else "0 reviews across 0 days in the last year."
        } else if (japanese) {
            "過去1年は${studiedDays}日で${total}件復習しました。最多は${dayFormat.format(Date(busiest.key))}の${busiest.value}件です。"
        } else {
            "$total reviews across $studiedDays days in the last year; busiest day ${dayFormat.format(Date(busiest.key))} with ${busiest.value}."
        }
        val weekdays = DateFormatSymbols(locale).shortWeekdays
        return Grid(weeks, (Calendar.SUNDAY..Calendar.SATURDAY).map { weekdays[it] }, summary)
    }

    private fun intensity(value: Int, sortedNonZero: List<Int>): Int {
        if (value <= 0 || sortedNonZero.isEmpty()) return 0
        val rank = sortedNonZero.indexOfLast { it <= value } + 1
        return (((rank * 4) + sortedNonZero.size - 1) / sortedNonZero.size).coerceIn(1, 4)
    }
}
