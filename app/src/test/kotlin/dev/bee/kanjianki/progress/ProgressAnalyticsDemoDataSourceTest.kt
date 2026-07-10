package dev.bee.kanjianki.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ProgressAnalyticsDemoDataSourceTest {
    @Test
    fun demoDataSourceExposesTheFiveScreenSnapshot() {
        val nowMillis = 1_747_000_000_000L
        val snapshot = DemoProgressAnalyticsDataSource.snapshot(nowMillis)

        assertEquals(nowMillis, snapshot.generatedAtMillis)
        assertEquals(progressAnalyticsDemoSnapshot(nowMillis), snapshot)

        val overview = snapshot.overview
        assertEquals("Stats overview", overview.title)
        assertEquals("Your learning at a glance", overview.subtitle)
        assertEquals(2_842, overview.totalReviews.value)
        assertEquals("2,842", overview.totalReviews.valueLabel)
        assertEquals("+18% vs previous 7d", overview.totalReviews.deltaLabel)
        assertEquals(92, overview.accuracy.value)
        assertEquals("92%", overview.accuracy.valueLabel)
        assertEquals("+4% vs previous 30d", overview.accuracy.deltaLabel)
        assertEquals(6, overview.currentStreak.currentDays)
        assertEquals(14, overview.currentStreak.bestDays)
        assertEquals(126, overview.kanjiLearned.value)
        assertEquals("+7 this week", overview.kanjiLearned.deltaLabel)
        assertEquals(9, overview.focusSessions.value)
        assertEquals("This week", overview.focusSessions.detailLabel)
        assertEquals(16_320_000L, overview.studyTime.millis)
        assertEquals("4h 32m", overview.studyTime.valueLabel)
        assertEquals("This week", overview.studyTime.detailLabel)

        assertEquals(
            listOf("Apr 19", "Apr 26", "May 3", "May 10", "May 17", "May 18"),
            overview.reviewsOverTime.xAxisLabels,
        )
        assertEquals(listOf(96, 101, 109, 118, 136, 142), overview.reviewsOverTime.series.single().values)
        assertEquals(
            "Reviews over time, 30-day range. Total reviews 2,842. Final point May 18 with 142 reviews. Trend is generally upward with small dips.",
            overview.reviewsOverTime.accessibilitySummary,
        )
        assertEquals(listOf("Meaning", "Reading", "Writing", "Discrimination"), overview.cardTypeBreakdown.segments.map { it.label })
        assertEquals(listOf(1_079, 767, 512, 484), overview.cardTypeBreakdown.segments.map { it.value })
        assertEquals(listOf(38, 27, 18, 17), overview.cardTypeBreakdown.segments.map { it.percent })
        assertEquals(
            "Review share by rung group. Total 2,842 reviews. Meaning 38 percent, Reading 27 percent, Writing 18 percent, Discrimination 17 percent.",
            overview.cardTypeBreakdown.accessibilitySummary,
        )
        assertEquals(listOf("Correct", "Incorrect"), overview.correctIncorrectBreakdown.segments.map { it.label })
        assertEquals(listOf(2_615, 227), overview.correctIncorrectBreakdown.segments.map { it.value })
        assertEquals(listOf(92, 8), overview.correctIncorrectBreakdown.segments.map { it.percent })
        assertEquals(
            "Correct vs incorrect. Correct 2,615 reviews, 92 percent. Incorrect 227 reviews, 8 percent. Total 2,842 reviews.",
            overview.correctIncorrectBreakdown.accessibilitySummary,
        )

        val reviews = snapshot.reviewsAnalytics
        assertEquals("Reviews analytics", reviews.title)
        assertEquals(AnalyticsRange.SEVEN_DAYS, reviews.selectedRange)
        assertEquals(listOf(AnalyticsRange.SEVEN_DAYS, AnalyticsRange.THIRTY_DAYS, AnalyticsRange.NINETY_DAYS), reviews.availableRanges)
        assertEquals(listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"), reviews.reviewsPerDay.labels)
        assertEquals(listOf(128, 96, 142, 186, 204, 148, 162), reviews.reviewsPerDay.values)
        assertEquals(1_066, reviews.totalReviews.value)
        assertEquals("1,066", reviews.totalReviews.valueLabel)
        assertEquals(152, reviews.averagePerDay.value)
        assertEquals("152", reviews.averagePerDay.valueLabel)
        assertEquals(975, reviews.correct.value)
        assertEquals(91, reviews.incorrect.value)
        assertEquals("Friday", reviews.bestDayLabel)
        assertEquals(6, reviews.currentStreak.currentDays)
        assertEquals("Reviews per day, 7-day range. 1,066 total reviews, average 152 per day. Correct 975, incorrect 91. Best day Friday with 204 reviews.", reviews.accessibilitySummary)
        assertEquals("Keep the streak going with a short review session today.", reviews.tip)

        val accuracy = snapshot.accuracyRetention
        assertEquals("Accuracy by rung group", accuracy.title)
        assertEquals(AnalyticsRange.THIRTY_DAYS, accuracy.selectedRange)
        assertEquals(listOf("Accuracy %", "7-day avg"), accuracy.accuracyTrend.series.map { it.label })
        assertEquals(92, accuracy.accuracyTrend.series.first().values.last())
        assertEquals(91, accuracy.accuracyTrend.series.last().values.last())
        assertEquals(
            "Accuracy over time, 30-day range. Current accuracy is 92 percent on May 18. Accuracy has generally increased over the past 30 days.",
            accuracy.accuracyTrend.accessibilitySummary,
        )
        assertEquals(listOf("Meaning", "Reading", "Writing", "Discrimination"), accuracy.retentionByCardType.map { it.label })
        assertEquals(listOf(93, 90, 85, 78), accuracy.retentionByCardType.map { it.percent })
        assertEquals(
            "Accuracy by rung group. Meaning 93 percent, Reading 90 percent, Writing 85 percent, Discrimination 78 percent.",
            accuracy.retentionSummary,
        )
        assertEquals(listOf("Excellent", "Excellent", "Great", "Good"), accuracy.categoryStatuses.map { it.status })

        val progress = snapshot.progressByLevel
        assertEquals("Ladder rung distribution", progress.title)
        assertEquals("", progress.selectedFilterLabel)
        assertEquals(126, progress.overallLearned.value)
        assertEquals(126, progress.overallLearned.total)
        assertEquals(100, progress.overallLearned.percent)
        assertEquals(
            "Ladder rung distribution for 126 active items.",
            progress.overallLearned.accessibilityLabel,
        )
        assertEquals(listOf("Kanji meaning", "Font meaning", "Word reading", "Write kanji"), progress.levelRows.map { it.level })
        assertEquals(listOf(58, 31, 21, 16), progress.levelRows.map { it.learned })
        assertEquals(listOf(126, 126, 126, 126), progress.levelRows.map { it.total })
        assertEquals(listOf(46, 25, 17, 13), progress.levelRows.map { it.percent })
        assertEquals(listOf("Apr 19", "Apr 26", "May 3", "May 10", "May 17"), progress.cumulativeProgress.xAxisLabels)
        assertEquals(listOf(25, 48, 72, 103, 135), progress.cumulativeProgress.series.single().values)
        assertEquals(
            "Cumulative distinct kanji practiced rises from 25 to 135 across the displayed range.",
            progress.cumulativeProgress.accessibilitySummary,
        )

        val weakness = snapshot.weaknessInsights
        assertEquals("Weakness insights", weakness.title)
        assertEquals(72, weakness.focusScore.value)
        assertEquals(100, weakness.focusScore.total)
        assertEquals("Needs improvement", weakness.focusScore.status)
        assertEquals(
            "Focus score 72 out of 100. Needs improvement.",
            weakness.focusScore.accessibilityLabel,
        )
        assertEquals(listOf("Meaning", "Reading", "Type meaning", "Similar kanji"), weakness.weaknessRows.map { it.label })
        assertEquals(listOf(81, 84, 79, 78), weakness.weaknessRows.map { it.accuracyPercent })
        assertEquals(listOf(42, 31, 28, 24), weakness.weaknessRows.map { it.missedCount })
        assertEquals(listOf("High", "High", "Medium", "Medium"), weakness.weaknessRows.map { it.severity })
        assertEquals(listOf("亜", "勉", "遣", "複", "誤"), weakness.mostMissedKanji.map { it.kanji })
        assertEquals(listOf(28, 22, 18, 15, 12), weakness.mostMissedKanji.map { it.misses })
        assertEquals(listOf("Meaning", "Reading", "Type meaning", "Similar kanji"), weakness.supportNeeded.map { it.label })
        assertEquals(listOf("Kanji", "Kanji", "", ""), weakness.supportNeeded.map { it.targetLabel })
        assertEquals(listOf(42, 31, 28, 24), weakness.supportNeeded.map { it.count })
    }

    @Test
    fun chartAccessibilitySummariesArePresentForEveryScreen() {
        val snapshot = progressAnalyticsDemoSnapshot(1_747_000_000_000L)

        assertNotNull(snapshot.overview.reviewsOverTime.accessibilitySummary)
        assertNotNull(snapshot.overview.cardTypeBreakdown.accessibilitySummary)
        assertNotNull(snapshot.overview.correctIncorrectBreakdown.accessibilitySummary)
        assertNotNull(snapshot.reviewsAnalytics.accessibilitySummary)
        assertNotNull(snapshot.accuracyRetention.accuracyTrend.accessibilitySummary)
        assertNotNull(snapshot.accuracyRetention.retentionSummary)
        assertNotNull(snapshot.progressByLevel.overallLearned.accessibilityLabel)
        assertNotNull(snapshot.progressByLevel.cumulativeProgress.accessibilitySummary)
        assertNotNull(snapshot.weaknessInsights.focusScore.accessibilityLabel)
    }
}
