package dev.bee.kanjianki.core

import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

/** Localized copy shared by the home-screen widget family. */
object WidgetTextCopy {
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun appName(): String = localizedText("Kani", "カニ")

    @JvmStatic
    fun notSetUpTitle(): String = localizedText("Set up Kani", "Kaniを設定")

    @JvmStatic
    fun notSetUpBody(): String = localizedText(
        "Open Kani to set up your study queue.",
        "Kaniを開いて学習キューを設定します。",
    )

    @JvmStatic
    fun openKaniLabel(): String = localizedText("Open Kani", "Kaniを開く")

    @JvmStatic
    fun dueCountLabel(dueCount: Int): String {
        val safeCount = dueCount.coerceAtLeast(0)
        val count = NumberFormat.getIntegerInstance(Locale.getDefault()).format(safeCount)
        return localizedText(
            if (safeCount == 1) "1 review ready" else "$count reviews ready",
            "復習できるカード${count}件",
        )
    }

    @JvmStatic
    fun streakLabel(streakDays: Int): String {
        val safeDays = streakDays.coerceAtLeast(0)
        return localizedText(
            if (safeDays == 1) "1-day streak" else "$safeDays-day streak",
            "${safeDays}日連続",
        )
    }

    /**
     * Splits the due count into review vs new work ("12 reviews · 3 new").
     * Callers should only use this when both counts are non-zero; otherwise
     * [dueCountLabel] keeps the single number.
     */
    @JvmStatic
    fun dueSplitLabel(reviewCount: Int, newCount: Int): String {
        val safeReviews = reviewCount.coerceAtLeast(0)
        val safeNew = newCount.coerceAtLeast(0)
        return localizedText(
            (if (safeReviews == 1) "1 review" else "$safeReviews reviews") +
                " · $safeNew new",
            "復習${safeReviews}件 · 新規${safeNew}件",
        )
    }

    /** Due-later lookahead line for the expanded tier ("5 more by 18:00"). */
    @JvmStatic
    fun dueLaterLabel(count: Int, byMillis: Long): String {
        if (count <= 0 || byMillis <= 0L) {
            return ""
        }
        val calendar = Calendar.getInstance().apply { timeInMillis = byMillis }
        val time = TimeOfDaySettingsPolicy.displayTime(
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
        )
        return localizedText("$count more by $time", "${time}までにあと${count}件")
    }

    /** Best-streak line for the caught-up expanded tier ("Best: 21 days"). */
    @JvmStatic
    fun bestStreakLabel(bestDays: Int): String {
        if (bestDays <= 0) {
            return ""
        }
        return localizedText(
            if (bestDays == 1) "Best: 1 day" else "Best: $bestDays days",
            "最長${bestDays}日",
        )
    }

    @JvmStatic
    fun studyNowLabel(): String = localizedText("Study now", "今すぐ学習")

    @JvmStatic
    fun studyLabel(): String = localizedText("Study", "学習")

    @JvmStatic
    fun nothingDueTitle(): String = localizedText("All caught up", "復習完了")

    @JvmStatic
    fun nothingDueBody(streakDays: Int, nextUsefulAtMillis: Long): String {
        val streak = streakLabel(streakDays)
        if (nextUsefulAtMillis <= 0L) {
            return localizedText("$streak · Nothing due", "$streak · 復習なし")
        }
        val calendar = Calendar.getInstance().apply { timeInMillis = nextUsefulAtMillis }
        val time = TimeOfDaySettingsPolicy.displayTime(
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
        )
        return localizedText(
            "$streak · More practice at $time",
            "$streak · 次の学習は$time",
        )
    }

    @JvmStatic
    fun errorTitle(): String = localizedText("Kani unavailable", "Kaniを利用できません")

    @JvmStatic
    fun errorBody(): String = localizedText(
        "Open Kani to recover your local study data.",
        "Kaniを開いてローカル学習データを復旧します。",
    )

    @JvmStatic
    fun quickDueStatus(): String = localizedText("Due", "期限")

    @JvmStatic
    fun quickCaughtUpStatus(): String = localizedText("Caught up", "復習完了")

    @JvmStatic
    fun quickSetupStatus(): String = localizedText("Set up", "設定")

    @JvmStatic
    fun quickErrorStatus(): String = localizedText("Unavailable", "利用不可")

    @JvmStatic
    fun visualCountLabel(count: Int): String {
        val safeCount = count.coerceAtLeast(0)
        return if (safeCount > 999) "999+" else safeCount.toString()
    }

    @JvmStatic
    fun reviewCountLabel(count: Int): String {
        val safeCount = count.coerceAtLeast(0)
        val formatted = NumberFormat.getIntegerInstance(Locale.getDefault()).format(safeCount)
        return localizedText(
            if (safeCount == 1) "1 review" else "$formatted reviews",
            "復習${formatted}件",
        )
    }

    @JvmStatic
    fun activityPeriodLabel(count: Int, days: Int): String {
        val safeDays = days.coerceAtLeast(0)
        val reviews = reviewCountLabel(count)
        return localizedText(
            "$reviews in $safeDays days",
            "${safeDays}日間で$reviews",
        )
    }

    @JvmStatic
    fun activityPeriodShortLabel(count: Int, days: Int): String {
        val safeCount = count.coerceAtLeast(0)
        val safeDays = days.coerceAtLeast(0)
        return localizedText(
            "$safeCount · $safeDays days",
            "$safeDays 日 · $safeCount 件",
        )
    }

    @JvmStatic
    fun noActivityTitle(): String = localizedText("No activity yet", "学習履歴はまだありません")

    @JvmStatic
    fun noActivityBody(): String = localizedText(
        "Reviews will appear here.",
        "復習するとここに表示されます。",
    )

    @JvmStatic
    fun openStatsLabel(): String = localizedText("Open stats", "統計を開く")

    @JvmStatic
    fun statsLabel(): String = localizedText("Stats", "統計")

    @JvmStatic
    fun quickStudyDescription(status: String, action: String): String = localizedText(
        "Quick study widget. $status. $action.",
        "クイック学習ウィジェット。$status。$action。",
    )

    @JvmStatic
    fun focusKanjiLabel(): String = localizedText("Focus kanji", "注目の漢字")

    @JvmStatic
    fun focusDetailsLabel(): String = localizedText("Details", "詳細")

    @JvmStatic
    fun focusDueStatus(): String = localizedText("Due now", "今すぐ復習")

    @JvmStatic
    fun focusEmptyTitle(): String = localizedText("No focus kanji", "注目の漢字なし")

    @JvmStatic
    fun focusEmptyBody(): String = localizedText(
        "Open Kani to sync eligible study items.",
        "Kaniを開いて学習対象を同期します。",
    )

    @JvmStatic
    fun focusKanjiDescription(
        kanji: String,
        primaryMeaning: String,
        readings: String,
        isDueNow: Boolean,
        hasStudyAction: Boolean,
    ): String {
        val parts = listOfNotNull(
            localizedText("Focus kanji widget", "注目の漢字ウィジェット"),
            kanji,
            primaryMeaning,
            readings.takeIf(String::isNotBlank)?.let {
                localizedText("Reading: $it", "読み：$it")
            },
            focusDueStatus().takeIf { isDueNow },
            focusDetailsLabel(),
            studyNowLabel().takeIf { hasStudyAction },
        )
        return localizedText(
            parts.joinToString(". ", postfix = "."),
            parts.joinToString("。", postfix = "。"),
        )
    }

    @JvmStatic
    fun activityStateDescription(title: String, body: String, action: String): String = localizedText(
        "Activity widget. $title. $body. $action.",
        "学習履歴ウィジェット。$title。$body。$action。",
    )

    @JvmStatic
    fun activityDescription(
        reviewCount: Int,
        days: Int,
        reviewsToday: Int,
        streakDays: Int,
        bestStreakDays: Int,
        action: String,
    ): String {
        val period = activityPeriodLabel(reviewCount, days)
        val today = localizedText(
            "${reviewsToday.coerceAtLeast(0)} today",
            "今日${reviewsToday.coerceAtLeast(0)}件",
        )
        val heading = localizedText(
            "Activity widget. $period, $today",
            "学習履歴ウィジェット。$period、$today",
        )
        val streak = streakLabel(streakDays)
        val best = bestStreakLabel(bestStreakDays)
        return localizedText(
            listOf(heading, streak, best, action)
                .filter { it.isNotEmpty() }
                .joinToString(". ", postfix = "."),
            listOf(heading, streak, best, action)
                .filter { it.isNotEmpty() }
                .joinToString("。", postfix = "。"),
        )
    }

    @JvmStatic
    fun widgetDescription(title: String, body: String): String =
        localizedText("Kani widget. $title. $body", "Kaniウィジェット。$title。$body")

    @JvmStatic
    fun widgetConfigTitle(): String = localizedText("Widget options", "ウィジェット設定")

    @JvmStatic
    fun widgetStyleSectionTitle(): String = localizedText("Style", "スタイル")

    @JvmStatic
    fun widgetStyleDueCardLabel(): String = localizedText("Due card", "復習カード")

    @JvmStatic
    fun widgetStyleHeatmapLabel(): String = localizedText("Heatmap", "ヒートマップ")

    @JvmStatic
    fun widgetThemeSectionTitle(): String = localizedText("Theme", "テーマ")

    @JvmStatic
    fun widgetThemeFollowAppLabel(): String = localizedText("Follow app", "アプリに合わせる")

    @JvmStatic
    fun widgetSaveLabel(): String = localizedText("Save", "保存")

    private fun localizedText(english: String, japanese: String): String =
        if (Locale.getDefault().language == JAPANESE_LANGUAGE) japanese else english
}
