package dev.bee.kanjianki.core.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WritingSampleTest {
    @Test
    fun emptySampleHasNoInkOrStrokes() {
        val sample = WritingSample.empty()

        assertFalse(sample.hasInk())
        assertEquals(0, sample.strokeCount())
        assertEquals(0f, sample.width, 0.001f)
        assertEquals(0f, sample.height, 0.001f)
    }

    @Test
    fun strokeCountsIgnoreEmptyStrokesAndExposeImmutableList() {
        val sample = WritingSample(
            listOf(
                InkStroke(emptyList()),
                InkStroke(listOf(InkPoint(0f, 0f, 0), InkPoint(1f, 1f, 1))),
            ),
            10f,
            20f,
        )

        assertTrue(sample.hasInk())
        assertEquals(1, sample.strokeCount())
        val emptyStroke = InkStroke(emptyList())
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (sample.strokes as MutableList<InkStroke>).add(emptyStroke)
        }
    }

    @Test
    fun inkPointEqualityUsesCoordinatesAndTimestamp() {
        val point = InkPoint(1f, 2f, 3)

        assertEquals(InkPoint(2f, 6f, 3), point.scaled(2f, 3f))
        assertEquals(point, InkPoint(1f, 2f, 3))
        assertEquals(point.hashCode(), InkPoint(1f, 2f, 3).hashCode())
        val otherType: Any = "point"
        val equalsOtherType = point == otherType
        val equalsDifferentX = point == InkPoint(2f, 2f, 3)
        val equalsDifferentY = point == InkPoint(1f, 3f, 3)
        val equalsDifferentTimestamp = point == InkPoint(1f, 2f, 4)
        assertFalse(equalsOtherType)
        assertFalse(equalsDifferentX)
        assertFalse(equalsDifferentY)
        assertFalse(equalsDifferentTimestamp)
    }
}
