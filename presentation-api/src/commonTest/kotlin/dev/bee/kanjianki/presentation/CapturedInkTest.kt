package dev.bee.kanjianki.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CapturedInkTest {
    @Test
    fun freshInkIsEmpty() {
        assertTrue(CapturedInk.EMPTY.isEmpty)
        assertTrue(CapturedInk(strokes = listOf(InkStroke())).isEmpty, "a stroke with no points is still empty")
        assertFalse(CapturedInk(strokes = listOf(stroke())).isEmpty)
    }

    @Test
    fun appendingAStrokeKeepsTheOrderAndDropsEmptyOnes() {
        val one = CapturedInk.EMPTY.withStroke(stroke(0f))
        val two = one.withStroke(stroke(1f))

        assertEquals(2, two.strokes.size)
        assertEquals(0f, two.strokes.first().points.first().x)
        // An empty stroke — a tap that produced no drag — is not committed.
        assertSame(two, two.withStroke(InkStroke()))
    }

    @Test
    fun undoRemovesTheLastStrokeAndBottomsOutAtEmpty() {
        val two = CapturedInk.EMPTY.withStroke(stroke(0f)).withStroke(stroke(1f))

        assertEquals(1, two.withoutLastStroke().strokes.size)
        assertEquals(0f, two.withoutLastStroke().strokes.single().points.first().x)
        // Undo on empty ink is a no-op rather than an error.
        assertSame(CapturedInk.EMPTY, CapturedInk.EMPTY.withoutLastStroke())
    }

    @Test
    fun anInkTimestampMustBeANonNegativeOffset() {
        assertFailsWith<IllegalArgumentException> { InkPoint(x = 0f, y = 0f, timestampMillis = -1L) }
    }

    @Test
    fun aPointCarriesItsNormalizedCoordinates() {
        val point = InkPoint(x = 0.25f, y = 0.75f, timestampMillis = 3L)
        assertEquals(0.25f, point.x)
        assertEquals(0.75f, point.y)
        assertEquals(3L, point.timestampMillis)
    }

    private fun stroke(x: Float = 0f) = InkStroke(
        points = listOf(InkPoint(x, 0f, 0L), InkPoint(x, 1f, 1L)),
    )
}
