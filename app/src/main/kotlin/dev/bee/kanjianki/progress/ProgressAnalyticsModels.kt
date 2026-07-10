package dev.bee.kanjianki.progress

import dev.bee.kanjianki.core.ChartAxisPolicy
import dev.bee.kanjianki.core.ReviewHeatmapPolicy

enum class AnalyticsRange(val days: Int, val label: String) {
    SEVEN_DAYS(7, "7 days"),
    THIRTY_DAYS(30, "30 days"),
    NINETY_DAYS(90, "90 days"),
}

data class ProgressAnalyticsState(
    val generatedAtMillis: Long,
    val overview: ProgressOverviewState,
    val reviewsAnalytics: ProgressReviewsAnalyticsState,
    val accuracyRetention: ProgressAccuracyRetentionState,
    val progressByLevel: ProgressByLevelState,
    val weaknessInsights: ProgressWeaknessInsightsState,
    val forecast: ProgressForecastState? = null,
)

data class ProgressForecastState(
    val totalItems: Int,
    val headline: String,
    val assumption: String,
    val burnDown: ProgressLineChartState,
)

data class ProgressOverviewState(
    val title: String,
    val subtitle: String,
    val totalReviews: ProgressCountMetricState,
    val accuracy: ProgressCountMetricState,
    val currentStreak: ProgressStreakMetricState,
    val kanjiLearned: ProgressCountMetricState,
    val focusSessions: ProgressCountMetricState,
    val studyTime: ProgressDurationMetricState,
    val reviewsToday: ProgressCountMetricState = ProgressCountMetricState(0, "0"),
    val reviewsOverTime: ProgressLineChartState,
    val cardTypeBreakdown: ProgressDistributionChartState,
    val correctIncorrectBreakdown: ProgressDistributionChartState,
)

data class ProgressCountMetricState(
    val value: Int,
    val valueLabel: String,
    val deltaLabel: String? = null,
    val detailLabel: String? = null,
)

data class ProgressDurationMetricState(
    val millis: Long,
    val valueLabel: String,
    val deltaLabel: String? = null,
    val detailLabel: String? = null,
)

data class ProgressStreakMetricState(
    val currentDays: Int,
    val bestDays: Int,
    val valueLabel: String,
    val detailLabel: String? = null,
)

data class ProgressReviewsAnalyticsState(
    val title: String,
    val selectedRange: AnalyticsRange,
    val availableRanges: List<AnalyticsRange>,
    val reviewsPerDay: ProgressBarChartState,
    val totalReviews: ProgressCountMetricState,
    val averagePerDay: ProgressCountMetricState,
    val correct: ProgressCountMetricState,
    val incorrect: ProgressCountMetricState,
    val bestDayLabel: String,
    val currentStreak: ProgressStreakMetricState,
    val tip: String,
    val accessibilitySummary: String,
    val rangeData: Map<AnalyticsRange, ProgressReviewsRangeData> = emptyMap(),
    val heatmap: ReviewHeatmapPolicy.Grid? = null,
)

data class ProgressReviewsRangeData(
    val reviewsPerDay: ProgressBarChartState,
    val totalReviews: ProgressCountMetricState,
    val averagePerDay: ProgressCountMetricState,
    val correct: ProgressCountMetricState,
    val incorrect: ProgressCountMetricState,
    val bestDayLabel: String,
    val accessibilitySummary: String,
)

data class ProgressAccuracyRetentionState(
    val title: String,
    val selectedRange: AnalyticsRange,
    val availableRanges: List<AnalyticsRange>,
    val accuracyTrend: ProgressLineChartState,
    val retentionByCardType: List<ProgressRetentionRowState>,
    val retentionSummary: String,
    val categoryStatuses: List<ProgressCategoryStatusState>,
    val rangeData: Map<AnalyticsRange, ProgressLineChartState> = emptyMap(),
)

data class ProgressRetentionRowState(
    val label: String,
    val percent: Int,
    val valueLabel: String,
)

data class ProgressCategoryStatusState(
    val label: String,
    val status: String,
)

data class ProgressByLevelState(
    val title: String,
    val selectedFilterLabel: String,
    val overallLearned: ProgressFractionMetricState,
    val levelRows: List<ProgressLevelRowState>,
    val cumulativeProgress: ProgressLineChartState,
)

data class ProgressFractionMetricState(
    val value: Int,
    val total: Int,
    val percent: Int,
    val valueLabel: String? = null,
    val accessibilityLabel: String,
)

data class ProgressLevelRowState(
    val level: String,
    val learned: Int,
    val total: Int,
    val percent: Int,
)

data class ProgressWeaknessInsightsState(
    val title: String,
    val focusScore: ProgressScoreMetricState,
    val weaknessRows: List<ProgressWeaknessRowState>,
    val mostMissedKanji: List<ProgressMissedKanjiState>,
    val supportNeeded: List<ProgressSupportNeedState>,
    val confusionPairs: List<ProgressConfusionPairState> = emptyList(),
    val focusScoreAvailable: Boolean = true,
)

data class ProgressConfusionPairState(
    val firstKanji: String,
    val secondKanji: String,
    val firstMeaning: String,
    val secondMeaning: String,
    val firstToSecond: Int,
    val secondToFirst: Int,
)

data class ProgressScoreMetricState(
    val value: Int,
    val total: Int,
    val status: String,
    val accessibilityLabel: String,
)

data class ProgressWeaknessRowState(
    val label: String,
    val accuracyPercent: Int,
    val missedCount: Int,
    val severity: String,
)

data class ProgressMissedKanjiState(
    val kanji: String,
    val misses: Int,
)

data class ProgressSupportNeedState(
    val label: String,
    val targetLabel: String,
    val count: Int,
)

data class ProgressBarChartState(
    val title: String,
    val labels: List<String>,
    val values: List<Int>,
    val accessibilitySummary: String,
    val selectedRange: AnalyticsRange? = null,
    val axis: ChartAxisPolicy.Axis = ChartAxisPolicy.forValues(values),
)

data class ProgressLineChartState(
    val title: String,
    val xAxisLabels: List<String>,
    val series: List<ProgressSeriesState>,
    val accessibilitySummary: String,
    val selectedRange: AnalyticsRange? = null,
    val tooltipLabel: String? = null,
    val axis: ChartAxisPolicy.Axis = ChartAxisPolicy.forValues(series.flatMap { it.values }),
)

data class ProgressSeriesState(
    val label: String,
    val values: List<Int>,
    val style: ProgressSeriesStyle = ProgressSeriesStyle.SOLID,
)

enum class ProgressSeriesStyle {
    SOLID,
    DASHED,
}

data class ProgressDistributionChartState(
    val title: String,
    val segments: List<ProgressDistributionSegmentState>,
    val accessibilitySummary: String,
)

data class ProgressDistributionSegmentState(
    val label: String,
    val value: Int,
    val percent: Int,
)
