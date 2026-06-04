package dev.bee.kanjianki.study

import dev.bee.kanjianki.core.study.WritingSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturedWritingTest {
    @Test
    fun capturedStrokeRejectsInvalidInputs() {
        val emptyPoints = emptyList<CapturedStroke.Point>()
        @Suppress("UNCHECKED_CAST")
        val pointsWithNull = listOf(point(1f, 2f), null) as List<CapturedStroke.Point>

        assertThrows(NullPointerException::class.java) { CapturedStroke(null) }
        assertThrows(IllegalArgumentException::class.java) { CapturedStroke(emptyPoints) }
        assertThrows(NullPointerException::class.java) { CapturedStroke(pointsWithNull) }
        assertThrows(IllegalArgumentException::class.java) { CapturedStroke.Point(Float.NaN, 0f) }
        assertThrows(IllegalArgumentException::class.java) { CapturedStroke.Point(Float.POSITIVE_INFINITY, 0f) }
        assertThrows(IllegalArgumentException::class.java) { CapturedStroke.Point(0f, Float.NEGATIVE_INFINITY) }
        assertThrows(IllegalArgumentException::class.java) { CapturedStroke.Point(0f, 0f, -1L) }
    }

    @Test
    fun capturedStrokeCopiesAndFreezesPoints() {
        val source = mutableListOf<CapturedStroke.Point>()
        source.add(CapturedStroke.Point(1f, 2f, 30L))
        source.add(point(3f, 4f))

        val stroke = CapturedStroke.of(source)
        source.add(point(9f, 9f))
        val appendedPoint = point(5f, 6f)

        assertEquals(2, stroke.points.size)
        assertEquals(1f, stroke.points[0].x, 0f)
        assertEquals(2f, stroke.points[0].y, 0f)
        assertEquals(30L, stroke.points[0].timestampMillis!!)
        assertEquals(3f, stroke.points[1].x, 0f)
        assertEquals(4f, stroke.points[1].y, 0f)
        assertNull(stroke.points[1].timestampMillis)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (stroke.points as MutableList<CapturedStroke.Point>).add(appendedPoint)
        }
    }

    @Test
    fun capturedWritingRejectsInvalidInputs() {
        val stroke = stroke(point(1f, 1f))
        val emptyStrokes = emptyList<CapturedStroke>()
        @Suppress("UNCHECKED_CAST")
        val nullStroke = listOf<CapturedStroke?>(null) as List<CapturedStroke>
        val oneStroke = listOf(stroke)

        assertThrows(NullPointerException::class.java) { CapturedWriting(null) }
        assertThrows(IllegalArgumentException::class.java) { CapturedWriting(emptyStrokes) }
        assertThrows(NullPointerException::class.java) { CapturedWriting(nullStroke) }
        assertThrows(IllegalArgumentException::class.java) { CapturedWriting(oneStroke, 100f, null, "") }
        assertThrows(IllegalArgumentException::class.java) { CapturedWriting(oneStroke, null, 100f, "") }
        assertThrows(IllegalArgumentException::class.java) { CapturedWriting(oneStroke, 0f, 100f, "") }
        assertThrows(IllegalArgumentException::class.java) { CapturedWriting(oneStroke, Float.NaN, 100f, "") }
        assertThrows(IllegalArgumentException::class.java) { CapturedWriting(oneStroke, Float.POSITIVE_INFINITY, 100f, "") }
    }

    @Test
    fun capturedWritingCopiesStrokesAndReportsContextFlags() {
        val baseStroke = stroke(point(1f, 1f))
        val strokes = mutableListOf<CapturedStroke>()
        strokes.add(baseStroke)

        val withAreaAndNullContext = CapturedWriting(strokes, 200f, 300f, null)
        strokes.add(stroke(point(2f, 2f)))
        val withTextContext = CapturedWriting(listOf(baseStroke), null, null, "previous")
        val basic = CapturedWriting.of(listOf(baseStroke))

        assertEquals(1, withAreaAndNullContext.strokes.size)
        assertEquals(200f, withAreaAndNullContext.writingAreaWidth!!, 0f)
        assertEquals(300f, withAreaAndNullContext.writingAreaHeight!!, 0f)
        assertEquals("", withAreaAndNullContext.preContext)
        assertTrue(withAreaAndNullContext.hasWritingArea())
        assertTrue(withAreaAndNullContext.hasRecognitionContext())
        assertFalse(withTextContext.hasWritingArea())
        assertTrue(withTextContext.hasRecognitionContext())
        assertFalse(basic.hasWritingArea())
        assertFalse(basic.hasRecognitionContext())
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (withAreaAndNullContext.strokes as MutableList<CapturedStroke>).add(baseStroke)
        }
    }

    @Test
    fun capturedWritingConvertsToWritingSample() {
        val writing = CapturedWriting(
            listOf(
                stroke(CapturedStroke.Point(1f, 2f, 30L)),
                stroke(CapturedStroke.Point(3f, 4f)),
            ),
            200f,
            300f,
            "",
        )

        val sample = writing.toWritingSample()

        assertEquals(2, sample.strokes.size)
        assertEquals(200f, sample.width, 0f)
        assertEquals(300f, sample.height, 0f)
        assertEquals(1f, sample.strokes[0].points[0].x, 0f)
        assertEquals(2f, sample.strokes[0].points[0].y, 0f)
        assertEquals(30L, sample.strokes[0].points[0].timestampMillis)
        assertEquals(3f, sample.strokes[1].points[0].x, 0f)
        assertEquals(4f, sample.strokes[1].points[0].y, 0f)
        assertEquals(0L, sample.strokes[1].points[0].timestampMillis)
    }

    @Test
    fun capturedWritingWithoutAreaConvertsToZeroSizedSample() {
        val writing = CapturedWriting.of(listOf(stroke(point(1f, 2f))))

        val sample = writing.toWritingSample()

        assertEquals(1, sample.strokes.size)
        assertEquals(0f, sample.width, 0f)
        assertEquals(0f, sample.height, 0f)
    }

    @Test
    fun staticWritingSampleConversionAllowsNoInk() {
        val sample = CapturedWriting.toWritingSample(emptyList<CapturedStroke>(), 200f, 300f)

        assertEquals(0, sample.strokes.size)
        assertEquals(200f, sample.width, 0f)
        assertEquals(300f, sample.height, 0f)
        assertFalse(sample.hasInk())
    }

    @Test
    fun prepareForRecognitionRejectsInvalidInputs() {
        val strokes = listOf(stroke(point(1f, 1f), point(2f, 2f)))
        val emptyStrokes = emptyList<CapturedStroke>()

        assertThrows(NullPointerException::class.java) { CapturedWriting.prepareForRecognition(null, 100f, 100f) }
        assertThrows(IllegalArgumentException::class.java) { CapturedWriting.prepareForRecognition(emptyStrokes, 100f, 100f) }
        assertThrows(IllegalArgumentException::class.java) { CapturedWriting.prepareForRecognition(strokes, 0f, 100f) }
        assertThrows(IllegalArgumentException::class.java) { CapturedWriting.prepareForRecognition(strokes, 100f, Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { CapturedWriting.prepareForRecognition(strokes, Float.NEGATIVE_INFINITY, 100f) }
    }

    @Test
    fun prepareForRecognitionSimplifiesDenseStrokesAndNormalizesIntoSquare() {
        val duplicateFinal = stroke(
            CapturedStroke.Point(10f, 10f, 0L),
            CapturedStroke.Point(11f, 11f, 1L),
            CapturedStroke.Point(13f, 10f, 2L),
            CapturedStroke.Point(13f, 10f, 3L),
        )
        val addedFinal = stroke(
            CapturedStroke.Point(20f, 20f, 4L),
            CapturedStroke.Point(21f, 20f, 5L),
            CapturedStroke.Point(24f, 20f, 6L),
        )

        val prepared = CapturedWriting.prepareForRecognition(
            listOf(duplicateFinal, addedFinal),
            320f,
            240f,
        )

        assertEquals(2, prepared.strokes.size)
        assertEquals(2, prepared.strokes[0].points.size)
        assertEquals(2, prepared.strokes[1].points.size)
        assertEquals(1000f, prepared.writingAreaWidth!!, 0f)
        assertEquals(1000f, prepared.writingAreaHeight!!, 0f)
        assertEquals("", prepared.preContext)
        assertTrue(prepared.hasWritingArea())
        assertTrue(prepared.hasRecognitionContext())
        assertPoint(prepared.strokes[0].points[0], 140f, 242.85715f, 0L)
        assertPoint(prepared.strokes[0].points[1], 294.2857f, 242.85715f, 2L)
        assertPoint(prepared.strokes[1].points[0], 654.2857f, 757.1429f, 4L)
        assertPoint(prepared.strokes[1].points[1], 860f, 757.1429f, 6L)
    }

    @Test
    fun prepareForRecognitionKeepsShortStrokesAndCentersMinimumSizeBounds() {
        val shortStroke = stroke(
            CapturedStroke.Point(5f, 7f, 10L),
            CapturedStroke.Point(5f, 7f, 11L),
        )

        val prepared = CapturedWriting.prepareForRecognition(
            listOf(shortStroke),
            80f,
            60f,
        )

        assertEquals(1, prepared.strokes.size)
        assertEquals(2, prepared.strokes[0].points.size)
        assertPoint(prepared.strokes[0].points[0], 140f, 140f, 10L)
        assertPoint(prepared.strokes[0].points[1], 140f, 140f, 11L)
    }

    @Test
    fun prepareForRecognitionKeepsFinalPointWhenOnlyYChanges() {
        val verticalFinal = stroke(
            CapturedStroke.Point(10f, 10f, 0L),
            CapturedStroke.Point(10f, 11f, 1L),
            CapturedStroke.Point(10f, 12f, 2L),
        )

        val prepared = CapturedWriting.prepareForRecognition(
            listOf(verticalFinal),
            100f,
            100f,
        )

        assertEquals(1, prepared.strokes.size)
        assertEquals(2, prepared.strokes[0].points.size)
        assertEquals(0L, prepared.strokes[0].points[0].timestampMillis!!)
        assertEquals(2L, prepared.strokes[0].points[1].timestampMillis!!)
    }

    private fun point(x: Float, y: Float): CapturedStroke.Point {
        return CapturedStroke.Point(x, y)
    }

    private fun stroke(vararg points: CapturedStroke.Point): CapturedStroke {
        return CapturedStroke(points.toList())
    }

    private fun assertPoint(point: CapturedStroke.Point, x: Float, y: Float, timestamp: Long) {
        assertEquals(x, point.x, 0.0001f)
        assertEquals(y, point.y, 0.0001f)
        assertEquals(timestamp, point.timestampMillis!!)
    }
}
