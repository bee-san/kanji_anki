package dev.bee.kanjianki.core.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeOrderDiagramPolicyTest {
    @Test
    fun nullGuideProducesEmptyDiagram() {
        val diagram = StrokeOrderDiagramPolicy.build(null)
        assertTrue(diagram.isEmpty())
        assertEquals(0, diagram.panels.size)
        assertEquals(0, diagram.omittedStrokeCount)
    }

    @Test
    fun emptyGuideProducesEmptyDiagram() {
        val guide = StrokeGuide("空", emptyList())
        val diagram = StrokeOrderDiagramPolicy.build(guide)
        assertTrue(diagram.isEmpty())
        assertEquals(0, diagram.omittedStrokeCount)
    }

    @Test
    fun singleStrokeProducesSinglePanel() {
        val stroke = makeStroke(0.1f, 0.2f, 0.3f, 0.4f)
        val guide = StrokeGuide("一", listOf(stroke))

        val diagram = StrokeOrderDiagramPolicy.build(guide)

        assertFalse(diagram.isEmpty())
        assertEquals(1, diagram.panels.size)
        assertEquals(0, diagram.omittedStrokeCount)

        val panel = diagram.panels[0]
        assertEquals(1, panel.strokeNumber)
        assertEquals(1, panel.strokes.size)
        assertTrue(panel.strokes[0].highlighted)
        assertNotNull(panel.startPoint)
        assertEquals(0.1f, panel.startPoint!!.x, 0.001f)
        assertEquals(0.2f, panel.startPoint!!.y, 0.001f)
    }

    @Test
    fun cumulativePanelsShowAllPriorStrokes() {
        val s1 = makeStroke(0.1f, 0.2f, 0.3f, 0.4f)
        val s2 = makeStroke(0.5f, 0.6f, 0.7f, 0.8f)
        val s3 = makeStroke(0.2f, 0.3f, 0.4f, 0.5f)
        val guide = StrokeGuide("大", listOf(s1, s2, s3))

        val diagram = StrokeOrderDiagramPolicy.build(guide)

        assertEquals(3, diagram.panels.size)
        assertEquals(0, diagram.omittedStrokeCount)

        assertEquals(1, diagram.panels[0].strokes.size)
        assertTrue(diagram.panels[0].strokes[0].highlighted)

        assertEquals(2, diagram.panels[1].strokes.size)
        assertFalse(diagram.panels[1].strokes[0].highlighted)
        assertTrue(diagram.panels[1].strokes[1].highlighted)

        assertEquals(3, diagram.panels[2].strokes.size)
        assertFalse(diagram.panels[2].strokes[0].highlighted)
        assertFalse(diagram.panels[2].strokes[1].highlighted)
        assertTrue(diagram.panels[2].strokes[2].highlighted)
    }

    @Test
    fun startPointExposedForHighlightedStroke() {
        val s1 = makeStroke(0.1f, 0.2f, 0.3f, 0.4f)
        val s2 = makeStroke(0.5f, 0.6f, 0.7f, 0.8f)
        val guide = StrokeGuide("二", listOf(s1, s2))

        val diagram = StrokeOrderDiagramPolicy.build(guide)

        assertEquals(0.1f, diagram.panels[0].startPoint!!.x, 0.001f)
        assertEquals(0.2f, diagram.panels[0].startPoint!!.y, 0.001f)
        assertEquals(0.5f, diagram.panels[1].startPoint!!.x, 0.001f)
        assertEquals(0.6f, diagram.panels[1].startPoint!!.y, 0.001f)
    }

    @Test
    fun twentyFourPanelCapWithOverflow() {
        val strokes = (1..30).map { i ->
            makeStroke(i * 0.01f, i * 0.02f, i * 0.01f + 0.1f, i * 0.02f + 0.1f)
        }
        val guide = StrokeGuide("鬱", strokes)

        val diagram = StrokeOrderDiagramPolicy.build(guide)

        assertEquals(StrokeOrderDiagramPolicy.MAX_PANELS, diagram.panels.size)
        assertEquals(6, diagram.omittedStrokeCount)
        assertEquals(24, diagram.panels.last().strokeNumber)
    }

    @Test
    fun exactlyTwentyFourStrokesProducesNoOverflow() {
        val strokes = (1..24).map { i ->
            makeStroke(i * 0.01f, i * 0.02f, i * 0.01f + 0.1f, i * 0.02f + 0.1f)
        }
        val guide = StrokeGuide("灘", strokes)

        val diagram = StrokeOrderDiagramPolicy.build(guide)

        assertEquals(24, diagram.panels.size)
        assertEquals(0, diagram.omittedStrokeCount)
    }

    @Test
    fun determinismOnRepeatedCalls() {
        val s1 = makeStroke(0.1f, 0.2f, 0.3f, 0.4f)
        val s2 = makeStroke(0.5f, 0.6f, 0.7f, 0.8f)
        val guide = StrokeGuide("木", listOf(s1, s2))

        val first = StrokeOrderDiagramPolicy.build(guide)
        val second = StrokeOrderDiagramPolicy.build(guide)

        assertEquals(first.panels.size, second.panels.size)
        for (i in first.panels.indices) {
            assertEquals(first.panels[i].strokeNumber, second.panels[i].strokeNumber)
            assertEquals(first.panels[i].strokes.size, second.panels[i].strokes.size)
        }
    }

    @Test
    fun panelStrokeNumbersAreOneIndexed() {
        val strokes = (1..5).map { i ->
            makeStroke(i * 0.1f, i * 0.1f, i * 0.1f + 0.05f, i * 0.1f + 0.05f)
        }
        val guide = StrokeGuide("本", strokes)

        val diagram = StrokeOrderDiagramPolicy.build(guide)

        for (i in diagram.panels.indices) {
            assertEquals(i + 1, diagram.panels[i].strokeNumber)
        }
    }

    @Test
    fun emptyStrokeStartPointIsNull() {
        val emptyStroke = InkStroke(emptyList())
        val validStroke = makeStroke(0.1f, 0.2f, 0.3f, 0.4f)
        val guide = StrokeGuide("口", listOf(emptyStroke, validStroke))

        val diagram = StrokeOrderDiagramPolicy.build(guide)

        assertEquals(2, diagram.panels.size)
        assertNull(diagram.panels[0].startPoint)
        assertNotNull(diagram.panels[1].startPoint)
    }

    private fun makeStroke(x1: Float, y1: Float, x2: Float, y2: Float): InkStroke {
        return InkStroke(
            listOf(
                InkPoint(x1, y1, 0L),
                InkPoint(x2, y2, 1L),
            )
        )
    }
}
