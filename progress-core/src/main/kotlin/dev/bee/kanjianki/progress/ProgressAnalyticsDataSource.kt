package dev.bee.kanjianki.progress

import dev.bee.kanjianki.core.ForecastTextCopy
import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.core.ReviewHeatmapPolicy
import dev.bee.kanjianki.core.StatsDashboardCopy
import dev.bee.kanjianki.core.StatsValueFormatter
import dev.bee.kanjianki.core.TaskTypeAccuracyPolicy
import java.lang.System.currentTimeMillis
import java.util.Locale
import java.util.TimeZone

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
    val locale = Locale.getDefault()
    val text = DemoStatsCopy(locale)
    val copy = StatsDashboardCopy.forLocale(locale)
    val forecastCopy = ForecastTextCopy.forLocale(locale)
    val availableRanges = listOf(
        AnalyticsRange.SEVEN_DAYS,
        AnalyticsRange.THIRTY_DAYS,
        AnalyticsRange.NINETY_DAYS,
    )
    val dates = DEMO_DATE_MILLIS.map(text::date)
    val groups = listOf(
        TaskTypeAccuracyPolicy.Group.MEANING,
        TaskTypeAccuracyPolicy.Group.READING,
        TaskTypeAccuracyPolicy.Group.WRITING,
        TaskTypeAccuracyPolicy.Group.DISCRIMINATION,
    )
    val groupLabels = groups.map(copy::group)
    val reviewSummary = text.value(
        "Reviews per day, 7-day range. 1,066 total reviews, average 152 per day. Correct 975, incorrect 91. Best day Friday with 204 reviews.",
        "7日間の復習数。合計1,066件、1日平均152件、正解975件、不正解91件。最多は金曜の204件です。",
    )

    return ProgressAnalyticsState(
        generatedAtMillis = nowMillis,
        overview = ProgressOverviewState(
            title = copy.statsOverview,
            subtitle = copy.overviewSubtitle,
            totalReviews = ProgressCountMetricState(2_842, text.integer(2_842), copy.deltaVsPreviousSeven("+18%")),
            accuracy = ProgressCountMetricState(92, "92%", copy.deltaVsPreviousThirty("+4%")),
            currentStreak = ProgressStreakMetricState(6, 14, copy.days(6), copy.bestDays(14)),
            kanjiLearned = ProgressCountMetricState(135, text.integer(135), copy.thisWeekDelta("7")),
            focusSessions = ProgressCountMetricState(9, text.integer(9), detailLabel = copy.thisWeek),
            studyTime = ProgressDurationMetricState(16_320_000L, StatsValueFormatter.duration(16_320_000L, locale), detailLabel = copy.thisWeek),
            reviewsToday = ProgressCountMetricState(42, text.integer(42)),
            reviewsOverTime = ProgressLineChartState(
                title = copy.reviewsOverTime,
                xAxisLabels = dates,
                series = listOf(ProgressSeriesState(copy.reviews, listOf(96, 101, 109, 118, 136, 142))),
                accessibilitySummary = text.value(
                    "Reviews over time, 30-day range. Total reviews 2,842. Final point May 18 with 142 reviews. Trend is generally upward with small dips.",
                    "30日間の復習推移です。合計2,842件、最後は5月18日の142件で、全体として増加傾向です。",
                ),
                selectedRange = AnalyticsRange.THIRTY_DAYS,
                tooltipLabel = text.value("May 18, 142 reviews", "5月18日、142件"),
            ),
            cardTypeBreakdown = ProgressDistributionChartState(
                title = copy.reviewShare,
                segments = listOf(1_079, 767, 512, 484).zip(listOf(38, 27, 18, 17)).mapIndexed { index, (value, percent) ->
                    ProgressDistributionSegmentState(groupLabels[index], value, percent)
                },
                accessibilitySummary = text.value(
                    "Review share by rung group. Total 2,842 reviews. Meaning 38 percent, Reading 27 percent, Writing 18 percent, Discrimination 17 percent.",
                    "段階グループ別の復習シェアです。合計2,842件。意味38%、読み27%、書き取り18%、見分け17%です。",
                ),
            ),
            correctIncorrectBreakdown = ProgressDistributionChartState(
                title = copy.correctVsIncorrect,
                segments = listOf(
                    ProgressDistributionSegmentState(copy.correct, 2_615, 92),
                    ProgressDistributionSegmentState(copy.incorrect, 227, 8),
                ),
                accessibilitySummary = text.value(
                    "Correct vs incorrect. Correct 2,615 reviews, 92 percent. Incorrect 227 reviews, 8 percent. Total 2,842 reviews.",
                    "正解と不正解の内訳です。正解2,615件（92%）、不正解227件（8%）、合計2,842件です。",
                ),
            ),
        ),
        reviewsAnalytics = ProgressReviewsAnalyticsState(
            title = copy.reviewsAnalytics,
            selectedRange = AnalyticsRange.SEVEN_DAYS,
            availableRanges = availableRanges,
            reviewsPerDay = ProgressBarChartState(
                title = copy.reviewsPerDay,
                labels = text.weekdays,
                values = listOf(128, 96, 142, 186, 204, 148, 162),
                accessibilitySummary = reviewSummary,
                selectedRange = AnalyticsRange.SEVEN_DAYS,
            ),
            totalReviews = ProgressCountMetricState(1_066, text.integer(1_066)),
            averagePerDay = ProgressCountMetricState(152, text.integer(152)),
            correct = ProgressCountMetricState(975, text.integer(975)),
            incorrect = ProgressCountMetricState(91, text.integer(91)),
            bestDayLabel = text.friday,
            currentStreak = ProgressStreakMetricState(6, 14, copy.days(6), copy.bestDays(14)),
            tip = copy.keepStreakTip,
            accessibilitySummary = reviewSummary,
            heatmap = ReviewHeatmapPolicy.build(
                (0 until 365).map { offset ->
                    ReviewHeatmapPolicy.DaySummary(
                        LocalDayPolicy.moveLocalDays(LocalDayPolicy.localDayStart(nowMillis), offset - 364),
                        if (offset % 4 == 0) (offset % 18) + 1 else 0,
                    )
                },
                nowMillis,
                TimeZone.getDefault(),
                locale,
            ),
        ),
        accuracyRetention = ProgressAccuracyRetentionState(
            title = copy.accuracyByGroup,
            selectedRange = AnalyticsRange.THIRTY_DAYS,
            availableRanges = availableRanges,
            accuracyTrend = ProgressLineChartState(
                title = copy.accuracyOverTime,
                xAxisLabels = dates,
                series = listOf(
                    ProgressSeriesState(copy.accuracyPercent, listOf(79, 81, 83, 86, 89, 92)),
                    ProgressSeriesState(text.value("7-day avg", "7日平均"), listOf(78, 80, 82, 85, 88, 91), ProgressSeriesStyle.DASHED),
                ),
                accessibilitySummary = text.value(
                    "Accuracy over time, 30-day range. Current accuracy is 92 percent on May 18. Accuracy has generally increased over the past 30 days.",
                    "30日間の正答率推移です。5月18日の正答率は92%で、全体として上昇しています。",
                ),
                selectedRange = AnalyticsRange.THIRTY_DAYS,
                tooltipLabel = text.value("May 18, 92 percent", "5月18日、92%"),
            ),
            retentionByCardType = listOf(93, 90, 85, 78).mapIndexed { index, percent ->
                ProgressRetentionRowState(groupLabels[index], percent, "$percent%")
            },
            retentionSummary = text.value(
                "Accuracy by rung group. Meaning 93 percent, Reading 90 percent, Writing 85 percent, Discrimination 78 percent.",
                "段階グループ別の正答率です。意味93%、読み90%、書き取り85%、見分け78%です。",
            ),
            categoryStatuses = listOf(93, 90, 85, 78).mapIndexed { index, percent ->
                ProgressCategoryStatusState(groupLabels[index], copy.status(percent))
            },
        ),
        progressByLevel = ProgressByLevelState(
            title = copy.ladderDistribution,
            selectedFilterLabel = "",
            overallLearned = ProgressFractionMetricState(126, 126, 100, copy.activeItems(126), copy.activeItemsSummary(126)),
            levelRows = listOf(
                Triple("kanji_meaning", 58, 46),
                Triple("font_meaning", 31, 25),
                Triple("word_reading", 21, 17),
                Triple("write_kanji", 16, 13),
            ).map { (rung, count, percent) -> ProgressLevelRowState(copy.rung(rung), count, 126, percent) },
            cumulativeProgress = ProgressLineChartState(
                title = copy.cumulativePracticed,
                xAxisLabels = dates.dropLast(1),
                series = listOf(ProgressSeriesState(copy.practicedKanji, listOf(25, 48, 72, 103, 135))),
                accessibilitySummary = text.value(
                    "Cumulative distinct kanji practiced rises from 25 to 135 across the displayed range.",
                    "表示期間に、練習した漢字の累計は25字から135字へ増えています。",
                ),
                selectedRange = AnalyticsRange.THIRTY_DAYS,
                tooltipLabel = text.value("May 17, 135 practiced kanji", "5月17日、練習した漢字135字"),
            ),
        ),
        weaknessInsights = ProgressWeaknessInsightsState(
            title = copy.weaknessInsights,
            focusScore = ProgressScoreMetricState(
                72,
                100,
                copy.focusStatus(72),
                text.value("Focus score 72 out of 100. Needs improvement.", "集中スコアは100点中72点。改善が必要です。"),
            ),
            weaknessRows = listOf(
                ProgressWeaknessRowState(groupLabels[0], 81, 42, text.severity(high = true)),
                ProgressWeaknessRowState(groupLabels[1], 84, 31, text.severity(high = true)),
                ProgressWeaknessRowState(copy.rung("type_meaning"), 79, 28, text.severity(high = false)),
                ProgressWeaknessRowState(copy.rung("similar_kanji"), 78, 24, text.severity(high = false)),
            ),
            mostMissedKanji = listOf("亜" to 28, "勉" to 22, "遣" to 18, "複" to 15, "誤" to 12).map {
                ProgressMissedKanjiState(it.first, it.second)
            },
            supportNeeded = listOf(
                ProgressSupportNeedState(groupLabels[0], text.kanji, 42),
                ProgressSupportNeedState(groupLabels[1], text.kanji, 31),
                ProgressSupportNeedState(copy.rung("type_meaning"), "", 28),
                ProgressSupportNeedState(copy.rung("similar_kanji"), "", 24),
            ),
            confusionPairs = listOf(
                ProgressConfusionPairState("徴", "微", "sign", "minute", 5, 2),
                ProgressConfusionPairState("待", "持", "wait", "hold", 3, 1),
            ),
        ),
        forecast = ProgressForecastState(
            totalItems = 126,
            headline = String.format(locale, forecastCopy.headline, 126, text.value("March 2027", "2027年3月")),
            assumption = forecastCopy.assumption,
            burnDown = ProgressLineChartState(
                title = text.value("Items remaining", "残りの項目"),
                xAxisLabels = listOf("Jul" to "7月", "Sep" to "9月", "Nov" to "11月", "Jan" to "1月", "Mar" to "3月")
                    .map { text.value(it.first, it.second) },
                series = listOf(ProgressSeriesState(text.value("Remaining", "残り"), listOf(126, 92, 61, 29, 0))),
                accessibilitySummary = text.value(
                    "Forecast from 126 remaining items in July to zero in March.",
                    "7月の残り126項目から3月の0項目までの予測です。",
                ),
            ),
        ),
    )
}

private class DemoStatsCopy(private val locale: Locale) {
    private val japanese = locale.language == Locale.JAPANESE.language
    val weekdays: List<String> = if (japanese) {
        listOf("月曜", "火曜", "水曜", "木曜", "金曜", "土曜", "日曜")
    } else {
        listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    }
    val friday: String get() = value("Friday", "金曜")
    val kanji: String get() = value("Kanji", "漢字")

    fun value(english: String, japaneseText: String): String = if (japanese) japaneseText else english
    fun integer(value: Int): String = StatsValueFormatter.integer(value, locale)
    fun date(millis: Long): String = StatsValueFormatter.date(
        millis,
        if (japanese) "M月d日" else "MMM d",
        locale,
        DEMO_DATE_ZONE,
    )
    fun severity(high: Boolean): String = if (high) value("High", "高") else value("Medium", "中")
}

private val DEMO_DATE_ZONE: TimeZone = TimeZone.getTimeZone("UTC")
private val DEMO_DATE_MILLIS = listOf(
    1_745_020_800_000L,
    1_745_625_600_000L,
    1_746_230_400_000L,
    1_746_835_200_000L,
    1_747_440_000_000L,
    1_747_526_400_000L,
)
