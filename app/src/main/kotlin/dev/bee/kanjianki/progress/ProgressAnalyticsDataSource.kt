package dev.bee.kanjianki.progress

import java.lang.System.currentTimeMillis

interface ProgressAnalyticsDataSource {
    fun snapshot(nowMillis: Long): ProgressAnalyticsState
}

object DemoProgressAnalyticsDataSource : ProgressAnalyticsDataSource {
    override fun snapshot(nowMillis: Long): ProgressAnalyticsState = progressAnalyticsDemoSnapshot(nowMillis)
}

object SampleProgressAnalyticsDataSource : ProgressAnalyticsDataSource {
    override fun snapshot(nowMillis: Long): ProgressAnalyticsState = progressAnalyticsDemoSnapshot(nowMillis)
}

fun progressAnalyticsSampleSnapshot(nowMillis: Long = currentTimeMillis()): ProgressAnalyticsState =
    progressAnalyticsDemoSnapshot(nowMillis)

fun progressAnalyticsDemoSnapshot(nowMillis: Long = currentTimeMillis()): ProgressAnalyticsState {
    val availableRanges = listOf(
        AnalyticsRange.SEVEN_DAYS,
        AnalyticsRange.THIRTY_DAYS,
        AnalyticsRange.NINETY_DAYS,
    )

    return ProgressAnalyticsState(
        generatedAtMillis = nowMillis,
        overview = ProgressOverviewState(
            title = "Stats overview",
            subtitle = "Your learning at a glance",
            totalReviews = ProgressCountMetricState(
                value = 2_842,
                valueLabel = "2,842",
                deltaLabel = "+18% vs last 7d",
            ),
            accuracy = ProgressCountMetricState(
                value = 92,
                valueLabel = "92%",
                deltaLabel = "+4% vs last 7d",
            ),
            currentStreak = ProgressStreakMetricState(
                currentDays = 6,
                bestDays = 14,
                valueLabel = "6 days",
                detailLabel = "Best 14 days",
            ),
            kanjiLearned = ProgressCountMetricState(
                value = 126,
                valueLabel = "126",
                deltaLabel = "+7 this week",
            ),
            focusSessions = ProgressCountMetricState(
                value = 9,
                valueLabel = "9",
                detailLabel = "This week",
            ),
            studyTime = ProgressDurationMetricState(
                millis = 16_320_000L,
                valueLabel = "4h 32m",
                detailLabel = "This week",
            ),
            reviewsOverTime = ProgressLineChartState(
                title = "Reviews over time",
                xAxisLabels = listOf("Apr 19", "Apr 26", "May 3", "May 10", "May 17", "May 18"),
                yAxisLabels = listOf("0", "60", "120", "180"),
                series = listOf(
                    ProgressSeriesState(
                        label = "Reviews",
                        values = listOf(96, 101, 109, 118, 136, 142),
                    ),
                ),
                accessibilitySummary = "Reviews over time, 30-day range. Total reviews 2,842. Final point May 18 with 142 reviews. Trend is generally upward with small dips.",
                selectedRange = AnalyticsRange.THIRTY_DAYS,
                tooltipLabel = "May 18, 142 reviews",
            ),
            cardTypeBreakdown = ProgressDistributionChartState(
                title = "Card type breakdown",
                segments = listOf(
                    ProgressDistributionSegmentState(label = "Meaning", value = 1_079, percent = 38),
                    ProgressDistributionSegmentState(label = "Reading", value = 767, percent = 27),
                    ProgressDistributionSegmentState(label = "Writing", value = 512, percent = 18),
                    ProgressDistributionSegmentState(label = "Similar kanji", value = 484, percent = 17),
                ),
                accessibilitySummary = "Card type breakdown. Total 2,842 reviews. Meaning 38 percent, Reading 27 percent, Writing 18 percent, Similar kanji 17 percent.",
            ),
            correctIncorrectBreakdown = ProgressDistributionChartState(
                title = "Correct vs incorrect",
                segments = listOf(
                    ProgressDistributionSegmentState(label = "Correct", value = 2_615, percent = 92),
                    ProgressDistributionSegmentState(label = "Incorrect", value = 227, percent = 8),
                ),
                accessibilitySummary = "Correct vs incorrect. Correct 2,615 reviews, 92 percent. Incorrect 227 reviews, 8 percent. Total 2,842 reviews.",
            ),
        ),
        reviewsAnalytics = ProgressReviewsAnalyticsState(
            title = "Reviews analytics",
            selectedRange = AnalyticsRange.SEVEN_DAYS,
            availableRanges = availableRanges,
            reviewsPerDay = ProgressBarChartState(
                title = "Reviews per day",
                labels = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"),
                values = listOf(128, 96, 142, 186, 204, 148, 162),
                accessibilitySummary = "Reviews per day, 7-day range. 1,066 total reviews, average 152 per day. Correct 975, incorrect 91. Best day Friday with 204 reviews.",
                selectedRange = AnalyticsRange.SEVEN_DAYS,
            ),
            totalReviews = ProgressCountMetricState(
                value = 1_066,
                valueLabel = "1,066",
            ),
            averagePerDay = ProgressCountMetricState(
                value = 152,
                valueLabel = "152",
            ),
            correct = ProgressCountMetricState(
                value = 975,
                valueLabel = "975",
            ),
            incorrect = ProgressCountMetricState(
                value = 91,
                valueLabel = "91",
            ),
            bestDayLabel = "Friday",
            currentStreak = ProgressStreakMetricState(
                currentDays = 6,
                bestDays = 14,
                valueLabel = "6 days",
                detailLabel = "Best 14 days",
            ),
            tip = "Keep the streak going with a short review session today.",
            accessibilitySummary = "Reviews per day, 7-day range. 1,066 total reviews, average 152 per day. Correct 975, incorrect 91. Best day Friday with 204 reviews.",
        ),
        accuracyRetention = ProgressAccuracyRetentionState(
            title = "Accuracy & retention",
            selectedRange = AnalyticsRange.THIRTY_DAYS,
            availableRanges = availableRanges,
            accuracyTrend = ProgressLineChartState(
                title = "Accuracy over time",
                xAxisLabels = listOf("Apr 19", "Apr 26", "May 3", "May 10", "May 17", "May 18"),
                yAxisLabels = listOf("70", "75", "80", "85", "90", "95"),
                series = listOf(
                    ProgressSeriesState(
                        label = "Accuracy %",
                        values = listOf(79, 81, 83, 86, 89, 92),
                    ),
                    ProgressSeriesState(
                        label = "7-day avg",
                        values = listOf(78, 80, 82, 85, 88, 91),
                        style = ProgressSeriesStyle.DASHED,
                    ),
                ),
                accessibilitySummary = "Accuracy over time, 30-day range. Current accuracy is 92 percent on May 18. Accuracy has generally increased over the past 30 days.",
                selectedRange = AnalyticsRange.THIRTY_DAYS,
                tooltipLabel = "May 18, 92 percent",
            ),
            retentionByCardType = listOf(
                ProgressRetentionRowState(label = "Meaning", percent = 93, valueLabel = "93%"),
                ProgressRetentionRowState(label = "Reading", percent = 90, valueLabel = "90%"),
                ProgressRetentionRowState(label = "Writing", percent = 85, valueLabel = "85%"),
                ProgressRetentionRowState(label = "Similar kanji", percent = 78, valueLabel = "78%"),
            ),
            retentionSummary = "Retention by card type. Meaning 93 percent, Reading 90 percent, Writing 85 percent, Similar kanji 78 percent.",
            categoryStatuses = listOf(
                ProgressCategoryStatusState(label = "Meaning", status = "Excellent"),
                ProgressCategoryStatusState(label = "Reading", status = "Great"),
                ProgressCategoryStatusState(label = "Writing", status = "Good"),
                ProgressCategoryStatusState(label = "Similar kanji", status = "Needs focus"),
            ),
        ),
        progressByLevel = ProgressByLevelState(
            title = "Progress by level",
            selectedFilterLabel = "All levels",
            overallLearned = ProgressFractionMetricState(
                value = 126,
                total = 1_026,
                percent = 12,
                valueLabel = "126 / 1,026",
                accessibilityLabel = "Progress by level, All levels. 126 of 1,026 kanji learned, 12 percent complete.",
            ),
            levelRows = listOf(
                ProgressLevelRowState(level = "N5", learned = 58, total = 90, percent = 64),
                ProgressLevelRowState(level = "N4", learned = 41, total = 150, percent = 27),
                ProgressLevelRowState(level = "N3", learned = 31, total = 210, percent = 15),
                ProgressLevelRowState(level = "N2", learned = 18, total = 270, percent = 7),
                ProgressLevelRowState(level = "N1", learned = 6, total = 306, percent = 2),
            ),
            cumulativeProgress = ProgressLineChartState(
                title = "Cumulative progress",
                xAxisLabels = listOf("Apr 19", "Apr 26", "May 3", "May 10", "May 17"),
                yAxisLabels = listOf("0", "50", "100", "150"),
                series = listOf(
                    ProgressSeriesState(
                        label = "All levels",
                        values = listOf(25, 48, 72, 103, 135),
                    ),
                ),
                accessibilitySummary = "Cumulative progress by level. All levels selected. Progress rises from 25 to 135 learned kanji across the displayed range.",
                selectedRange = AnalyticsRange.THIRTY_DAYS,
                tooltipLabel = "May 17, 135 learned kanji",
            ),
        ),
        weaknessInsights = ProgressWeaknessInsightsState(
            title = "Weakness insights",
            focusScore = ProgressScoreMetricState(
                value = 72,
                total = 100,
                status = "Needs improvement",
                accessibilityLabel = "Focus score 72 out of 100. Needs improvement.",
            ),
            weaknessRows = listOf(
                ProgressWeaknessRowState(label = "Meaning", accuracyPercent = 81, missedCount = 42, severity = "High"),
                ProgressWeaknessRowState(label = "Reading", accuracyPercent = 84, missedCount = 31, severity = "High"),
                ProgressWeaknessRowState(label = "Type meaning", accuracyPercent = 79, missedCount = 28, severity = "Medium"),
                ProgressWeaknessRowState(label = "Similar kanji", accuracyPercent = 78, missedCount = 24, severity = "Medium"),
            ),
            mostMissedKanji = listOf(
                ProgressMissedKanjiState(kanji = "亜", misses = 28),
                ProgressMissedKanjiState(kanji = "勉", misses = 22),
                ProgressMissedKanjiState(kanji = "遣", misses = 18),
                ProgressMissedKanjiState(kanji = "複", misses = 15),
                ProgressMissedKanjiState(kanji = "誤", misses = 12),
            ),
            supportNeeded = listOf(
                ProgressSupportNeedState(label = "Meaning", targetLabel = "Kanji", count = 42),
                ProgressSupportNeedState(label = "Reading", targetLabel = "Kanji", count = 31),
                ProgressSupportNeedState(label = "Type meaning", targetLabel = "", count = 28),
                ProgressSupportNeedState(label = "Similar kanji", targetLabel = "", count = 24),
            ),
        ),
    )
}
