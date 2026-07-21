package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KanjiMemoryHistoryPolicyTest {
    @Test
    fun nullInputProducesEmptyResult() {
        val result = KanjiMemoryHistoryPolicy.build(null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun fewerThanTwoPointsProducesEmptyResult() {
        val rows = listOf(makeRow(1000L, "good", 5.0))
        val result = KanjiMemoryHistoryPolicy.build(rows)
        assertTrue(result.isEmpty())
    }

    @Test
    fun twoValidPointsProducesNonEmptyResult() {
        val rows = listOf(
            makeRow(day(1), "good", 5.0),
            makeRow(day(2), "good", 10.0),
        )
        val result = KanjiMemoryHistoryPolicy.build(rows)
        assertFalse(result.isEmpty())
        assertEquals(2, result.points.size)
        assertEquals(5.0f, result.points[0].stability, 0.01f)
        assertEquals(10.0f, result.points[1].stability, 0.01f)
    }

    @Test
    fun zeroStabilityRowsSkipped() {
        val rows = listOf(
            makeRow(day(1), "good", 5.0),
            makeRow(day(2), "again", 0.0),
            makeRow(day(3), "good", 8.0),
        )
        val result = KanjiMemoryHistoryPolicy.build(rows)
        assertEquals(2, result.points.size)
    }

    @Test
    fun nonFiniteStabilityIsSkippedAndHugeFiniteValuesStayFinite() {
        val rows = listOf(
            makeRow(day(1), "good", Double.NaN),
            makeRow(day(2), "good", Double.POSITIVE_INFINITY),
            makeRow(day(3), "good", Double.MAX_VALUE),
            makeRow(day(4), "good", 8.0),
        )

        val result = KanjiMemoryHistoryPolicy.build(rows)

        assertEquals(2, result.points.size)
        assertEquals(Float.MAX_VALUE, result.points[0].stability, 0.0f)
        assertTrue(result.points.all { it.stability.isFinite() })
    }

    @Test
    fun captionCountsReviewsAndMisses() {
        val rows = listOf(
            makeRow(day(1), "good", 5.0),
            makeRow(day(2), "again", 3.0),
            makeRow(day(3), "hard", 4.0),
            makeRow(day(4), "good", 8.0),
        )
        val result = KanjiMemoryHistoryPolicy.build(rows)
        assertEquals("4 reviews, 2 misses", result.caption)
    }

    @Test
    fun dayLabelFormatting() {
        val rows = listOf(
            makeRow(day(1), "good", 5.0),
            makeRow(day(2), "good", 10.0),
        )
        val result = KanjiMemoryHistoryPolicy.build(rows)
        assertTrue(result.points[0].dayLabel.contains("/"))
    }

    @Test
    fun determinism() {
        val rows = listOf(
            makeRow(day(1), "good", 5.0),
            makeRow(day(2), "again", 3.0),
            makeRow(day(3), "good", 8.0),
        )
        val r1 = KanjiMemoryHistoryPolicy.build(rows)
        val r2 = KanjiMemoryHistoryPolicy.build(rows)
        assertEquals(r1.points.size, r2.points.size)
        assertEquals(r1.caption, r2.caption)
    }

    private fun day(offset: Int): Long {
        return 1720000000000L + offset * 86400000L
    }

    private fun makeRow(millis: Long, rating: String, stability: Double): KanjiMemoryHistoryPolicy.MemoryHistoryRow {
        return KanjiMemoryHistoryPolicy.MemoryHistoryRow(millis, rating, stability)
    }
}
