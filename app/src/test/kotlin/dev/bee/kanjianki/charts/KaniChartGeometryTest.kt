package dev.bee.kanjianki.charts

import dev.bee.kanjianki.core.ChartAxisPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class KaniChartGeometryTest {
    @Test fun axisMappingUsesSameAxisAsPrintedTicks() {
        val axis = ChartAxisPolicy.forMaximum(180)
        assertEquals(0f, KaniChartGeometry.normalized(0, axis), 0.0001f)
        assertEquals(.5f, KaniChartGeometry.normalized(100, axis), 0.0001f)
        assertEquals(1f, KaniChartGeometry.normalized(300, axis), 0.0001f)
        assertEquals(0f, KaniChartGeometry.normalized(4, ChartAxisPolicy.forMaximum(0)), 0.0001f)
    }

    @Test fun donutSegmentMathHasNoFabricatedFloor() {
        assertEquals(emptyList<Float>(), KaniChartGeometry.donutSweeps(listOf(0, 0)))
        assertEquals(listOf(90f, 270f), KaniChartGeometry.donutSweeps(listOf(1, 3)))
        assertEquals(listOf(0f, 360f), KaniChartGeometry.donutSweeps(listOf(-2, 4)))
        assertEquals(
            listOf(180f, 180f),
            KaniChartGeometry.donutSweeps(listOf(Int.MAX_VALUE, Int.MAX_VALUE)),
        )
    }

    @Test fun donutBoundsStaySquareAndCenteredInsideWideCanvas() {
        val bounds = KaniChartGeometry.centeredSquareBounds(width = 260f, height = 150f, inset = 20f)
        assertEquals(65f, bounds.left, 0.0001f)
        assertEquals(10f, bounds.top, 0.0001f)
        assertEquals(130f, bounds.size, 0.0001f)
        assertEquals(0f, KaniChartGeometry.centeredSquareBounds(10f, 8f, 20f).size, 0.0001f)
    }
}
