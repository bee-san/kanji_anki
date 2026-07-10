package dev.bee.kanjianki.charts

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.KaniTheme
import dev.bee.kanjianki.core.ChartAxisPolicy
import dev.bee.kanjianki.core.ReviewHeatmapPolicy
import dev.bee.kanjianki.progress.ProgressLineChartState
import dev.bee.kanjianki.progress.ProgressSeriesState
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KaniChartComposeLayoutTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun yAxisTicksFormAColumnBesideThePlot() {
        val axis = ChartAxisPolicy.Axis(150, listOf(0, 50, 100, 150))
        val chart = ProgressLineChartState(
            title = "Forecast",
            xAxisLabels = listOf("Jul", "Sep", "Nov", "Jan", "Mar"),
            series = listOf(ProgressSeriesState("Remaining", listOf(126, 92, 61, 29, 0))),
            accessibilitySummary = "Forecast",
            axis = axis,
        )
        composeRule.setContent { KaniTheme { KaniLineChart(chart, listOf(Color.Magenta), Modifier.width(280.dp)) } }
        composeRule.waitForIdle()

        val plot = composeRule.onNodeWithTag(KaniLineChartTag).fetchSemanticsNode().boundsInRoot
        val maximum = composeRule.onNodeWithTag(KaniYAxisTickTagPrefix + "150").fetchSemanticsNode().boundsInRoot
        val minimum = composeRule.onNodeWithTag(KaniYAxisTickTagPrefix + "0").fetchSemanticsNode().boundsInRoot
        assertTrue(maximum.top < minimum.top)
        assertTrue(maximum.right <= plot.left)
        assertTrue(minimum.right <= plot.left)
    }

    @Test fun heatmapMonthLabelsKeepTheirOriginatingWeekOffsets() {
        val now = 1_747_000_000_000L
        val grid = ReviewHeatmapPolicy.build(emptyList(), now, TimeZone.getTimeZone("UTC"), Locale.US)
        val labelledWeeks = grid.weeks.mapIndexedNotNull { index, week -> week.monthLabel?.let { index to it } }
        assertTrue(labelledWeeks.size > 2)
        composeRule.setContent { KaniTheme { KaniHeatmapChart(grid, Color.Magenta, Modifier.width(280.dp)) } }
        composeRule.waitForIdle()

        val chart = composeRule.onNodeWithTag(KaniHeatmapChartTag).fetchSemanticsNode().boundsInRoot
        val gridBounds = composeRule.onNodeWithTag(KaniHeatmapGridTag).fetchSemanticsNode().boundsInRoot
        assertTrue(composeRule.onAllNodesWithTag(KaniHeatmapMonthTagPrefix + labelledWeeks[1].first).fetchSemanticsNodes().isEmpty())
        val (weekIndex, _) = labelledWeeks[2]
        val label = composeRule.onNodeWithTag(KaniHeatmapMonthTagPrefix + weekIndex).fetchSemanticsNode().boundsInRoot
        val expectedLeft = chart.left + chart.width * weekIndex / grid.weeks.size
        assertEquals(expectedLeft, label.left, 1.5f)
        assertTrue(gridBounds.height in 20f..60f)
    }
}
