package dev.bee.kanjianki.core

import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsPresentationPolicyTest {
    @Test fun formatsValuesAndCopyByLocale() {
        assertEquals("1,234", StatsValueFormatter.integer(1234, Locale.US))
        assertEquals("1h 5m", StatsValueFormatter.duration(3_900_000L, Locale.US))
        assertEquals("1時間5分", StatsValueFormatter.duration(3_900_000L, Locale.JAPAN))
        assertEquals("0m", StatsValueFormatter.duration(-1L, Locale.US))
        assertEquals("Jan 1", StatsValueFormatter.date(0L, "MMM d", Locale.US, TimeZone.getTimeZone("UTC")))
        assertTrue(StatsEmptyStateCopy.charts(Locale.US).title.contains("story"))
        assertTrue(StatsEmptyStateCopy.confusion(Locale.JAPAN).title.contains("取り違え"))
        assertTrue(StatsEmptyStateCopy.forecast(Locale.US).body.contains("forecast"))
        assertTrue(ForecastTextCopy.forLocale(Locale.US).assumption.contains("Anki"))
        assertTrue(ForecastTextCopy.forLocale(Locale.JAPAN).headline.contains("%d"))
        val english = StatsDashboardCopy.forLocale(Locale.US)
        val japanese = StatsDashboardCopy.forLocale(Locale.JAPAN)
        assertEquals("Accuracy by rung group", english.accuracyByGroup)
        assertEquals("段階別正答率", japanese.accuracyByGroup)
        assertEquals("Meaning", english.group(TaskTypeAccuracyPolicy.Group.MEANING))
        assertEquals("意味", japanese.group(TaskTypeAccuracyPolicy.Group.MEANING))
        assertEquals("+4% vs previous 7d", english.deltaVsPreviousSeven("+4%"))
        assertEquals("前の7日比 +4%", japanese.deltaVsPreviousSeven("+4%"))
        assertEquals("Word reading", english.rung("word_reading"))
        assertEquals("単語の読み", japanese.rung("word_reading"))
        assertEquals("Items remaining", english.itemsRemaining)
        assertEquals("残りの項目", japanese.itemsRemaining)
        assertEquals("Jul 10, 3 reviews", english.reviewsTooltip("Jul 10", 3))
        assertEquals("7月 10、復習3件", japanese.reviewsTooltip("7月 10", 3))
        assertEquals("Medium", english.impactSeverity(KanjiImpactAnalyzer.BUCKET_NOT_HELPING))
        assertEquals("中", japanese.impactSeverity(KanjiImpactAnalyzer.BUCKET_NOT_HELPING))
        assertTrue(japanese.forecastSummary(8, 2).contains("残り2字"))
    }

    @Test fun coversDashboardCopyAndFormatterBranches() {
        assertEquals("1h", StatsValueFormatter.duration(3_600_000L, Locale.US))
        assertEquals("1時間", StatsValueFormatter.duration(3_600_000L, Locale.JAPAN))
        assertEquals("1分", StatsValueFormatter.duration(60_000L, Locale.JAPAN))
        assertEquals("+1.25", StatsValueFormatter.decimal(1.25, 2, signed = true, locale = Locale.US))
        assertEquals("-1.25", StatsValueFormatter.decimal(-1.25, 2, signed = true, locale = Locale.US))
        assertEquals("1970", StatsValueFormatter.date(0L, "yyyy", Locale.US, TimeZone.getTimeZone("UTC")))

        assertTrue(StatsEmptyStateCopy.charts(Locale.JAPAN).body.contains("傾向"))
        assertTrue(StatsEmptyStateCopy.confusion(Locale.US).body.contains("90 days"))
        assertTrue(StatsEmptyStateCopy.forecast(Locale.JAPAN).body.contains("予測"))

        val english = StatsDashboardCopy.forLocale(Locale.US)
        val japanese = StatsDashboardCopy.forLocale(Locale.JAPAN)
        val englishLabels = listOf(
            english.statsOverview,
            english.overviewSubtitle,
            english.reviewsAnalytics,
            english.accuracyByGroup,
            english.ladderDistribution,
            english.weaknessInsights,
            english.practiceForecast,
            english.reviewsToday,
            english.reviewCalendar,
            english.recentConfusionPairs,
            english.lastNinetyDays,
            english.bestDay,
            english.mostMissedKanji,
            english.supportNeeded,
            english.allReviews,
            english.thirtyDayAccuracy,
            english.studiedToday,
            english.keepStreakAlive,
            english.distinctKanjiPracticed,
            english.answeredTasks,
            english.lastSevenDays,
            english.thisWeek,
            english.noData,
            english.correct,
            english.incorrect,
            english.reviews,
            english.accuracyPercent,
            english.practicedKanji,
            english.reviewsOverTime,
            english.reviewShare,
            english.correctVsIncorrect,
            english.reviewsPerDay,
            english.accuracyOverTime,
            english.cumulativePracticed,
            english.itemsRemaining,
            english.remaining,
            english.matureSupport,
            english.percentWord,
            english.keepStreakTip,
            english.startMomentumTip,
        )
        val japaneseLabels = listOf(
            japanese.statsOverview,
            japanese.overviewSubtitle,
            japanese.reviewsAnalytics,
            japanese.accuracyByGroup,
            japanese.ladderDistribution,
            japanese.weaknessInsights,
            japanese.practiceForecast,
            japanese.reviewsToday,
            japanese.reviewCalendar,
            japanese.recentConfusionPairs,
            japanese.lastNinetyDays,
            japanese.bestDay,
            japanese.mostMissedKanji,
            japanese.supportNeeded,
            japanese.allReviews,
            japanese.thirtyDayAccuracy,
            japanese.studiedToday,
            japanese.keepStreakAlive,
            japanese.distinctKanjiPracticed,
            japanese.answeredTasks,
            japanese.lastSevenDays,
            japanese.thisWeek,
            japanese.noData,
            japanese.correct,
            japanese.incorrect,
            japanese.reviews,
            japanese.accuracyPercent,
            japanese.practicedKanji,
            japanese.reviewsOverTime,
            japanese.reviewShare,
            japanese.correctVsIncorrect,
            japanese.reviewsPerDay,
            japanese.accuracyOverTime,
            japanese.cumulativePracticed,
            japanese.itemsRemaining,
            japanese.remaining,
            japanese.matureSupport,
            japanese.percentWord,
            japanese.keepStreakTip,
            japanese.startMomentumTip,
        )
        assertEquals(40, englishLabels.size)
        assertEquals(englishLabels.size, japaneseLabels.size)
        assertTrue(englishLabels.all { it.isNotBlank() })
        assertTrue(japaneseLabels.all { it.isNotBlank() })
        assertTrue(englishLabels.zip(japaneseLabels).all { (left, right) -> left != right })

        assertEquals(
            listOf("Meaning", "Reading", "Writing", "Discrimination"),
            listOf(
                TaskTypeAccuracyPolicy.Group.MEANING,
                TaskTypeAccuracyPolicy.Group.READING,
                TaskTypeAccuracyPolicy.Group.WRITING,
                TaskTypeAccuracyPolicy.Group.DISCRIMINATION,
            ).map(english::group),
        )
        assertEquals(listOf("Excellent", "Great", "Good", "Needs focus"), listOf(90, 80, 70, 69).map(english::status))
        assertEquals(listOf("Excellent", "Good", "Needs improvement"), listOf(90, 80, 79).map(english::focusStatus))
        assertEquals("+2% vs previous 30d", english.deltaVsPreviousThirty("+2%"))
        assertEquals("+3 this week", english.thisWeekDelta("3"))
        assertEquals("+4 today", english.todayDelta("4"))
        assertEquals("5 days", english.days(5))
        assertEquals("Best 6 days", english.bestDays(6))
        assertEquals("7 active items", english.activeItems(7))
        assertTrue(english.activeItemsSummary(7).contains("7 active items"))
        assertTrue(english.reviewSummary(30, "8", "0.3", "6", "2").contains("8 total reviews"))
        assertTrue(english.volumeSummary().contains("30 days"))
        assertTrue(english.accuracySummary(30, 75, "Jul 10").contains("75 percent"))
        assertTrue(english.cumulativeSummary().contains("Cumulative"))
        assertEquals("Jul 10, 9 kanji", english.practicedTooltip("Jul 10", 9))
        assertTrue(english.forecastSummary(10, 3).contains("3 remaining"))
        assertEquals(
            listOf("Low", "Medium", "High"),
            listOf(
                KanjiImpactAnalyzer.BUCKET_HELPED,
                KanjiImpactAnalyzer.BUCKET_NOT_HELPING,
                "unknown",
            ).map(english::impactSeverity),
        )
        assertEquals("4 misses", english.misses(4))
        assertEquals(
            listOf(
                "Write kanji",
                "Type meaning",
                "Meaning to kanji",
                "Reading to kanji",
                "Similar kanji",
                "Kanji meaning",
                "Font meaning",
                "Kanji reading",
                "Word reading",
                "Sentence reading",
                "unknown",
            ),
            listOf(
                "write_kanji",
                "type_meaning",
                "meaning_kanji",
                "reading_kanji",
                "similar_kanji",
                "kanji_meaning",
                "font_meaning",
                "kanji_reading",
                "word_reading",
                "sentence_reading",
                "unknown",
            ).map(english::rung),
        )
    }
}
