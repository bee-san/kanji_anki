package dev.bee.kanjianki.progress

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressAnalyticsCopyTest {
    @Test
    fun demoSnapshotRemainsEnglishOutsideJapaneseLocale() = withDefaultLocale(Locale.US) {
        val snapshot = progressAnalyticsDemoSnapshot(1_747_000_000_000L)

        assertEquals("Stats overview", snapshot.overview.title)
        assertEquals("Reviews analytics", snapshot.reviewsAnalytics.title)
        assertEquals("Accuracy & retention", snapshot.accuracyRetention.title)
        assertEquals("Progress by level", snapshot.progressByLevel.title)
        assertEquals("Weakness insights", snapshot.weaknessInsights.title)
        assertEquals("Progress", ProgressAnalyticsCopy.bottomNavLabel("Progress"))
        assertEquals("30 days", ProgressAnalyticsCopy.rangeLabel(AnalyticsRange.THIRTY_DAYS))
    }

    @Test
    fun demoSnapshotLocalizesProgressAnalyticsCopyInJapaneseLocale() = withDefaultLocale(Locale.JAPAN) {
        val snapshot = progressAnalyticsDemoSnapshot(1_747_000_000_000L)

        assertEquals("統計の概要", snapshot.overview.title)
        assertEquals("学習状況の概要", snapshot.overview.subtitle)
        assertEquals("+18% 直近7日比", snapshot.overview.totalReviews.deltaLabel)
        assertEquals("最高14日", snapshot.overview.currentStreak.detailLabel)
        assertEquals("復習の推移", snapshot.overview.reviewsOverTime.title)
        assertEquals(
            listOf("4月19日", "4月26日", "5月3日", "5月10日", "5月17日", "5月18日"),
            snapshot.overview.reviewsOverTime.xAxisLabels,
        )
        assertEquals(
            listOf("意味", "読み", "書き取り", "似た漢字"),
            snapshot.overview.cardTypeBreakdown.segments.map { it.label },
        )
        assertEquals(listOf("正解", "不正解"), snapshot.overview.correctIncorrectBreakdown.segments.map { it.label })

        assertEquals("復習分析", snapshot.reviewsAnalytics.title)
        assertEquals(listOf("月曜", "火曜", "水曜", "木曜", "金曜", "土曜", "日曜"), snapshot.reviewsAnalytics.reviewsPerDay.labels)
        assertEquals("金曜", snapshot.reviewsAnalytics.bestDayLabel)
        assertEquals("今日も短い復習で連続学習を続けましょう。", snapshot.reviewsAnalytics.tip)

        assertEquals("正答率と定着", snapshot.accuracyRetention.title)
        assertEquals(listOf("正答率%", "7日平均"), snapshot.accuracyRetention.accuracyTrend.series.map { it.label })
        assertEquals(listOf("優秀", "とても良い", "良い", "要集中"), snapshot.accuracyRetention.categoryStatuses.map { it.status })

        assertEquals("レベル別進捗", snapshot.progressByLevel.title)
        assertEquals("全レベル", snapshot.progressByLevel.selectedFilterLabel)
        assertEquals("レベル別進捗、全レベル。1026字中126字の漢字を学習済み、12%完了。", snapshot.progressByLevel.overallLearned.accessibilityLabel)
        assertEquals("累計進捗", snapshot.progressByLevel.cumulativeProgress.title)

        assertEquals("弱点の分析", snapshot.weaknessInsights.title)
        assertEquals("改善が必要", snapshot.weaknessInsights.focusScore.status)
        assertEquals(listOf("高", "高", "中", "中"), snapshot.weaknessInsights.weaknessRows.map { it.severity })
        assertEquals(listOf("意味", "読み", "意味入力", "似た漢字"), snapshot.weaknessInsights.supportNeeded.map { it.label })
    }

    @Test
    fun composeCopyHelpersLocalizeLabelsAndPreserveSemanticKeys() = withDefaultLocale(Locale.JAPAN) {
        assertEquals("進捗", ProgressAnalyticsCopy.bottomNavLabel("Progress"))
        assertEquals("30日", ProgressAnalyticsCopy.rangeLabel(AnalyticsRange.THIRTY_DAYS))
        assertEquals("復習合計", ProgressAnalyticsCopy.totalReviewsLabel())
        assertEquals("最高14日", ProgressAnalyticsCopy.bestStreakLabel(14))
        assertEquals("/ 100", ProgressAnalyticsCopy.ofTotalLabel(100))
        assertEquals("ミス3回", ProgressAnalyticsCopy.missesLabel(3))
        assertEquals("meaning", ProgressAnalyticsCopy.cardTypeKey("意味"))
        assertEquals("needs_focus", ProgressAnalyticsCopy.statusKey("要集中"))
        assertEquals("high", ProgressAnalyticsCopy.severityKey("高"))
    }

    @Test
    fun rangeSpecificAnalyticsCopyLocalizesJapaneseSummaries() = withDefaultLocale(Locale.JAPAN) {
        val reviewsRange = ProgressReviewsRangeData(
            reviewsPerDay = ProgressBarChartState(
                title = "Reviews per day",
                labels = listOf("Apr 19", "Friday"),
                values = listOf(1, 2),
                accessibilitySummary = "English reviews summary",
                selectedRange = AnalyticsRange.NINETY_DAYS,
            ),
            totalReviews = ProgressCountMetricState(value = 3, valueLabel = "3"),
            averagePerDay = ProgressCountMetricState(value = 1, valueLabel = "1"),
            correct = ProgressCountMetricState(value = 2, valueLabel = "2"),
            incorrect = ProgressCountMetricState(value = 1, valueLabel = "1"),
            bestDayLabel = "May 18",
            accessibilitySummary = "English range summary",
        )
        val accuracyRange = ProgressLineChartState(
            title = "Accuracy over time",
            xAxisLabels = listOf("Apr 19", "May 18"),
            yAxisLabels = listOf("70", "80", "90"),
            series = listOf(ProgressSeriesState(label = "Accuracy %", values = listOf(88, 90))),
            accessibilitySummary = "English accuracy summary",
            selectedRange = AnalyticsRange.SEVEN_DAYS,
            tooltipLabel = "May 18, 90 percent",
        )

        val base = progressAnalyticsDemoSnapshot(1_747_000_000_000L)
        val localized = ProgressAnalyticsCopy.localize(
            base.copy(
                reviewsAnalytics = base.reviewsAnalytics.copy(
                    rangeData = mapOf(AnalyticsRange.NINETY_DAYS to reviewsRange),
                ),
                accuracyRetention = base.accuracyRetention.copy(
                    rangeData = mapOf(AnalyticsRange.SEVEN_DAYS to accuracyRange),
                ),
            ),
        )

        val localizedReviews = localized.reviewsAnalytics.rangeData.getValue(AnalyticsRange.NINETY_DAYS)
        assertEquals("1日ごとの復習", localizedReviews.reviewsPerDay.title)
        assertEquals(listOf("4月19日", "金曜"), localizedReviews.reviewsPerDay.labels)
        assertEquals("90日間の復習数です。合計3件、1日平均1件です。", localizedReviews.reviewsPerDay.accessibilitySummary)
        assertEquals("5月18日", localizedReviews.bestDayLabel)
        assertEquals("90日間の復習数です。合計3件、正解2件、不正解1件です。", localizedReviews.accessibilitySummary)

        val localizedAccuracy = localized.accuracyRetention.rangeData.getValue(AnalyticsRange.SEVEN_DAYS)
        assertEquals("正答率の推移", localizedAccuracy.title)
        assertEquals(listOf("4月19日", "5月18日"), localizedAccuracy.xAxisLabels)
        assertEquals(listOf("正答率%"), localizedAccuracy.series.map { it.label })
        assertEquals("7日間の正答率推移です。現在の正答率は90%です。", localizedAccuracy.accessibilitySummary)
        assertEquals("5月18日, 90 %", localizedAccuracy.tooltipLabel)
    }

    private inline fun withDefaultLocale(locale: Locale, block: () -> Unit) {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            block()
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
