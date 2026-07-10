package dev.bee.kanjianki.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.core.ChartAxisPolicy
import dev.bee.kanjianki.core.ReviewHeatmapPolicy
import dev.bee.kanjianki.progress.ProgressBarChartState
import dev.bee.kanjianki.progress.ProgressDistributionChartState
import dev.bee.kanjianki.progress.ProgressLineChartState
import dev.bee.kanjianki.progress.ProgressSeriesStyle

internal const val KaniLineChartTag = "kani-line-chart"
internal const val KaniBarChartTag = "kani-bar-chart"
internal const val KaniDonutChartTag = "kani-donut-chart"
internal const val KaniHeatmapChartTag = "kani-heatmap-chart"
internal const val KaniHeatmapGridTag = "kani-heatmap-grid"
internal const val KaniHeatmapWeekTag = "kani-heatmap-week"
internal const val KaniYAxisTickTagPrefix = "kani-y-axis-tick-"
internal const val KaniHeatmapMonthTagPrefix = "kani-heatmap-month-"

private val KaniYAxisWidth = 32.dp
private val KaniPlotHeight = 150.dp

internal object KaniChartGeometry {
    data class SquareBounds(val left: Float, val top: Float, val size: Float)

    fun normalized(value: Int, axis: ChartAxisPolicy.Axis): Float =
        if (axis.axisMax <= 0) 0f else (value.coerceIn(0, axis.axisMax) / axis.axisMax.toFloat())

    fun donutSweeps(values: List<Int>): List<Float> {
        val total = values.sumOf { it.coerceAtLeast(0) }
        if (total == 0) return emptyList()
        return values.map { it.coerceAtLeast(0) * 360f / total }
    }

    fun centeredSquareBounds(width: Float, height: Float, inset: Float): SquareBounds {
        val side = (minOf(width, height) - inset.coerceAtLeast(0f)).coerceAtLeast(0f)
        return SquareBounds((width - side) / 2f, (height - side) / 2f, side)
    }
}

@Composable
internal fun KaniLineChart(
    chart: ProgressLineChartState,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val stroke = 3.dp
    Column(modifier = modifier.semantics { contentDescription = chart.accessibilitySummary }) {
        PlotWithYAxis(chart.axis, KaniLineChartTag) {
            chart.series.forEachIndexed { seriesIndex, series ->
                if (series.values.isEmpty()) return@forEachIndexed
                val path = Path()
                series.values.forEachIndexed { index, value ->
                    val x = if (series.values.size == 1) size.width / 2f else size.width * index / (series.values.size - 1f)
                    val y = size.height * (1f - KaniChartGeometry.normalized(value, chart.axis))
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = colors.getOrElse(seriesIndex) { colors.firstOrNull() ?: Color.Black },
                    style = Stroke(
                        width = stroke.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = if (series.style == ProgressSeriesStyle.DASHED) {
                            PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
                        } else null,
                    ),
                )
            }
        }
        PlotXAxisLabels(chart.xAxisLabels)
    }
}

@Composable
internal fun KaniBarChart(
    chart: ProgressBarChartState,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.semantics { contentDescription = chart.accessibilitySummary }) {
        PlotWithYAxis(chart.axis, KaniBarChartTag) {
            if (chart.values.isEmpty()) return@PlotWithYAxis
            val slot = size.width / chart.values.size
            val barWidth = slot * 0.56f
            chart.values.forEachIndexed { index, value ->
                val barHeight = size.height * KaniChartGeometry.normalized(value, chart.axis)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(index * slot + (slot - barWidth) / 2f, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()),
                )
            }
        }
        PlotXAxisLabels(chart.labels)
    }
}

@Composable
internal fun KaniDonutChart(
    chart: ProgressDistributionChartState,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val sweeps = KaniChartGeometry.donutSweeps(chart.segments.map { it.value })
    val stroke = 20.dp
    Canvas(
        modifier = modifier.height(150.dp).fillMaxWidth()
            .testTag(KaniDonutChartTag)
            .semantics { contentDescription = chart.accessibilitySummary },
    ) {
        var start = -90f
        val bounds = KaniChartGeometry.centeredSquareBounds(size.width, size.height, stroke.toPx())
        sweeps.forEachIndexed { index, sweep ->
            drawArc(
                color = colors.getOrElse(index) { colors.firstOrNull() ?: Color.Black },
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(bounds.left, bounds.top),
                size = Size(bounds.size, bounds.size),
                style = Stroke(stroke.toPx(), cap = StrokeCap.Butt),
            )
            start += sweep
        }
    }
}

@Composable
internal fun KaniHeatmapChart(
    grid: ReviewHeatmapPolicy.Grid,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val gap = 2.dp
    Column(
        modifier = modifier.fillMaxWidth().testTag(KaniHeatmapChartTag)
            .semantics { contentDescription = grid.accessibilitySummary },
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(18.dp)) {
            val weekWidth = if (grid.weeks.isEmpty()) 0.dp else maxWidth / grid.weeks.size
            var monthOrdinal = 0
            grid.weeks.forEachIndexed { weekIndex, week ->
                week.monthLabel?.let { label ->
                    val showLabel = maxWidth >= 420.dp || monthOrdinal % 2 == 0
                    monthOrdinal++
                    if (showLabel) {
                        Text(
                            label,
                            modifier = Modifier.offset(x = weekWidth * weekIndex)
                                .testTag(KaniHeatmapMonthTagPrefix + weekIndex),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cell = if (grid.weeks.isEmpty()) 0.dp else {
                minOf((maxWidth - gap * (grid.weeks.size - 1)) / grid.weeks.size, 13.dp).coerceAtLeast(0.dp)
            }
            val gridHeight = if (cell == 0.dp) 0.dp else cell * 7 + gap * 6
            Canvas(Modifier.fillMaxWidth().height(gridHeight).testTag(KaniHeatmapGridTag)) {
                if (grid.weeks.isEmpty()) return@Canvas
                val cellPx = cell.toPx()
                grid.weeks.forEachIndexed { weekIndex, week ->
                    week.cells.forEachIndexed { dayIndex, day ->
                        val alpha = when (day.intensity) { 1 -> .22f; 2 -> .42f; 3 -> .68f; 4 -> 1f; else -> .08f }
                        drawRect(
                            color = accent.copy(alpha = alpha),
                            topLeft = Offset(weekIndex * (cellPx + gap.toPx()), dayIndex * (cellPx + gap.toPx())),
                            size = Size(cellPx, cellPx),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(1.dp)) {
            grid.weeks.forEach {
                Spacer(Modifier.weight(1f).testTag(KaniHeatmapWeekTag))
            }
        }
        Text(grid.weekdayLabels.joinToString(" · "), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PlotWithYAxis(
    axis: ChartAxisPolicy.Axis,
    canvasTag: String,
    draw: DrawScope.() -> Unit,
) {
    Row(Modifier.fillMaxWidth()) {
        Column(
            Modifier.width(KaniYAxisWidth).height(KaniPlotHeight).padding(end = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
        ) {
            axis.ticks.asReversed().forEach { tick ->
                Text(
                    tick.toString(),
                    modifier = Modifier.testTag(KaniYAxisTickTagPrefix + tick),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
        Canvas(Modifier.weight(1f).height(KaniPlotHeight).testTag(canvasTag), onDraw = draw)
    }
}

@Composable
private fun PlotXAxisLabels(labels: List<String>) {
    if (labels.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Spacer(Modifier.width(KaniYAxisWidth))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.take(6).forEach { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
        }
    }
}
