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
        assertEquals("Accuracy by rung group", snapshot.accuracyRetention.title)
        assertEquals("Ladder rung distribution", snapshot.progressByLevel.title)
        assertEquals("Weakness insights", snapshot.weaknessInsights.title)
        assertEquals("Progress", ProgressAnalyticsCopy.bottomNavLabel("Progress"))
        assertEquals("30 days", ProgressAnalyticsCopy.rangeLabel(AnalyticsRange.THIRTY_DAYS))
    }

    @Test
    fun demoSnapshotLocalizesProgressAnalyticsCopyInJapaneseLocale() = withDefaultLocale(Locale.JAPAN) {
        val snapshot = progressAnalyticsDemoSnapshot(1_747_000_000_000L)

        assertEquals("統計の概要", snapshot.overview.title)
        assertEquals("学習状況の概要", snapshot.overview.subtitle)
        assertEquals("前の7日比 +18%", snapshot.overview.totalReviews.deltaLabel)
        assertEquals("最高14日", snapshot.overview.currentStreak.detailLabel)
        assertEquals("復習の推移", snapshot.overview.reviewsOverTime.title)
        assertEquals(
            listOf("4月19日", "4月26日", "5月3日", "5月10日", "5月17日", "5月18日"),
            snapshot.overview.reviewsOverTime.xAxisLabels,
        )
        assertEquals(
            listOf("意味", "読み", "書き取り", "見分け"),
            snapshot.overview.cardTypeBreakdown.segments.map { it.label },
        )
        assertEquals(listOf("正解", "不正解"), snapshot.overview.correctIncorrectBreakdown.segments.map { it.label })

        assertEquals("復習分析", snapshot.reviewsAnalytics.title)
        assertEquals(listOf("月曜", "火曜", "水曜", "木曜", "金曜", "土曜", "日曜"), snapshot.reviewsAnalytics.reviewsPerDay.labels)
        assertEquals("金曜", snapshot.reviewsAnalytics.bestDayLabel)
        assertEquals("今日も短い復習で連続学習を続けましょう。", snapshot.reviewsAnalytics.tip)

        assertEquals("段階別正答率", snapshot.accuracyRetention.title)
        assertEquals(listOf("正答率%", "7日平均"), snapshot.accuracyRetention.accuracyTrend.series.map { it.label })
        assertEquals(listOf("優秀", "優秀", "とても良い", "良い"), snapshot.accuracyRetention.categoryStatuses.map { it.status })

        assertEquals("ラダー段階の分布", snapshot.progressByLevel.title)
        assertEquals("", snapshot.progressByLevel.selectedFilterLabel)
        assertEquals("ラダー段階の分布。学習中の項目は126件です。", snapshot.progressByLevel.overallLearned.accessibilityLabel)
        assertEquals("練習した漢字の累計", snapshot.progressByLevel.cumulativeProgress.title)

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
