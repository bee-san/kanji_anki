package dev.bee.kanjianki.stats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.presentation.ChartAxis
import dev.bee.kanjianki.presentation.HeatmapCell
import dev.bee.kanjianki.presentation.HeatmapWeek
import dev.bee.kanjianki.presentation.ReviewHeatmap
import dev.bee.kanjianki.presentation.StatsAccuracy
import dev.bee.kanjianki.presentation.StatsBarChart
import dev.bee.kanjianki.presentation.StatsByLevel
import dev.bee.kanjianki.presentation.StatsCategoryStatus
import dev.bee.kanjianki.presentation.StatsDashboard
import dev.bee.kanjianki.presentation.StatsDistribution
import dev.bee.kanjianki.presentation.StatsDistributionSegment
import dev.bee.kanjianki.presentation.StatsForecast
import dev.bee.kanjianki.presentation.StatsLevelRow
import dev.bee.kanjianki.presentation.StatsLineChart
import dev.bee.kanjianki.presentation.StatsMissedKanji
import dev.bee.kanjianki.presentation.StatsOverview
import dev.bee.kanjianki.presentation.StatsRange
import dev.bee.kanjianki.presentation.StatsRetentionRow
import dev.bee.kanjianki.presentation.StatsReviews
import dev.bee.kanjianki.presentation.StatsSeries
import dev.bee.kanjianki.presentation.StatsSupportNeed
import dev.bee.kanjianki.presentation.StatsWeakness
import dev.bee.kanjianki.presentation.StatsWeaknessRow
import dev.bee.kanjianki.ui.KaniTheme

/** A dashboard with every section and chart populated, for the render assertions. */
internal fun sampleDashboard(): StatsDashboard = StatsDashboard(
    forecast = StatsForecast(
        headline = "Practice forecast",
        assumption = "at 20 reviews a day",
        burnDown = lineChart(),
    ),
    overview = StatsOverview(
        title = "Overview",
        subtitle = "the last 30 days",
        streakValue = "9 days",
        accuracyValue = "88%",
        reviewsTodayValue = "24",
        totalReviewsValue = "1,204",
        kanjiLearnedValue = "312",
        studyTimeValue = "4h 20m",
        reviewsOverTime = lineChart(),
        cardTypeBreakdown = distribution(),
        correctIncorrectBreakdown = distribution(),
    ),
    reviews = StatsReviews(
        title = "Reviews",
        selectedRange = StatsRange.THIRTY_DAYS,
        availableRanges = StatsRange.entries,
        reviewsPerDay = barChart(),
        totalReviewsValue = "1,204",
        averagePerDayValue = "40",
        correctValue = "1,060",
        incorrectValue = "144",
        bestDayLabel = "Tuesday",
        tip = "Mornings are your strongest.",
        accessibilitySummary = "reviews per day",
        heatmap = heatmap(),
    ),
    accuracy = StatsAccuracy(
        title = "Accuracy",
        selectedRange = StatsRange.THIRTY_DAYS,
        availableRanges = StatsRange.entries,
        accuracyTrend = lineChart(),
        retentionByCardType = listOf(StatsRetentionRow("Recognition", 90, "90%")),
        retentionSummary = "Retention is holding.",
        categoryStatuses = listOf(StatsCategoryStatus("Reading", "on track")),
    ),
    progressByLevel = StatsByLevel(
        title = "By level",
        overallLearnedLabel = "312 of 2,136 learned",
        overallPercent = 15,
        levelRows = listOf(StatsLevelRow("N5", 80, 100, 80)),
        cumulativeProgress = lineChart(),
    ),
    weakness = StatsWeakness(
        title = "Weak spots",
        focusScoreValue = "72",
        focusScoreStatus = "improving",
        focusScoreAvailable = true,
        weaknessRows = listOf(StatsWeaknessRow("Reading", 60, 12, "high")),
        mostMissedKanji = listOf(StatsMissedKanji("脱", 9), StatsMissedKanji("説", 5)),
        supportNeeded = listOf(StatsSupportNeed("脱", "2 more mature", 1)),
    ),
)

internal fun lineChart(): StatsLineChart = StatsLineChart(
    title = "Reviews over time",
    xAxisLabels = listOf("Mon", "Wed", "Fri"),
    series = listOf(StatsSeries("reviews", listOf(3, 8, 5)), StatsSeries("goal", listOf(6, 6, 6), dashed = true)),
    accessibilitySummary = "reviews trend",
    axis = ChartAxis(axisMax = 10, ticks = listOf(0, 5, 10)),
)

internal fun barChart(): StatsBarChart = StatsBarChart(
    title = "Reviews per day",
    labels = listOf("Mon", "Tue", "Wed"),
    values = listOf(4, 9, 6),
    accessibilitySummary = "reviews per day bars",
    axis = ChartAxis(axisMax = 10, ticks = listOf(0, 5, 10)),
)

internal fun distribution(): StatsDistribution = StatsDistribution(
    title = "Card types",
    segments = listOf(
        StatsDistributionSegment("Recognition", 60, 60),
        StatsDistributionSegment("Reading", 40, 40),
    ),
    accessibilitySummary = "card type split",
)

internal fun heatmap(): ReviewHeatmap = ReviewHeatmap(
    weeks = List(4) { HeatmapWeek(cells = List(7) { d -> HeatmapCell(reviews = d, intensity = d % 5) }) },
    weekdayLabels = listOf("M", "T", "W", "T", "F", "S", "S"),
    accessibilitySummary = "review heatmap",
)

private val STATS_WINDOW_WIDTH: Dp = 411.dp
private val STATS_WINDOW_HEIGHT: Dp = 891.dp

@Composable
private fun FixedWindow(width: Dp, height: Dp, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val scale = maxOf(width / maxWidth, height / maxHeight, 1f)
        CompositionLocalProvider(
            LocalDensity provides Density(density = density.density / scale, fontScale = density.fontScale),
        ) {
            Box(modifier = Modifier.requiredSize(width = width, height = height)) { content() }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun renderStats(content: @Composable () -> Unit, block: ComposeUiTest.() -> Unit) {
    runComposeUiTest {
        setContent {
            KaniTheme {
                FixedWindow(width = STATS_WINDOW_WIDTH, height = STATS_WINDOW_HEIGHT) {
                    Box(modifier = Modifier.verticalScroll(rememberScrollState())) { content() }
                }
            }
        }
        block()
    }
}

internal fun SemanticsNodeInteraction.subtreeTextOrEmpty(): String {
    fun collect(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap(::collect)
    return collect(fetchSemanticsNode()).joinToString(" ")
}

internal fun SemanticsNodeInteraction.contentDescriptionOrEmpty(): String =
    fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
        ?.joinToString(" ").orEmpty()
