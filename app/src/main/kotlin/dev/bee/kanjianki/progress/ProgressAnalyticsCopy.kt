package dev.bee.kanjianki.progress

import java.util.Locale

object ProgressAnalyticsCopy {
    private const val JAPANESE_LANGUAGE = "ja"

    fun localize(state: ProgressAnalyticsState): ProgressAnalyticsState {
        if (!isJapaneseLocale()) return state
        return state.copy(
            overview = localizeOverview(state.overview),
            reviewsAnalytics = localizeReviews(state.reviewsAnalytics),
            accuracyRetention = localizeAccuracy(state.accuracyRetention),
            progressByLevel = localizeProgressByLevel(state.progressByLevel),
            weaknessInsights = localizeWeakness(state.weaknessInsights),
        )
    }

    fun bottomNavLabel(english: String): String = localizedLabel(english)

    fun rangeLabel(range: AnalyticsRange): String {
        return when (range) {
            AnalyticsRange.SEVEN_DAYS -> localizedText(range.label, "7日")
            AnalyticsRange.THIRTY_DAYS -> localizedText(range.label, "30日")
            AnalyticsRange.NINETY_DAYS -> localizedText(range.label, "90日")
        }
    }

    fun totalReviewsLabel(): String = localizedText("Total reviews", "復習合計")

    fun accuracyLabel(): String = localizedText("Accuracy", "正答率")

    fun streakLabel(): String = localizedText("Streak", "連続日数")

    fun bestStreakLabel(days: Int): String = localizedText("Best $days days", "最高${days}日")

    fun kanjiLearnedLabel(): String = localizedText("Kanji learned", "学習済み漢字")

    fun focusSessionsLabel(): String = localizedText("Focus sessions", "集中セッション")

    fun studyTimeLabel(): String = localizedText("Study time", "学習時間")

    fun averagePerDayLabel(): String = localizedText("Average / day", "1日平均")

    fun correctLabel(): String = localizedText("Correct", "正解")

    fun incorrectLabel(): String = localizedText("Incorrect", "不正解")

    fun bestDayCardTitle(): String = localizedText("Best day", "最多の日")

    fun allLevelsLearnedLabel(): String = localizedText("All levels learned", "全レベルの学習済み")

    fun mostMissedKanjiTitle(): String = localizedText("Most missed kanji", "ミスが多い漢字")

    fun supportNeededTitle(): String = localizedText("Support needed", "支援が必要")

    fun focusScoreLabel(): String = localizedText("Focus score", "集中スコア")

    fun focusScoreDetail(): String = localizedText(
        "Kanji that need extra practice are highlighted here.",
        "追加練習が必要な漢字をここで確認できます。",
    )

    fun ofTotalLabel(total: Int): String = localizedText("of $total", "/ $total")

    fun missesLabel(count: Int): String = localizedText("$count misses", "ミス${count}回")

    fun cardTypeKey(label: String): String {
        return when (label) {
            "Meaning", "意味" -> "meaning"
            "Reading", "読み" -> "reading"
            "Writing", "書き取り" -> "writing"
            "Similar kanji", "似た漢字" -> "similar"
            "Type meaning", "意味入力" -> "type_meaning"
            else -> "other"
        }
    }

    fun statusKey(status: String): String {
        return when (status) {
            "Excellent", "優秀" -> "excellent"
            "Great", "とても良い" -> "great"
            "Good", "良い" -> "good"
            "Needs focus", "要集中" -> "needs_focus"
            "Needs improvement", "改善が必要" -> "needs_improvement"
            else -> "other"
        }
    }

    fun severityKey(severity: String): String {
        return when (severity) {
            "High", "高" -> "high"
            "Medium", "中" -> "medium"
            "Low", "低" -> "low"
            else -> "other"
        }
    }

    private fun localizeOverview(state: ProgressOverviewState): ProgressOverviewState {
        return state.copy(
            title = "統計の概要",
            subtitle = "学習状況の概要",
            totalReviews = state.totalReviews.copy(
                deltaLabel = localizeDeltaLabel(state.totalReviews.deltaLabel),
                detailLabel = localizedLabelOrNull(state.totalReviews.detailLabel),
            ),
            accuracy = state.accuracy.copy(
                deltaLabel = localizeDeltaLabel(state.accuracy.deltaLabel),
                detailLabel = localizedLabelOrNull(state.accuracy.detailLabel),
            ),
            currentStreak = state.currentStreak.copy(
                valueLabel = daysLabel(state.currentStreak.currentDays),
                detailLabel = state.currentStreak.detailLabel?.let(::localizeBestDays),
            ),
            kanjiLearned = state.kanjiLearned.copy(
                deltaLabel = localizeThisWeekDelta(state.kanjiLearned.deltaLabel),
                detailLabel = localizedLabelOrNull(state.kanjiLearned.detailLabel),
            ),
            focusSessions = state.focusSessions.copy(
                deltaLabel = localizedLabelOrNull(state.focusSessions.deltaLabel),
                detailLabel = localizedLabelOrNull(state.focusSessions.detailLabel),
            ),
            studyTime = state.studyTime.copy(
                deltaLabel = localizeTodayDelta(state.studyTime.deltaLabel),
                detailLabel = localizedLabelOrNull(state.studyTime.detailLabel),
            ),
            reviewsOverTime = state.reviewsOverTime.copy(
                title = "復習の推移",
                xAxisLabels = state.reviewsOverTime.xAxisLabels.map(::localizeDateText),
                series = state.reviewsOverTime.series.map { it.copy(label = localizedLabel(it.label)) },
                accessibilitySummary = "30日間の復習推移です。合計${state.totalReviews.valueLabel}件の復習を表示します。",
                tooltipLabel = state.reviewsOverTime.tooltipLabel?.let(::localizeDateText),
            ),
            cardTypeBreakdown = state.cardTypeBreakdown.copy(
                title = "カード種類の内訳",
                segments = state.cardTypeBreakdown.segments.map { it.copy(label = localizedLabel(it.label)) },
                accessibilitySummary = "カード種類ごとの復習内訳です。",
            ),
            correctIncorrectBreakdown = state.correctIncorrectBreakdown.copy(
                title = "正解と不正解",
                segments = state.correctIncorrectBreakdown.segments.map { it.copy(label = localizedLabel(it.label)) },
                accessibilitySummary = "正解と不正解の内訳です。正解${state.correctIncorrectBreakdown.segments.firstOrNull()?.percent ?: 0}%を表示します。",
            ),
        )
    }

    private fun localizeReviews(state: ProgressReviewsAnalyticsState): ProgressReviewsAnalyticsState {
        return state.copy(
            title = "復習分析",
            reviewsPerDay = state.reviewsPerDay.copy(
                title = "1日ごとの復習",
                labels = state.reviewsPerDay.labels.map(::localizedLabel),
                accessibilitySummary = "7日間の復習数です。合計${state.totalReviews.valueLabel}件、1日平均${state.averagePerDay.valueLabel}件です。",
            ),
            bestDayLabel = localizedLabel(state.bestDayLabel),
            currentStreak = state.currentStreak.copy(
                valueLabel = daysLabel(state.currentStreak.currentDays),
                detailLabel = state.currentStreak.detailLabel?.let(::localizeBestDays),
            ),
            tip = if (state.currentStreak.currentDays > 0) {
                "今日も短い復習で連続学習を続けましょう。"
            } else {
                "今日短く復習して勢いを作りましょう。"
            },
            accessibilitySummary = "7日間の復習数です。合計${state.totalReviews.valueLabel}件、正解${state.correct.valueLabel}件、不正解${state.incorrect.valueLabel}件です。",
        )
    }

    private fun localizeAccuracy(state: ProgressAccuracyRetentionState): ProgressAccuracyRetentionState {
        return state.copy(
            title = "正答率と定着",
            accuracyTrend = state.accuracyTrend.copy(
                title = "正答率の推移",
                xAxisLabels = state.accuracyTrend.xAxisLabels.map(::localizeDateText),
                series = state.accuracyTrend.series.map { it.copy(label = localizedLabel(it.label)) },
                accessibilitySummary = "30日間の正答率推移です。現在の正答率は${state.accuracyTrend.series.firstOrNull()?.values?.lastOrNull() ?: 0}%です。",
                tooltipLabel = state.accuracyTrend.tooltipLabel?.let(::localizeDateText),
            ),
            retentionByCardType = state.retentionByCardType.map { it.copy(label = localizedLabel(it.label)) },
            retentionSummary = "カード種類ごとの定着率です。",
            categoryStatuses = state.categoryStatuses.map {
                it.copy(label = localizedLabel(it.label), status = localizedLabel(it.status))
            },
        )
    }

    private fun localizeProgressByLevel(state: ProgressByLevelState): ProgressByLevelState {
        return state.copy(
            title = "レベル別進捗",
            selectedFilterLabel = "全レベル",
            overallLearned = state.overallLearned.copy(
                accessibilityLabel = "レベル別進捗、全レベル。${state.overallLearned.total}字中${state.overallLearned.value}字の漢字を学習済み、${state.overallLearned.percent}%完了。",
            ),
            cumulativeProgress = state.cumulativeProgress.copy(
                title = "累計進捗",
                xAxisLabels = state.cumulativeProgress.xAxisLabels.map(::localizeDateText),
                series = state.cumulativeProgress.series.map { it.copy(label = localizedLabel(it.label)) },
                accessibilitySummary = "全レベルの累計進捗です。表示範囲内で学習済み漢字が増えています。",
                tooltipLabel = state.cumulativeProgress.tooltipLabel?.let(::localizeDateText),
            ),
        )
    }

    private fun localizeWeakness(state: ProgressWeaknessInsightsState): ProgressWeaknessInsightsState {
        return state.copy(
            title = "弱点の分析",
            focusScore = state.focusScore.copy(
                status = localizedLabel(state.focusScore.status),
                accessibilityLabel = "集中スコア${state.focusScore.value}/${state.focusScore.total}。${localizedLabel(state.focusScore.status)}。",
            ),
            weaknessRows = state.weaknessRows.map {
                it.copy(label = localizedLabel(it.label), severity = localizedLabel(it.severity))
            },
            supportNeeded = state.supportNeeded.map {
                it.copy(label = localizedLabel(it.label), targetLabel = localizedLabel(it.targetLabel))
            },
        )
    }

    private fun localizedLabelOrNull(value: String?): String? = value?.let(::localizedLabel)

    private fun localizedLabel(value: String): String {
        if (!isJapaneseLocale()) return value
        return when (value) {
            "Home" -> "ホーム"
            "Study" -> "学習"
            "Progress" -> "進捗"
            "Profile" -> "プロフィール"
            "Reviews" -> "復習"
            "Accuracy %" -> "正答率%"
            "7-day avg" -> "7日平均"
            "Meaning" -> "意味"
            "Reading" -> "読み"
            "Writing" -> "書き取り"
            "Similar kanji" -> "似た漢字"
            "Type meaning" -> "意味入力"
            "Correct" -> "正解"
            "Incorrect" -> "不正解"
            "All levels" -> "全レベル"
            "All reviews" -> "全復習"
            "30-day accuracy" -> "30日間の正答率"
            "Studied today" -> "今日は学習済み"
            "Keep the streak alive" -> "連続学習を続けましょう"
            "Distinct kanji" -> "復習済み漢字"
            "Answered tasks" -> "回答済みタスク"
            "Study sessions" -> "学習セッション"
            "This week" -> "今週"
            "No data" -> "データなし"
            "Excellent" -> "優秀"
            "Great" -> "とても良い"
            "Good" -> "良い"
            "Needs focus" -> "要集中"
            "Needs improvement" -> "改善が必要"
            "High" -> "高"
            "Medium" -> "中"
            "Low" -> "低"
            "Mature support" -> "成熟サポート"
            "Kanji" -> "漢字"
            "Monday" -> "月曜"
            "Tuesday" -> "火曜"
            "Wednesday" -> "水曜"
            "Thursday" -> "木曜"
            "Friday" -> "金曜"
            "Saturday" -> "土曜"
            "Sunday" -> "日曜"
            else -> value
        }
    }

    private fun localizeDeltaLabel(value: String?): String? {
        if (value == null) return null
        return value.replace("vs last 7d", "直近7日比")
    }

    private fun localizeThisWeekDelta(value: String?): String? {
        if (value == null) return null
        return value.replace("this week", "今週")
    }

    private fun localizeTodayDelta(value: String?): String? {
        if (value == null) return null
        return value.replace("today", "今日")
    }

    private fun localizeBestDays(value: String): String {
        val match = Regex("Best (\\d+) days").matchEntire(value)
        return if (match != null) {
            "最高${match.groupValues[1]}日"
        } else {
            localizedLabel(value)
        }
    }

    private fun daysLabel(days: Int): String = "${days}日"

    private fun localizeDateText(value: String): String {
        if (!isJapaneseLocale()) return value
        var out = value
        val months = mapOf(
            "Jan" to "1月",
            "Feb" to "2月",
            "Mar" to "3月",
            "Apr" to "4月",
            "May" to "5月",
            "Jun" to "6月",
            "Jul" to "7月",
            "Aug" to "8月",
            "Sep" to "9月",
            "Oct" to "10月",
            "Nov" to "11月",
            "Dec" to "12月",
        )
        for ((english, japanese) in months) {
            out = out.replace(Regex("\\b$english\\s+(\\d{1,2})"), "${japanese}$1日")
        }
        out = out.replace("reviews", "復習")
            .replace("review", "復習")
            .replace("percent", "%")
            .replace("learned kanji", "学習済み漢字")
        return out
    }

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
