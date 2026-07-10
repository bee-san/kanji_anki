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
}
