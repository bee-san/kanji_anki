package dev.bee.kanjianki.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.presentation.ChartAxis
import dev.bee.kanjianki.presentation.ReviewHeatmap
import dev.bee.kanjianki.presentation.StatsBarChart
import dev.bee.kanjianki.presentation.StatsDistribution
import dev.bee.kanjianki.presentation.StatsLineChart
import dev.bee.kanjianki.ui.KaniTheme
import dev.bee.kanjianki.ui.KaniUiTokens

const val STATS_LINE_CHART_TEST_TAG: String = "kani-stats-line-chart"
const val STATS_BAR_CHART_TEST_TAG: String = "kani-stats-bar-chart"
const val STATS_DONUT_CHART_TEST_TAG: String = "kani-stats-donut-chart"
const val STATS_HEATMAP_TEST_TAG: String = "kani-stats-heatmap"

/**
 * The description an enclosing section lends a chart whose own summary is blank.
 *
 * A composition local rather than a parameter because the value is the same for every
 * chart in a section and the section already has it: threading it through seven call
 * sites would be seven chances to pass the wrong one, or to pass nothing.
 */
internal val LocalChartFallbackDescription = compositionLocalOf { "" }

/**
 * The shared chart primitives, operating on the portable stats model.
 *
 * Ported from `:app/charts/KaniCharts`, with the `:core` axis/heatmap types replaced
 * by the portable [ChartAxis]/[ReviewHeatmap] the leaf module can see. Each carries
 * its own accessibility summary from the model, so a screen reader gets one sentence
 * per chart rather than a stream of numbers it cannot chart.
 */
@Composable
internal fun StatsLineChartView(chart: StatsLineChart, colors: List<Color>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(STATS_LINE_CHART_TEST_TAG)
            .chartDescription(chart.accessibilitySummary),
    ) {
        PlotWithYAxis(chart.axis) {
            chart.series.forEachIndexed { i, series ->
                if (series.values.isNotEmpty()) {
                    drawPath(
                        path = linePath(series.values, chart.axis),
                        color = colors.getOrElse(i) { colors.firstOrNull() ?: Color.Black },
                        style = lineStroke(series.dashed),
                    )
                }
            }
        }
        AxisLabelRow(chart.xAxisLabels)
    }
}

@Composable
internal fun StatsBarChartView(chart: StatsBarChart, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(STATS_BAR_CHART_TEST_TAG)
            .chartDescription(chart.accessibilitySummary),
    ) {
        PlotWithYAxis(chart.axis) {
            val count = chart.values.size
            if (count > 0) {
                val slot = size.width / count
                val barWidth = slot * BAR_FILL
                chart.values.forEachIndexed { i, value ->
                    val h = size.height * normalized(value, chart.axis)
                    drawRect(
                        color = accent,
                        topLeft = Offset(slot * i + (slot - barWidth) / 2f, size.height - h),
                        size = Size(barWidth, h),
                    )
                }
            }
        }
        AxisLabelRow(chart.labels)
    }
}

@Composable
internal fun StatsDonutChartView(chart: StatsDistribution, colors: List<Color>, modifier: Modifier = Modifier) {
    val sweeps = donutSweeps(chart.segments.map { it.value })
    val strokeDp = 20.dp
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(PLOT_HEIGHT)
            .testTag(STATS_DONUT_CHART_TEST_TAG)
            .chartDescription(chart.accessibilitySummary),
    ) {
        var start = -90f
        val side = (minOf(size.width, size.height) - strokeDp.toPx()).coerceAtLeast(0f)
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        sweeps.forEachIndexed { i, sweep ->
            drawArc(
                color = colors.getOrElse(i) { colors.firstOrNull() ?: Color.Black },
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(left, top),
                size = Size(side, side),
                style = Stroke(strokeDp.toPx(), cap = StrokeCap.Butt),
            )
            start += sweep
        }
    }
}

@Composable
internal fun StatsHeatmapView(heatmap: ReviewHeatmap, accent: Color, modifier: Modifier = Modifier) {
    val weeks = heatmap.weeks
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(STATS_HEATMAP_TEST_TAG)
            .chartDescription(heatmap.accessibilitySummary),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(HEATMAP_HEIGHT)) {
            if (weeks.isNotEmpty()) {
                val gap = HEATMAP_GAP.toPx()
                val cell = ((size.width - gap * (weeks.size - 1)) / weeks.size).coerceAtLeast(0f)
                weeks.forEachIndexed { w, week ->
                    week.cells.forEachIndexed { d, c ->
                        drawRect(
                            color = accent.copy(alpha = intensityAlpha(c.intensity)),
                            topLeft = Offset(w * (cell + gap), d * (cell + gap)),
                            size = Size(cell, cell),
                        )
                    }
                }
            }
        }
        if (heatmap.weekdayLabels.isNotEmpty()) {
            Text(
                text = heatmap.weekdayLabels.joinToString(" · "),
                color = KaniTheme.colors.muted,
                fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
            )
        }
    }
}

/**
 * Labels a chart with [summary], or with the enclosing section's title when it is blank.
 *
 * A chart is drawn onto a `Canvas`, so its content is invisible to assistive technology
 * and to every sighted assertion: an empty `contentDescription` renders exactly as
 * correctly as a full one, and a screen reader reaches an unlabelled graphic with no
 * indication that anything was lost. The blank case is not hypothetical — the summaries
 * are built as literals in `:progress-core` and passed through `DesktopStatsModel` and
 * every intermediate mapper without a single check on the way, so any one of them
 * returning "" is a silent, untestable regression at the far end of the chain.
 *
 * The fallback is the section title rather than a stated "chart, no description
 * available", because the title is true, is already translated, and tells the user what
 * they are looking at. The apologetic version tells them only that Kani has a bug.
 *
 * It is a real fallback and not an assertion because losing a sentence of detail is a
 * far better outcome for the user than a crash on the Stats screen; the tests are where
 * the blank is meant to be caught.
 */
@Composable
private fun Modifier.chartDescription(summary: String): Modifier {
    val fallback = LocalChartFallbackDescription.current
    val description = summary.ifBlank { fallback }
    // An unlabelled graphic is better than one labelled with the empty string: a
    // screen reader announces the former as an image it cannot describe, and skips
    // straight past the latter as though there were nothing there at all.
    if (description.isBlank()) return this
    return semantics { contentDescription = description }
}

@Composable
private fun PlotWithYAxis(axis: ChartAxis, plot: DrawScope.() -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.height(PLOT_HEIGHT).padding(end = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            for (label in axis.labels.reversed()) {
                Text(
                    text = label,
                    color = KaniTheme.colors.muted,
                    fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
                )
            }
        }
        Canvas(modifier = Modifier.weight(1f).height(PLOT_HEIGHT)) { plot() }
    }
}

@Composable
private fun AxisLabelRow(labels: List<String>) {
    if (labels.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        for (label in labels) {
            Text(
                text = label,
                color = KaniTheme.colors.muted,
                fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
            )
        }
    }
}

private fun DrawScope.linePath(values: List<Int>, axis: ChartAxis): Path {
    val path = Path()
    values.forEachIndexed { index, value ->
        val x = if (values.size == 1) size.width / 2f else size.width * index / (values.size - 1f)
        val y = size.height * (1f - normalized(value, axis))
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    return path
}

private fun DrawScope.lineStroke(dashed: Boolean): Stroke = Stroke(
    width = 3.dp.toPx(),
    cap = StrokeCap.Round,
    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())) else null,
)

private fun normalized(value: Int, axis: ChartAxis): Float =
    if (axis.axisMax <= 0) 0f else value.coerceIn(0, axis.axisMax) / axis.axisMax.toFloat()

private fun donutSweeps(values: List<Int>): List<Float> {
    val total = values.sumOf { it.coerceAtLeast(0).toLong() }
    if (total == 0L) return emptyList()
    return values.map { (it.coerceAtLeast(0).toDouble() * 360.0 / total.toDouble()).toFloat() }
}

private fun intensityAlpha(intensity: Int): Float = when (intensity.coerceIn(0, 4)) {
    0 -> 0.08f
    1 -> 0.30f
    2 -> 0.50f
    3 -> 0.75f
    else -> 1.0f
}

private val PLOT_HEIGHT = 150.dp
private val HEATMAP_HEIGHT = 96.dp
private val HEATMAP_GAP = 2.dp
private const val BAR_FILL = 0.7f
