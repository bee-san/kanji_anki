package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.core.ChartAxisPolicy
import dev.bee.kanjianki.core.ReviewHeatmapPolicy
import dev.bee.kanjianki.progress.AnalyticsRange
import dev.bee.kanjianki.progress.ProgressAccuracyRetentionState
import dev.bee.kanjianki.progress.ProgressAnalyticsState
import dev.bee.kanjianki.progress.ProgressBarChartState
import dev.bee.kanjianki.progress.ProgressByLevelState
import dev.bee.kanjianki.progress.ProgressDistributionChartState
import dev.bee.kanjianki.progress.ProgressForecastState
import dev.bee.kanjianki.progress.ProgressLineChartState
import dev.bee.kanjianki.progress.ProgressOverviewState
import dev.bee.kanjianki.progress.ProgressReviewsAnalyticsState
import dev.bee.kanjianki.progress.ProgressWeaknessInsightsState
import dev.bee.kanjianki.presentation.ChartAxis
import dev.bee.kanjianki.presentation.HeatmapCell
import dev.bee.kanjianki.presentation.HeatmapWeek
import dev.bee.kanjianki.presentation.ReviewHeatmap
import dev.bee.kanjianki.presentation.StatsAccuracy
import dev.bee.kanjianki.presentation.StatsBarChart
import dev.bee.kanjianki.presentation.StatsByLevel
import dev.bee.kanjianki.presentation.StatsCategoryStatus
import dev.bee.kanjianki.presentation.StatsConfusionPair
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

/**
 * Maps the shared `ProgressAnalyticsState` to the portable `StatsDashboard`.
 *
 * The analytics computation is `:progress-core`'s — the same one the Android host
 * runs — so this is a pure field-for-field translation, not a second computation.
 * Its only real work is replacing the two `:core` chart types the leaf module cannot
 * see (`ChartAxisPolicy.Axis` → [ChartAxis], `ReviewHeatmapPolicy.Grid` →
 * [ReviewHeatmap]) and flattening the Android model's per-metric value objects to the
 * value labels the shared dashboard renders.
 */
internal object DesktopStatsModel {
    fun dashboard(state: ProgressAnalyticsState): StatsDashboard = StatsDashboard(
        forecast = state.forecast?.let(::forecast),
        overview = overview(state.overview),
        reviews = reviews(state.reviewsAnalytics),
        accuracy = accuracy(state.accuracyRetention),
        progressByLevel = byLevel(state.progressByLevel),
        weakness = weakness(state.weaknessInsights),
    )

    private fun forecast(state: ProgressForecastState): StatsForecast = StatsForecast(
        headline = state.headline,
        assumption = state.assumption,
        burnDown = lineChart(state.burnDown),
    )

    private fun overview(state: ProgressOverviewState): StatsOverview = StatsOverview(
        title = state.title,
        subtitle = state.subtitle,
        streakValue = state.currentStreak.valueLabel,
        accuracyValue = state.accuracy.valueLabel,
        reviewsTodayValue = state.reviewsToday.valueLabel,
        totalReviewsValue = state.totalReviews.valueLabel,
        kanjiLearnedValue = state.kanjiLearned.valueLabel,
        studyTimeValue = state.studyTime.valueLabel,
        reviewsOverTime = lineChart(state.reviewsOverTime),
        cardTypeBreakdown = distribution(state.cardTypeBreakdown),
        correctIncorrectBreakdown = distribution(state.correctIncorrectBreakdown),
    )

    private fun reviews(state: ProgressReviewsAnalyticsState): StatsReviews = StatsReviews(
        title = state.title,
        selectedRange = range(state.selectedRange),
        availableRanges = state.availableRanges.map(::range),
        reviewsPerDay = barChart(state.reviewsPerDay),
        totalReviewsValue = state.totalReviews.valueLabel,
        averagePerDayValue = state.averagePerDay.valueLabel,
        correctValue = state.correct.valueLabel,
        incorrectValue = state.incorrect.valueLabel,
        bestDayLabel = state.bestDayLabel,
        tip = state.tip,
        accessibilitySummary = state.accessibilitySummary,
        heatmap = state.heatmap?.let(::heatmap),
    )

    private fun accuracy(state: ProgressAccuracyRetentionState): StatsAccuracy = StatsAccuracy(
        title = state.title,
        selectedRange = range(state.selectedRange),
        availableRanges = state.availableRanges.map(::range),
        accuracyTrend = lineChart(state.accuracyTrend),
        retentionByCardType = state.retentionByCardType.map {
            StatsRetentionRow(label = it.label, percent = it.percent, valueLabel = it.valueLabel)
        },
        retentionSummary = state.retentionSummary,
        categoryStatuses = state.categoryStatuses.map {
            StatsCategoryStatus(label = it.label, status = it.status)
        },
    )

    private fun byLevel(state: ProgressByLevelState): StatsByLevel = StatsByLevel(
        title = state.title,
        overallLearnedLabel = state.overallLearned.accessibilityLabel,
        overallPercent = state.overallLearned.percent,
        levelRows = state.levelRows.map {
            StatsLevelRow(level = it.level, learned = it.learned, total = it.total, percent = it.percent)
        },
        cumulativeProgress = lineChart(state.cumulativeProgress),
    )

    private fun weakness(state: ProgressWeaknessInsightsState): StatsWeakness = StatsWeakness(
        title = state.title,
        focusScoreValue = state.focusScore.accessibilityLabel,
        focusScoreStatus = state.focusScore.status,
        focusScoreAvailable = state.focusScoreAvailable,
        weaknessRows = state.weaknessRows.map {
            StatsWeaknessRow(
                label = it.label,
                accuracyPercent = it.accuracyPercent,
                missedCount = it.missedCount,
                severity = it.severity,
            )
        },
        mostMissedKanji = state.mostMissedKanji.map { StatsMissedKanji(kanji = it.kanji, misses = it.misses) },
        supportNeeded = state.supportNeeded.map {
            StatsSupportNeed(label = it.label, targetLabel = it.targetLabel, count = it.count)
        },
        confusionPairs = state.confusionPairs.map {
            StatsConfusionPair(
                firstKanji = it.firstKanji,
                secondKanji = it.secondKanji,
                firstMeaning = it.firstMeaning,
                secondMeaning = it.secondMeaning,
                firstToSecond = it.firstToSecond,
                secondToFirst = it.secondToFirst,
            )
        },
    )

    private fun lineChart(chart: ProgressLineChartState): StatsLineChart = StatsLineChart(
        title = chart.title,
        xAxisLabels = chart.xAxisLabels,
        series = chart.series.map {
            StatsSeries(
                label = it.label,
                values = it.values,
                dashed = it.style == dev.bee.kanjianki.progress.ProgressSeriesStyle.DASHED,
            )
        },
        accessibilitySummary = chart.accessibilitySummary,
        axis = axis(chart.axis),
    )

    private fun barChart(chart: ProgressBarChartState): StatsBarChart = StatsBarChart(
        title = chart.title,
        labels = chart.labels,
        values = chart.values,
        accessibilitySummary = chart.accessibilitySummary,
        axis = axis(chart.axis),
    )

    private fun distribution(chart: ProgressDistributionChartState): StatsDistribution = StatsDistribution(
        title = chart.title,
        segments = chart.segments.map {
            StatsDistributionSegment(label = it.label, value = it.value, percent = it.percent)
        },
        accessibilitySummary = chart.accessibilitySummary,
    )

    private fun heatmap(grid: ReviewHeatmapPolicy.Grid): ReviewHeatmap = ReviewHeatmap(
        weeks = grid.weeks.map { week ->
            HeatmapWeek(
                cells = week.cells.map { HeatmapCell(reviews = it.reviews, intensity = it.intensity) },
                monthLabel = week.monthLabel,
            )
        },
        weekdayLabels = grid.weekdayLabels,
        accessibilitySummary = grid.accessibilitySummary,
    )

    private fun axis(axis: ChartAxisPolicy.Axis): ChartAxis =
        ChartAxis(axisMax = axis.axisMax, ticks = axis.ticks)

    private fun range(range: AnalyticsRange): StatsRange = when (range) {
        AnalyticsRange.SEVEN_DAYS -> StatsRange.SEVEN_DAYS
        AnalyticsRange.THIRTY_DAYS -> StatsRange.THIRTY_DAYS
        AnalyticsRange.NINETY_DAYS -> StatsRange.NINETY_DAYS
    }
}
