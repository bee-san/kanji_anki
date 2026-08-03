package dev.bee.kanjianki.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StatsDashboardTest {
    @Test
    fun aChartAxisLabelsItsTicks() {
        val axis = ChartAxis(axisMax = 10, ticks = listOf(0, 5, 10))
        assertEquals(listOf("0", "5", "10"), axis.labels)
    }

    @Test
    fun aMostMissedKanjiOpensItsBrowseDetail() {
        val missed = StatsMissedKanji(kanji = "脱", misses = 9)
        assertEquals(
            KaniAction.Navigation.Open(KaniDestination.Detail(kanji = "脱", fromBrowse = true)),
            missed.action,
        )
    }

    @Test
    fun aMissedKanjiRowIsAboutAKanji() {
        assertFailsWith<IllegalArgumentException> { StatsMissedKanji(kanji = " ", misses = 1) }
    }

    @Test
    fun aFullDashboardHoldsEverySectionAndChart() {
        // One construction touching every model class, so a renamed field fails here
        // rather than in a windowed render test — and so :presentation-api's own
        // coverage sees each class built.
        val dashboard = StatsDashboard(
            forecast = StatsForecast("Forecast", "at 20/day", lineChart()),
            overview = StatsOverview(
                title = "Overview",
                subtitle = "30 days",
                streakValue = "9",
                accuracyValue = "88%",
                reviewsTodayValue = "24",
                totalReviewsValue = "1,204",
                kanjiLearnedValue = "312",
                studyTimeValue = "4h",
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
                bestDayLabel = "Tue",
                tip = "mornings",
                accessibilitySummary = "bars",
                heatmap = ReviewHeatmap(
                    weeks = listOf(HeatmapWeek(cells = listOf(HeatmapCell(reviews = 3, intensity = 2)), monthLabel = "Jan")),
                    weekdayLabels = listOf("M"),
                    accessibilitySummary = "heatmap",
                ),
            ),
            accuracy = StatsAccuracy(
                title = "Accuracy",
                selectedRange = StatsRange.SEVEN_DAYS,
                availableRanges = StatsRange.entries,
                accuracyTrend = lineChart(),
                retentionByCardType = listOf(StatsRetentionRow("Recognition", 90, "90%")),
                retentionSummary = "holding",
                categoryStatuses = listOf(StatsCategoryStatus("Reading", "on track")),
            ),
            progressByLevel = StatsByLevel(
                title = "By level",
                overallLearnedLabel = "312 of 2,136",
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
                mostMissedKanji = listOf(StatsMissedKanji("脱", 9)),
                supportNeeded = listOf(StatsSupportNeed("脱", "2 more", 1)),
                confusionPairs = listOf(StatsConfusionPair("脱", "説", "take off", "explain", 3, 1)),
            ),
        )

        assertEquals(90, dashboard.accuracy.retentionByCardType.single().percent)
        assertEquals(StatsRange.THIRTY_DAYS, dashboard.reviews.selectedRange)
        assertEquals(2, dashboard.reviews.heatmap?.weeks?.single()?.cells?.single()?.intensity)
        assertEquals("Jan", dashboard.reviews.heatmap?.weeks?.single()?.monthLabel)
        assertEquals(1, dashboard.weakness.confusionPairs.single().secondToFirst)
        assertEquals(30, StatsRange.THIRTY_DAYS.days)
        assertEquals("312 of 2,136", dashboard.progressByLevel.overallLearnedLabel)
        assertEquals("on track", dashboard.accuracy.categoryStatuses.single().status)
        assertEquals("2 more", dashboard.weakness.supportNeeded.single().targetLabel)
    }

    @Test
    fun aSeriesDefaultsToSolidAndCanBeDashed() {
        assertEquals(false, StatsSeries("s", listOf(1, 2)).dashed)
        assertEquals(true, StatsSeries("goal", listOf(6), dashed = true).dashed)
    }

    @Test
    fun aDistributionSegmentCarriesItsValueAndPercent() {
        val segment = StatsDistributionSegment("Recognition", value = 60, percent = 60)
        assertEquals(60, segment.value)
        assertEquals(60, segment.percent)
    }

    private fun lineChart() = StatsLineChart(
        title = "trend",
        xAxisLabels = listOf("Mon", "Fri"),
        series = listOf(StatsSeries("reviews", listOf(3, 8))),
        accessibilitySummary = "trend",
        axis = ChartAxis(axisMax = 10, ticks = listOf(0, 10)),
    )

    private fun barChart() = StatsBarChart(
        title = "bars",
        labels = listOf("Mon", "Tue"),
        values = listOf(4, 9),
        accessibilitySummary = "bars",
        axis = ChartAxis(axisMax = 10, ticks = listOf(0, 10)),
    )

    private fun distribution() = StatsDistribution(
        title = "split",
        segments = listOf(StatsDistributionSegment("A", 60, 60)),
        accessibilitySummary = "split",
    )
}
