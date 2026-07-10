package dev.bee.kanjianki.core

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewHeatmapPolicyTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val now = Calendar.getInstance(utc).run {
        set(2026, Calendar.JULY, 10, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }

    @Test fun fillsYearGridPlacesMonthLabelsAndSummarizesBusiestDay() {
        val today = LocalDayPolicy.localDayStart(now, utc)
        val grid = ReviewHeatmapPolicy.build(
            listOf(
                ReviewHeatmapPolicy.DaySummary(LocalDayPolicy.moveLocalDays(today, -10, utc), 2),
                ReviewHeatmapPolicy.DaySummary(LocalDayPolicy.moveLocalDays(today, -2, utc), 9),
            ),
            now, utc, Locale.US,
        )
        assertEquals(53, grid.weeks.size)
        assertEquals(7, grid.weeks.first().cells.size)
        assertTrue(grid.weeks.mapNotNull { it.monthLabel }.contains("Jul"))
        assertEquals(7, grid.weekdayLabels.size)
        val missingDay = LocalDayPolicy.moveLocalDays(today, -5, utc)
        assertEquals(0, grid.weeks.flatMap { it.cells }.single { it.dayStartMillis == missingDay }.reviews)
        assertTrue(grid.accessibilitySummary.contains("11 reviews across 2 days"))
        assertTrue(grid.accessibilitySummary.contains("with 9"))
    }

    @Test fun binsAllZeroSingleUniformAndSkewedData() {
        val today = LocalDayPolicy.localDayStart(now, utc)
        fun intensities(values: List<Int>) = ReviewHeatmapPolicy.build(
            values.mapIndexed { i, value -> ReviewHeatmapPolicy.DaySummary(LocalDayPolicy.moveLocalDays(today, i - values.lastIndex, utc), value) },
            now, utc, Locale.US,
        ).weeks.flatMap { it.cells }.filter { it.dayStartMillis in LocalDayPolicy.moveLocalDays(today, -values.lastIndex, utc)..today }.map { it.intensity }
        assertTrue(intensities(listOf(0, 0)).all { it == 0 })
        assertEquals(listOf(4), intensities(listOf(7)))
        assertEquals(listOf(4, 4, 4, 4), intensities(listOf(3, 3, 3, 3)))
        assertEquals(listOf(1, 2, 3, 4), intensities(listOf(1, 2, 10, 100)))
    }

    @Test fun localDayMovementKeepsDstBucketsAndGridEdgesStable() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val dstNow = Calendar.getInstance(zone).run {
            set(2026, Calendar.MARCH, 9, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
            timeInMillis
        }
        val today = LocalDayPolicy.localDayStart(dstNow, zone)
        val yesterday = LocalDayPolicy.moveLocalDays(today, -1, zone)
        val grid = ReviewHeatmapPolicy.build(listOf(ReviewHeatmapPolicy.DaySummary(yesterday, 4)), dstNow, zone, Locale.US)
        assertEquals(4, grid.weeks.flatMap { it.cells }.single { it.dayStartMillis == yesterday }.reviews)
    }
}
