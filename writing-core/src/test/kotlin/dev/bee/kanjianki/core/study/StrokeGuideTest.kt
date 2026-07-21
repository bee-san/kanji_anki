package dev.bee.kanjianki.core.study

import java.io.IOException
import java.io.StringReader
import java.util.Collections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeGuideTest {
    @Test
    fun parsesCompactNormalizedStrokeData() {
        val guides = StrokeGuideParser.parse(
            StringReader(
                "# generated from KanjiVG\n拉\t0.1,0.2;0.3,0.4||0.5,0.6;0.7,0.8\n",
            ),
        )

        val guide = guides["拉"]!!

        assertEquals("拉", guide.kanji)
        assertEquals(2, guide.strokeCount())
        assertEquals(0.1f, guide.strokes[0].points[0].x, 0.001f)
        val emptyStroke = InkStroke(Collections.emptyList())
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (guide.strokes as MutableList<InkStroke>).add(emptyStroke)
        }
    }

    @Test
    fun rejectsMalformedStrokeData() {
        val error = assertThrows(Exception::class.java) {
            StrokeGuideParser.parse(StringReader("拉\t0.1,0.2;bad\n"))
        }
        val message = error.message.orEmpty()
        assertTrue(message.contains("Invalid point") || message.contains("Invalid coordinate"))
    }

    @Test
    fun skipsBlankAndCommentLines() {
        val guides = StrokeGuideParser.parse(
            StringReader("\n   # generated data\n\t\n"),
        )

        assertTrue(guides.isEmpty())
    }

    @Test
    fun rejectsMissingColumnsInvalidCoordinatesAndEmptyUsableStrokes() {
        val missingColumn = assertThrows(Exception::class.java) {
            StrokeGuideParser.parse(StringReader("拉\n"))
        }
        val invalidCoordinate = assertThrows(Exception::class.java) {
            StrokeGuideParser.parse(StringReader("拉\t0.1,nope;0.2,0.3\n"))
        }
        val noUsableStroke = assertThrows(Exception::class.java) {
            StrokeGuideParser.parse(StringReader("拉\t0.1,0.2\n"))
        }

        assertTrue(missingColumn.message.orEmpty().contains("expected kanji<TAB>stroke data"))
        assertTrue(invalidCoordinate.message.orEmpty().contains("Invalid coordinate"))
        assertTrue(noUsableStroke.message.orEmpty().contains("no usable strokes"))
    }

    @Test
    fun rejectsNonFiniteCoordinates() {
        for (coordinate in listOf("NaN", "Infinity", "-Infinity", "1e100")) {
            val error = assertThrows(IOException::class.java) {
                StrokeGuideParser.parse(StringReader("拉\t$coordinate,0.2;0.3,0.4\n"))
            }

            assertTrue(error.message.orEmpty().contains("Invalid coordinate"))
        }
    }
}
