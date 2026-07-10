package dev.bee.kanjianki.core

import java.util.Calendar
import java.util.Locale

/** Localized copy for the three home-screen widget states. */
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
        return localizedText(
            if (safeCount == 1) "1 review ready" else "$safeCount reviews ready",
            "復習できるカード${safeCount}件",
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

    @JvmStatic
    fun studyNowLabel(): String = localizedText("Study now", "今すぐ学習")

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
    fun widgetDescription(title: String, body: String): String =
        localizedText("Kani widget. $title. $body", "Kaniウィジェット。$title。$body")

    private fun localizedText(english: String, japanese: String): String =
        if (Locale.getDefault().language == JAPANESE_LANGUAGE) japanese else english
}
