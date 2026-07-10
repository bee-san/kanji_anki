package dev.bee.kanjianki.core

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object StatsValueFormatter {
    @JvmStatic fun integer(value: Int, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getIntegerInstance(locale).format(value)

    @JvmStatic fun duration(millis: Long, locale: Locale = Locale.getDefault()): String {
        val minutes = millis.coerceAtLeast(0L) / 60_000L
        val hours = minutes / 60L
        val remainder = minutes % 60L
        val japanese = locale.language == Locale.JAPANESE.language
        return when {
            hours > 0 && remainder > 0 -> if (japanese) "${hours}時間${remainder}分" else "${hours}h ${remainder}m"
            hours > 0 -> if (japanese) "${hours}時間" else "${hours}h"
            else -> if (japanese) "${remainder}分" else "${remainder}m"
        }
    }

    @JvmStatic
    @JvmOverloads
    fun decimal(value: Double, fractionDigits: Int, signed: Boolean = false, locale: Locale = Locale.getDefault()): String {
        val formatter = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = fractionDigits.coerceAtLeast(0)
            maximumFractionDigits = fractionDigits.coerceAtLeast(0)
            isGroupingUsed = false
        }
        val formatted = formatter.format(value)
        return if (signed && value >= 0.0) "+$formatted" else formatted
    }

    @JvmStatic
    fun date(millis: Long, pattern: String, locale: Locale = Locale.getDefault(), zone: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat(pattern, locale).apply { timeZone = zone }.format(Date(millis))
}

object StatsEmptyStateCopy {
    data class Copy(val title: String, val body: String)
    @JvmStatic fun charts(locale: Locale = Locale.getDefault()): Copy = if (locale.language == "ja") {
        Copy("まだこれから🦀", "復習すると、ここに学習の傾向が表示されます。")
    } else Copy("Your story starts here 🦀", "Complete a few reviews to reveal your learning trends.")

    @JvmStatic fun confusion(locale: Locale = Locale.getDefault()): Copy = if (locale.language == "ja") {
        Copy("最近の取り違えはありません", "その調子です。直近90日間の選択ミスがここに表示されます。")
    } else Copy("No recent mix-ups", "Nice work. Choice mistakes from the last 90 days will appear here.")

    @JvmStatic fun forecast(locale: Locale = Locale.getDefault()): Copy = if (locale.language == "ja") {
        Copy("予測する項目がありません", "弱点の漢字が追加されると完了予測が表示されます。")
    } else Copy("Nothing to forecast yet", "Your completion forecast appears when weak kanji enter the ladder.")
}

object ForecastTextCopy {
    data class Copy(val headline: String, val beyondHorizon: String, val assumption: String)
    @JvmStatic fun forLocale(locale: Locale = Locale.getDefault()): Copy = if (locale.language == "ja") {
        Copy(
            "このペースなら、弱点の漢字%d字を%sまでに練習完了できる見込みです",
            "予測期間（2年）より先",
            "すべて合格すると仮定した予測です。Ankiでの完了はAnki側の復習にも依存します。",
        )
    } else Copy(
        "On pace to finish practicing all %d weak kanji by %s",
        "beyond the two-year forecast",
        "Assumes passes; finishing in Anki still depends on your Anki reviews.",
    )
}

/** Locale-first dashboard copy; callers format structured values before building UI state. */
class StatsDashboardCopy private constructor(private val locale: Locale) {
    private val japanese: Boolean = locale.language == Locale.JAPANESE.language
    val statsOverview: String get() = text("Stats overview", "統計の概要")
    val overviewSubtitle: String get() = text("Your learning at a glance", "学習状況の概要")
    val reviewsAnalytics: String get() = text("Reviews analytics", "復習分析")
    val accuracyByGroup: String get() = text("Accuracy by rung group", "段階別正答率")
    val ladderDistribution: String get() = text("Ladder rung distribution", "ラダー段階の分布")
    val weaknessInsights: String get() = text("Weakness insights", "弱点の分析")
    val practiceForecast: String get() = text("Practice forecast", "練習完了予測")
    val reviewsToday: String get() = text("Reviews today", "今日の復習")
    val reviewCalendar: String get() = text("Review calendar", "復習カレンダー")
    val recentConfusionPairs: String get() = text("Recent confusion pairs", "最近の取り違え")
    val lastNinetyDays: String get() = text("Last 90 days", "直近90日")
    val bestDay: String get() = text("Best day", "最多の日")
    val mostMissedKanji: String get() = text("Most missed kanji", "ミスが多い漢字")
    val supportNeeded: String get() = text("Support needed", "支援が必要")
    val allReviews: String get() = text("All reviews", "すべての復習")
    val thirtyDayAccuracy: String get() = text("30-day accuracy", "30日間の正答率")
    val studiedToday: String get() = text("Studied today", "今日は学習済み")
    val keepStreakAlive: String get() = text("Keep the streak alive", "連続学習を続けましょう")
    val distinctKanjiPracticed: String get() = text("Distinct kanji practiced", "練習した漢字")
    val answeredTasks: String get() = text("Answered tasks", "回答した課題")
    val lastSevenDays: String get() = text("Last 7 days", "直近7日")
    val thisWeek: String get() = text("This week", "今週")
    val noData: String get() = text("No data", "データなし")
    val correct: String get() = text("Correct", "正解")
    val incorrect: String get() = text("Incorrect", "不正解")
    val reviews: String get() = text("Reviews", "復習")
    val accuracyPercent: String get() = text("Accuracy %", "正答率%")
    val practicedKanji: String get() = text("Practiced kanji", "練習した漢字")
    val reviewsOverTime: String get() = text("Reviews over time", "復習の推移")
    val reviewShare: String get() = text("Review share by rung group", "段階グループ別の復習シェア")
    val correctVsIncorrect: String get() = text("Correct vs incorrect", "正解と不正解")
    val reviewsPerDay: String get() = text("Reviews per day", "1日ごとの復習")
    val accuracyOverTime: String get() = text("Accuracy over time", "正答率の推移")
    val cumulativePracticed: String get() = text("Cumulative distinct kanji practiced", "練習した漢字の累計")
    val itemsRemaining: String get() = text("Items remaining", "残りの項目")
    val remaining: String get() = text("Remaining", "残り")
    val matureSupport: String get() = text("Mature support", "定着した支援")
    val percentWord: String get() = text("percent", "パーセント")
    val keepStreakTip: String get() = text("Keep the streak going with a short review session today.", "今日も短い復習で連続学習を続けましょう。")
    val startMomentumTip: String get() = text("Start a short review session today to build momentum.", "今日短く復習して勢いを作りましょう。")

    fun group(group: TaskTypeAccuracyPolicy.Group): String = when (group) {
        TaskTypeAccuracyPolicy.Group.MEANING -> text("Meaning", "意味")
        TaskTypeAccuracyPolicy.Group.READING -> text("Reading", "読み")
        TaskTypeAccuracyPolicy.Group.WRITING -> text("Writing", "書き取り")
        TaskTypeAccuracyPolicy.Group.DISCRIMINATION -> text("Discrimination", "見分け")
    }

    fun status(percent: Int): String = when {
        percent >= 90 -> text("Excellent", "優秀")
        percent >= 80 -> text("Great", "とても良い")
        percent >= 70 -> text("Good", "良い")
        else -> text("Needs focus", "要集中")
    }

    fun focusStatus(score: Int): String = when {
        score >= 90 -> text("Excellent", "優秀")
        score >= 80 -> text("Good", "良い")
        else -> text("Needs improvement", "改善が必要")
    }

    fun deltaVsPreviousSeven(value: String): String = text("$value vs previous 7d", "前の7日比 $value")
    fun deltaVsPreviousThirty(value: String): String = text("$value vs previous 30d", "前の30日比 $value")
    fun thisWeekDelta(value: String): String = text("+$value this week", "今週 +$value")
    fun todayDelta(value: String): String = text("+$value today", "今日 +$value")
    fun days(value: Int): String = text("$value days", "${value}日")
    fun bestDays(value: Int): String = text("Best $value days", "最高${value}日")
    fun activeItems(value: Int): String = text("${StatsValueFormatter.integer(value, locale)} active items", "学習中 ${StatsValueFormatter.integer(value, locale)}件")
    fun activeItemsSummary(value: Int): String = text(
        "Ladder rung distribution for ${StatsValueFormatter.integer(value, locale)} active items.",
        "ラダー段階の分布。学習中の項目は${StatsValueFormatter.integer(value, locale)}件です。",
    )

    fun reviewSummary(rangeDays: Int, total: String, average: String, correct: String, incorrect: String): String = text(
        "Reviews per day, $rangeDays-day range. $total total reviews, average $average per day. Correct $correct, incorrect $incorrect.",
        "${rangeDays}日間の復習数。合計${total}件、1日平均${average}件、正解${correct}件、不正解${incorrect}件。",
    )

    fun volumeSummary(): String = text("Review volume over the last 30 days.", "直近30日間の復習量です。")
    fun accuracySummary(rangeDays: Int, accuracy: Int, date: String): String = text(
        "Accuracy over time, $rangeDays-day range. Current accuracy is $accuracy percent on $date.",
        "${rangeDays}日間の正答率推移。$date の正答率は${accuracy}%です。",
    )
    fun cumulativeSummary(): String = text(
        "Cumulative distinct kanji practiced from each kanji's first recorded review.",
        "各漢字の最初の復習日から数えた、練習した漢字の累計です。",
    )
    fun reviewsTooltip(date: String, count: Int): String = text(
        "$date, ${StatsValueFormatter.integer(count, locale)} reviews",
        "$date、復習${StatsValueFormatter.integer(count, locale)}件",
    )
    fun practicedTooltip(date: String, count: Int): String = text(
        "$date, ${StatsValueFormatter.integer(count, locale)} kanji",
        "$date、漢字${StatsValueFormatter.integer(count, locale)}字",
    )
    fun forecastSummary(total: Int, remainingCount: Int): String = text(
        "$total weak kanji forecast; $remainingCount remaining by the final displayed month.",
        "弱点の漢字${total}字の予測。表示期間の最終月には残り${remainingCount}字です。",
    )
    fun impactSeverity(bucket: String): String = when (bucket) {
        KanjiImpactAnalyzer.BUCKET_HELPED -> text("Low", "低")
        KanjiImpactAnalyzer.BUCKET_NOT_HELPING -> text("Medium", "中")
        else -> text("High", "高")
    }
    fun misses(value: Int): String = text("$value misses", "ミス${value}回")
    fun rung(wireName: String): String = when (wireName) {
        "write_kanji" -> text("Write kanji", "漢字を書く")
        "type_meaning" -> text("Type meaning", "意味入力")
        "meaning_kanji" -> text("Meaning to kanji", "意味から漢字")
        "reading_kanji" -> text("Reading to kanji", "読みから漢字")
        "similar_kanji" -> text("Similar kanji", "似た漢字")
        "kanji_meaning" -> text("Kanji meaning", "漢字の意味")
        "font_meaning" -> text("Font meaning", "字体の意味")
        "kanji_reading" -> text("Kanji reading", "漢字の読み")
        "word_reading" -> text("Word reading", "単語の読み")
        "sentence_reading" -> text("Sentence reading", "文の読み")
        else -> wireName
    }

    private fun text(english: String, japaneseText: String): String = if (japanese) japaneseText else english

    companion object {
        @JvmStatic fun forLocale(locale: Locale = Locale.getDefault()): StatsDashboardCopy =
            StatsDashboardCopy(locale)
    }
}
