package dev.bee.kanjianki.presentation

/**
 * The progress-analytics dashboard, as portable data both hosts render.
 *
 * The Android host computes a display-ready `ProgressAnalyticsState` in
 * `:app/progress` from `StatsSnapshot`; this is the same shape with its two `:core`
 * dependencies replaced by portable equivalents — `ChartAxisPolicy.Axis` becomes
 * [ChartAxis] and `ReviewHeatmapPolicy.Grid` becomes [ReviewHeatmap] — so a leaf
 * feature that sees only `:presentation-api` can render it. The analytics computation
 * stays in `:core`/`:application`; a host maps its snapshot into this.
 *
 * Every string is already resolved by the host — the dashboard is dense with
 * host-computed prose about the user's own collection — so these are plain `String`s,
 * not [UiText], matching how the Android model carried them.
 */
data class StatsDashboard(
    val overview: StatsOverview,
    val reviews: StatsReviews,
    val accuracy: StatsAccuracy,
    val progressByLevel: StatsByLevel,
    val weakness: StatsWeakness,
    val forecast: StatsForecast? = null,
)

/** The value range and tick marks for a chart's axis. */
data class ChartAxis(
    val axisMax: Int,
    val ticks: List<Int>,
) {
    val labels: List<String>
        get() = ticks.map { it.toString() }
}

/** How far through the session the user is on one range of days. */
enum class StatsRange(val days: Int) {
    SEVEN_DAYS(7),
    THIRTY_DAYS(30),
    NINETY_DAYS(90),
}

data class StatsForecast(
    val headline: String,
    val assumption: String,
    val burnDown: StatsLineChart,
)

data class StatsOverview(
    val title: String,
    val subtitle: String,
    val streakValue: String,
    val accuracyValue: String,
    val reviewsTodayValue: String,
    val totalReviewsValue: String,
    val kanjiLearnedValue: String,
    val studyTimeValue: String,
    val reviewsOverTime: StatsLineChart,
    val cardTypeBreakdown: StatsDistribution,
    val correctIncorrectBreakdown: StatsDistribution,
)

data class StatsReviews(
    val title: String,
    val selectedRange: StatsRange,
    val availableRanges: List<StatsRange>,
    val reviewsPerDay: StatsBarChart,
    val totalReviewsValue: String,
    val averagePerDayValue: String,
    val correctValue: String,
    val incorrectValue: String,
    val bestDayLabel: String,
    val tip: String,
    val accessibilitySummary: String,
    val heatmap: ReviewHeatmap? = null,
)

data class StatsAccuracy(
    val title: String,
    val selectedRange: StatsRange,
    val availableRanges: List<StatsRange>,
    val accuracyTrend: StatsLineChart,
    val retentionByCardType: List<StatsRetentionRow>,
    val retentionSummary: String,
    val categoryStatuses: List<StatsCategoryStatus>,
)

data class StatsRetentionRow(
    val label: String,
    val percent: Int,
    val valueLabel: String,
)

data class StatsCategoryStatus(
    val label: String,
    val status: String,
)

data class StatsByLevel(
    val title: String,
    val overallLearnedLabel: String,
    val overallPercent: Int,
    val levelRows: List<StatsLevelRow>,
    val cumulativeProgress: StatsLineChart,
)

data class StatsLevelRow(
    val level: String,
    val learned: Int,
    val total: Int,
    val percent: Int,
)

data class StatsWeakness(
    val title: String,
    val focusScoreValue: String,
    val focusScoreStatus: String,
    val focusScoreAvailable: Boolean,
    val weaknessRows: List<StatsWeaknessRow>,
    val mostMissedKanji: List<StatsMissedKanji>,
    val supportNeeded: List<StatsSupportNeed>,
    val confusionPairs: List<StatsConfusionPair> = emptyList(),
)

data class StatsWeaknessRow(
    val label: String,
    val accuracyPercent: Int,
    val missedCount: Int,
    val severity: String,
)

data class StatsMissedKanji(
    val kanji: String,
    val misses: Int,
) {
    init {
        require(kanji.isNotBlank()) { "a missed-kanji row is about a kanji" }
    }

    /** Opening a missed kanji goes to its browse detail. */
    val action: KaniAction
        get() = KaniAction.Navigation.Open(KaniDestination.Detail(kanji = kanji, fromBrowse = true))
}

data class StatsSupportNeed(
    val label: String,
    val targetLabel: String,
    val count: Int,
)

data class StatsConfusionPair(
    val firstKanji: String,
    val secondKanji: String,
    val firstMeaning: String,
    val secondMeaning: String,
    val firstToSecond: Int,
    val secondToFirst: Int,
)

data class StatsBarChart(
    val title: String,
    val labels: List<String>,
    val values: List<Int>,
    val accessibilitySummary: String,
    val axis: ChartAxis,
)

data class StatsLineChart(
    val title: String,
    val xAxisLabels: List<String>,
    val series: List<StatsSeries>,
    val accessibilitySummary: String,
    val axis: ChartAxis,
)

data class StatsSeries(
    val label: String,
    val values: List<Int>,
    val dashed: Boolean = false,
)

data class StatsDistribution(
    val title: String,
    val segments: List<StatsDistributionSegment>,
    val accessibilitySummary: String,
)

data class StatsDistributionSegment(
    val label: String,
    val value: Int,
    val percent: Int,
)

/** The review-frequency heatmap: weeks of day cells with intensity. */
data class ReviewHeatmap(
    val weeks: List<HeatmapWeek>,
    val weekdayLabels: List<String>,
    val accessibilitySummary: String,
)

data class HeatmapWeek(
    val cells: List<HeatmapCell>,
    val monthLabel: String? = null,
)

data class HeatmapCell(
    val reviews: Int,
    val intensity: Int,
)
