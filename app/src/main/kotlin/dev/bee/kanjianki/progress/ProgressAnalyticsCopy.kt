package dev.bee.kanjianki.progress

import java.util.Locale

/** Small locale-aware UI-label helpers. Dashboard state itself is built locale-first. */
object ProgressAnalyticsCopy {
    fun bottomNavLabel(english: String): String = if (!isJapaneseLocale()) english else when (english) {
        "Home" -> "ホーム"
        "Study" -> "学習"
        "Progress" -> "進捗"
        "Profile" -> "プロフィール"
        else -> english
    }

    fun rangeLabel(range: AnalyticsRange): String = when (range) {
        AnalyticsRange.SEVEN_DAYS -> localizedText(range.label, "7日")
        AnalyticsRange.THIRTY_DAYS -> localizedText(range.label, "30日")
        AnalyticsRange.NINETY_DAYS -> localizedText(range.label, "90日")
    }

    fun totalReviewsLabel(): String = localizedText("Total reviews", "復習合計")
    fun accuracyLabel(): String = localizedText("Accuracy", "正答率")
    fun streakLabel(): String = localizedText("Streak", "連続日数")
    fun bestStreakLabel(days: Int): String = localizedText("Best $days days", "最高${days}日")
    fun kanjiLearnedLabel(): String = localizedText("Kanji practiced", "練習した漢字")
    fun focusSessionsLabel(): String = localizedText("Tasks answered", "回答した課題")
    fun studyTimeLabel(): String = localizedText("Study time", "学習時間")
    fun averagePerDayLabel(): String = localizedText("Average / day", "1日平均")
    fun correctLabel(): String = localizedText("Correct", "正解")
    fun incorrectLabel(): String = localizedText("Incorrect", "不正解")
    fun bestDayCardTitle(): String = localizedText("Best day", "最多の日")
    fun activeLadderItemsLabel(): String = localizedText("Active ladder items", "学習中の項目")
    fun mostMissedKanjiTitle(): String = localizedText("Most missed kanji", "ミスが多い漢字")
    fun supportNeededTitle(): String = localizedText("Support needed", "支援が必要")
    fun focusScoreLabel(): String = localizedText("Focus score", "集中スコア")
    fun focusScoreDetail(): String = localizedText(
        "Kanji that need extra practice are highlighted here.",
        "追加練習が必要な漢字をここで確認できます。",
    )
    fun ofTotalLabel(total: Int): String = localizedText("of $total", "/ $total")
    fun missesLabel(count: Int): String = localizedText("$count misses", "ミス${count}回")

    fun cardTypeKey(label: String): String = when (label) {
        "Meaning", "意味" -> "meaning"
        "Reading", "読み" -> "reading"
        "Writing", "書き取り" -> "writing"
        "Similar kanji", "似た漢字" -> "similar"
        "Discrimination", "見分け" -> "discrimination"
        "Type meaning", "意味入力" -> "type_meaning"
        else -> "other"
    }

    fun statusKey(status: String): String = when (status) {
        "Excellent", "優秀" -> "excellent"
        "Great", "とても良い" -> "great"
        "Good", "良い" -> "good"
        "Needs focus", "要集中" -> "needs_focus"
        "Needs improvement", "改善が必要" -> "needs_improvement"
        else -> "other"
    }

    fun severityKey(severity: String): String = when (severity) {
        "High", "高" -> "high"
        "Medium", "中" -> "medium"
        "Low", "低" -> "low"
        else -> "other"
    }

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == Locale.JAPANESE.language
}
