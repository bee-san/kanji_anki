package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.charts.KaniDonutChart
import dev.bee.kanjianki.charts.KaniHeatmapChart
import dev.bee.kanjianki.charts.KaniLineChart
import dev.bee.kanjianki.core.StatsEmptyStateCopy
import dev.bee.kanjianki.core.StatsDashboardCopy
import dev.bee.kanjianki.progress.AnalyticsRange
import dev.bee.kanjianki.progress.ProgressAccuracyRetentionState
import dev.bee.kanjianki.progress.ProgressAnalyticsCopy
import dev.bee.kanjianki.progress.ProgressAnalyticsState
import dev.bee.kanjianki.progress.ProgressConfusionPairState
import dev.bee.kanjianki.progress.ProgressCountMetricState
import dev.bee.kanjianki.progress.ProgressDistributionChartState
import dev.bee.kanjianki.progress.ProgressForecastState
import dev.bee.kanjianki.progress.ProgressLineChartState
import dev.bee.kanjianki.progress.ProgressOverviewState
import dev.bee.kanjianki.progress.ProgressReviewsAnalyticsState
import dev.bee.kanjianki.progress.ProgressReviewsRangeData
import dev.bee.kanjianki.progress.ProgressStreakMetricState
import dev.bee.kanjianki.progress.ProgressWeaknessInsightsState

internal const val ProgressOverviewHeroSummaryTag = "progress-overview-hero-summary"
internal const val ProgressStreakSummaryTag = "progress-streak-summary"
internal const val ProgressCurrentStreakMetricTag = "progress-current-streak-metric"
internal const val ProgressLongestStreakMetricTag = "progress-longest-streak-metric"
internal const val ProgressOverviewMetricsCompactTag = "progress-overview-metrics-compact"
internal const val ProgressDistributionCardCompactLayoutTag = "progress-distribution-card-compact-layout"
internal const val ProgressForecastCardTag = "progress-forecast-card"
internal const val ProgressConfusionRowTagPrefix = "progress-confusion-row-"
internal const val ProgressRangeChipRowTag = "progress-range-chip-row"
internal const val ProgressRangeChipTagPrefix = "progress-range-chip-"

private const val ProgressAnalyticsCompactWidthBreakpointDp = 420

internal fun progressAnalyticsOverviewMetricColumns(maxWidth: Dp): Int =
    if (progressAnalyticsIsCompactWidth(maxWidth)) 2 else 3

internal fun progressAnalyticsDistributionUsesStackedLegendLayout(maxWidth: Dp): Boolean =
    progressAnalyticsIsCompactWidth(maxWidth)

internal fun progressAnalyticsOverviewSummaryText(state: ProgressOverviewState): String =
    "${ProgressAnalyticsCopy.totalReviewsLabel()} ${state.totalReviews.valueLabel} · " +
        "${ProgressAnalyticsCopy.accuracyLabel()} ${state.accuracy.valueLabel} · " +
        "${ProgressAnalyticsCopy.streakLabel()} ${state.currentStreak.valueLabel}"

internal fun progressAnalyticsStreakSummaryText(
    state: ProgressStreakMetricState,
    tip: String,
): String {
    val copy = StatsDashboardCopy.forLocale()
    return "${ProgressAnalyticsCopy.currentStreakLabel()} ${state.valueLabel} · " +
        "${ProgressAnalyticsCopy.longestStreakLabel()} ${copy.days(state.bestDays)}. $tip"
}

private fun progressAnalyticsIsCompactWidth(maxWidth: Dp): Boolean =
    maxWidth < ProgressAnalyticsCompactWidthBreakpointDp.dp

@Composable
internal fun ProgressAnalyticsDashboardScreen(
    state: ProgressAnalyticsState,
    modifier: Modifier = Modifier,
    onBrowseKanji: (String) -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val compact = progressAnalyticsIsCompactWidth(maxWidth)
        Column(
            Modifier.fillMaxWidth().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
        ) {
            ForecastSection(state.forecast)
            OverviewSection(state.overview, compact)
            ReviewsSection(state.reviewsAnalytics, compact)
            AccuracySection(state.accuracyRetention, compact)
            LadderSection(state)
            WeaknessSection(state.weaknessInsights, onBrowseKanji)
        }
    }
}

@Composable
private fun ForecastSection(forecast: ProgressForecastState?) {
    val copy = StatsDashboardCopy.forLocale()
    StatsSection(alternate = true, modifier = Modifier.testTag(ProgressForecastCardTag)) {
        HomeSectionHeader(copy.practiceForecast, null, null)
        if (forecast == null) {
            val copy = StatsEmptyStateCopy.forecast()
            HomeEmptyState(copy.title, copy.body)
            return@StatsSection
        }
        Text(forecast.headline, style = MaterialTheme.typography.headlineSmall, color = KaniTheme.colors.ink)
        ChartLeaf { KaniLineChart(forecast.burnDown, listOf(KaniTheme.colors.primary)) }
        Text(forecast.assumption, style = MaterialTheme.typography.labelSmall, color = KaniTheme.colors.muted)
    }
}

@Composable
private fun OverviewSection(state: ProgressOverviewState, compact: Boolean) {
    StatsSection {
        Text(
            state.title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineLarge,
            color = KaniTheme.colors.ink,
        )
        Text(state.subtitle, style = MaterialTheme.typography.bodyMedium, color = KaniTheme.colors.muted)
        HeroStrip(state)
        MetricGrid(state, compact)
        DistributionCharts(state, compact)
    }
}

@Composable
private fun HeroStrip(state: ProgressOverviewState) {
    val copy = StatsDashboardCopy.forLocale()
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(ProgressOverviewHeroSummaryTag)
            .semantics { contentDescription = progressAnalyticsOverviewSummaryText(state) },
        shape = KaniUiTokens.LeafShape,
        color = KaniTheme.colors.panel,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HeroMetric(ProgressAnalyticsCopy.streakLabel(), state.currentStreak.valueLabel, Modifier.weight(1f))
            HeroMetric(ProgressAnalyticsCopy.accuracyLabel(), state.accuracy.valueLabel, Modifier.weight(1f))
            HeroMetric(copy.reviewsToday, state.reviewsToday.valueLabel, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = KaniTheme.colors.primary, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = KaniTheme.colors.muted)
    }
}

@Composable
private fun MetricGrid(state: ProgressOverviewState, compact: Boolean) {
    val metrics = listOf(
        Metric(R.drawable.ic_stats_24, ProgressAnalyticsCopy.totalReviewsLabel(), state.totalReviews.valueLabel, state.totalReviews.deltaLabel, KaniTheme.colors.teal),
        Metric(R.drawable.ic_book_24, ProgressAnalyticsCopy.kanjiLearnedLabel(), state.kanjiLearned.valueLabel, state.kanjiLearned.deltaLabel, KaniTheme.colors.primary),
        Metric(R.drawable.ic_target_24, ProgressAnalyticsCopy.focusSessionsLabel(), state.focusSessions.valueLabel, state.focusSessions.deltaLabel, KaniTheme.colors.blue),
        Metric(R.drawable.ic_study_24, ProgressAnalyticsCopy.studyTimeLabel(), state.studyTime.valueLabel, state.studyTime.deltaLabel, KaniTheme.colors.gold),
    )
    Column(
        Modifier.fillMaxWidth().then(if (compact) Modifier.testTag(ProgressOverviewMetricsCompactTag) else Modifier),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        metrics.chunked(if (compact) 2 else 3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { metric ->
                    KaniMetricCard(
                        metric.icon, metric.label, metric.value, metric.delta,
                        metric.color, Modifier.weight(1f),
                    )
                }
                if (compact && row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private data class Metric(val icon: Int, val label: String, val value: String, val delta: String?, val color: Color)

@Composable
private fun DistributionCharts(state: ProgressOverviewState, compact: Boolean) {
    val charts = listOf(state.cardTypeBreakdown, state.correctIncorrectBreakdown).filter { it.segments.isNotEmpty() }
    if (charts.isEmpty()) {
        EmptyCharts()
        return
    }
    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            charts.forEach { DistributionCard(it, Modifier.testTag(ProgressDistributionCardCompactLayoutTag)) }
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            charts.forEach { DistributionCard(it, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun DistributionCard(chart: ProgressDistributionChartState, modifier: Modifier = Modifier) {
    ChartLeaf(modifier) {
        Text(chart.title, style = MaterialTheme.typography.titleMedium, color = KaniTheme.colors.ink)
        KaniDonutChart(chart, chartColors())
        chart.segments.forEach { Text("${it.label} · ${it.value} (${it.percent}%)", style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun ReviewsSection(state: ProgressReviewsAnalyticsState, compact: Boolean) {
    val copy = StatsDashboardCopy.forLocale()
    var selected by rememberSaveable { mutableStateOf(state.selectedRange) }
    val selectedData = state.rangeData[selected] ?: state.rangeData[state.selectedRange] ?: ProgressReviewsRangeData(
        state.reviewsPerDay, state.totalReviews, state.averagePerDay, state.correct, state.incorrect,
        state.bestDayLabel, state.accessibilitySummary,
    )
    StatsSection(alternate = true) {
        HomeSectionHeader(state.title, null, null)
        StreakSummaryCard(state.currentStreak, state.tip)
        ProgressRangeChips(state.availableRanges, selected, onSelect = { selected = it }, compact = compact, scope = "reviews")
        val heatmap = state.heatmap
        val hasHeatmapData = heatmap != null && heatmap.weeks.any { week -> week.cells.any { it.reviews > 0 } }
        // The per-day bar chart was removed in favor of the review calendar heatmap;
        // reviewsPerDay now only gates whether the selected range has data to summarize.
        val hasReviewData = selectedData.reviewsPerDay.values.any { it > 0 }
        if (!hasHeatmapData && !hasReviewData) {
            EmptyCharts()
        }
        heatmap?.let { populatedHeatmap ->
            if (hasHeatmapData) {
                ChartLeaf {
                    Text(copy.reviewCalendar, style = MaterialTheme.typography.titleMedium)
                    KaniHeatmapChart(populatedHeatmap, KaniTheme.colors.primary)
                }
            }
        }
        if (hasReviewData) {
            MiniMetrics(
                listOf(
                    ProgressAnalyticsCopy.totalReviewsLabel() to selectedData.totalReviews.valueLabel,
                    ProgressAnalyticsCopy.averagePerDayLabel() to selectedData.averagePerDay.valueLabel,
                    ProgressAnalyticsCopy.correctLabel() to selectedData.correct.valueLabel,
                    ProgressAnalyticsCopy.incorrectLabel() to selectedData.incorrect.valueLabel,
                    copy.bestDay to selectedData.bestDayLabel,
                )
            )
        }
    }
}

@Composable
private fun StreakSummaryCard(state: ProgressStreakMetricState, tip: String) {
    val copy = StatsDashboardCopy.forLocale()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ProgressStreakSummaryTag)
            .semantics(mergeDescendants = true) {
                contentDescription = progressAnalyticsStreakSummaryText(state, tip)
            },
        shape = KaniUiTokens.LeafShape,
        color = KaniTheme.colors.panel,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeroMetric(
                    ProgressAnalyticsCopy.currentStreakLabel(),
                    state.valueLabel,
                    Modifier.weight(1f).testTag(ProgressCurrentStreakMetricTag),
                )
                HeroMetric(
                    ProgressAnalyticsCopy.longestStreakLabel(),
                    copy.days(state.bestDays),
                    Modifier.weight(1f).testTag(ProgressLongestStreakMetricTag),
                )
            }
            Text(
                tip,
                style = MaterialTheme.typography.labelSmall,
                color = KaniTheme.colors.muted,
            )
        }
    }
}

@Composable
private fun AccuracySection(state: ProgressAccuracyRetentionState, compact: Boolean) {
    var selected by rememberSaveable { mutableStateOf(state.selectedRange) }
    val chart = state.rangeData[selected] ?: state.accuracyTrend
    StatsSection {
        HomeSectionHeader(state.title, null, null)
        ProgressRangeChips(state.availableRanges, selected, onSelect = { selected = it }, compact = compact, scope = "accuracy")
        if (chart.series.isEmpty() || state.retentionByCardType.isEmpty()) {
            EmptyCharts()
        } else {
            ChartLeaf { KaniLineChart(chart, chartColors()) }
            state.retentionByCardType.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(row.label, style = MaterialTheme.typography.bodyMedium)
                    Text(row.valueLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.categoryStatuses.forEach { StatusChip("${it.label} · ${it.status}") }
            }
        }
    }
}

@Composable
private fun LadderSection(state: ProgressAnalyticsState) {
    val ladder = state.progressByLevel
    StatsSection(alternate = true) {
        HomeSectionHeader(ladder.title, null, null)
        if (ladder.levelRows.isEmpty()) {
            EmptyCharts()
        } else {
            ladder.levelRows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(row.level, style = MaterialTheme.typography.bodyMedium)
                    Text("${row.learned} · ${row.percent}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (ladder.cumulativeProgress.series.isNotEmpty()) {
            ChartLeaf { KaniLineChart(ladder.cumulativeProgress, listOf(KaniTheme.colors.blue)) }
        }
    }
}

@Composable
private fun WeaknessSection(state: ProgressWeaknessInsightsState, onBrowseKanji: (String) -> Unit) {
    val copy = StatsDashboardCopy.forLocale()
    StatsSection {
        HomeSectionHeader(state.title, null, null)
        if (state.focusScoreAvailable) {
            Surface(shape = KaniUiTokens.LeafShape, color = KaniTheme.colors.panelSoft) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp).semantics { contentDescription = state.focusScore.accessibilityLabel },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🦀", modifier = Modifier.semantics { contentDescription = "Kani crab mascot" }, style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(ProgressAnalyticsCopy.focusScoreLabel(), style = MaterialTheme.typography.titleMedium)
                        Text("${state.focusScore.value} / ${state.focusScore.total}", style = MaterialTheme.typography.headlineSmall)
                        Text(state.focusScore.status, style = MaterialTheme.typography.labelSmall, color = KaniTheme.colors.coral)
                    }
                }
            }
        }
        ProgressConfusionCard(state.confusionPairs, onBrowseKanji)
        if (state.weaknessRows.isEmpty() && state.mostMissedKanji.isEmpty() && state.supportNeeded.isEmpty()) {
            EmptyCharts()
        } else {
            state.weaknessRows.forEach {
                Text("${it.label} · ${it.accuracyPercent}% · ${ProgressAnalyticsCopy.missesLabel(it.missedCount)}", style = MaterialTheme.typography.bodyMedium)
            }
            if (state.mostMissedKanji.isNotEmpty()) {
                Text(copy.mostMissedKanji, style = MaterialTheme.typography.titleMedium)
                MiniMetrics(state.mostMissedKanji.map { it.kanji to ProgressAnalyticsCopy.missesLabel(it.misses) })
            }
            if (state.supportNeeded.isNotEmpty()) {
                Text(copy.supportNeeded, style = MaterialTheme.typography.titleMedium)
                state.supportNeeded.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(listOf(row.label, row.targetLabel).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                        Text(row.count.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProgressConfusionCard(pairs: List<ProgressConfusionPairState>, onBrowseKanji: (String) -> Unit) {
    val copy = StatsDashboardCopy.forLocale()
    if (pairs.isEmpty()) {
        val copy = StatsEmptyStateCopy.confusion()
        HomeEmptyState(copy.title, copy.body)
        return
    }
    ChartLeaf {
        Text(copy.recentConfusionPairs, style = MaterialTheme.typography.titleMedium)
        Text(copy.lastNinetyDays, style = MaterialTheme.typography.labelSmall, color = KaniTheme.colors.muted)
        pairs.forEach { pair ->
            Row(
                Modifier.fillMaxWidth().testTag(ProgressConfusionRowTagPrefix + pair.firstKanji)
                    .clickable { onBrowseKanji(pair.firstKanji) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${pair.firstKanji} ↔ ${pair.secondKanji}", fontFamily = GamesKanjiFontFamily, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${pair.firstMeaning} / ${pair.secondMeaning}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${pair.firstKanji}→${pair.secondKanji} ×${pair.firstToSecond} · ${pair.secondKanji}→${pair.firstKanji} ×${pair.secondToFirst}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ProgressRangeChips(
    ranges: List<AnalyticsRange>,
    selected: AnalyticsRange,
    onSelect: (AnalyticsRange) -> Unit,
    compact: Boolean,
    scope: String,
) {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
        modifier = Modifier.fillMaxWidth().testTag(ProgressRangeChipRowTag),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 4.dp),
    ) {
        ranges.forEach { range ->
            val active = range == selected
            Surface(
                modifier = Modifier.testTag(ProgressRangeChipTagPrefix + scope + "-" + range.name).clickable { onSelect(range) },
                shape = KaniUiTokens.PillShape,
                color = if (active) KaniTheme.colors.primary else KaniTheme.colors.pill,
            ) {
                Text(
                    ProgressAnalyticsCopy.rangeLabel(range),
                    Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) KaniTheme.colors.onPrimary else KaniTheme.colors.ink,
                )
            }
        }
    }
}

@Composable
private fun MiniMetrics(values: List<Pair<String, String>>) {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { (label, value) ->
            Surface(shape = KaniUiTokens.PillShape, color = KaniTheme.colors.pill) {
                Text("$label · $value", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun StatusChip(text: String) {
    Surface(shape = KaniUiTokens.PillShape, color = KaniTheme.colors.panel) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EmptyCharts() {
    val copy = StatsEmptyStateCopy.charts()
    HomeEmptyState(copy.title, copy.body)
}

@Composable
private fun StatsSection(
    modifier: Modifier = Modifier,
    alternate: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = KaniUiTokens.PanelShape,
        color = if (alternate) KaniTheme.colors.panelSoft else KaniTheme.colors.surface,
        contentColor = KaniTheme.colors.ink,
        border = BorderStroke(1.dp, KaniTheme.colors.borderSoft),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun ChartLeaf(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier.fillMaxWidth(),
        shape = KaniUiTokens.LeafShape,
        color = KaniTheme.colors.panelSoft,
        contentColor = KaniTheme.colors.ink,
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun chartColors(): List<Color> = listOf(
    KaniTheme.colors.primary, KaniTheme.colors.teal, KaniTheme.colors.blue, KaniTheme.colors.gold,
)
