package dev.bee.kanjianki.core

import java.text.DateFormat
import java.util.Date
import java.util.Locale

object DateTextPolicy {
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun humanSyncTime(timestampMillis: Long): String {
        return humanSyncTime(timestampMillis, System.currentTimeMillis())
    }

    @JvmStatic
    fun humanSyncTime(timestampMillis: Long, nowMillis: Long): String {
        if (timestampMillis <= 0L) {
            return localizedText("date unknown", "日付不明")
        }
        val date = Date(timestampMillis)
        val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
        if (LocalDayPolicy.sameLocalDay(timestampMillis, nowMillis)) {
            return localizedText("today at ", "今日 ") + timeFormat.format(date)
        }
        val yesterday = LocalDayPolicy.moveLocalDays(LocalDayPolicy.localDayStart(nowMillis), -1)
        if (LocalDayPolicy.sameLocalDay(timestampMillis, yesterday)) {
            return localizedText("yesterday at ", "昨日 ") + timeFormat.format(date)
        }
        return shortDateTime(timestampMillis)
    }

    @JvmStatic
    fun dueText(dueAt: Long, now: Long): String {
        if (dueAt <= now) {
            return localizedText("due now", "今すぐ復習")
        }
        val delta = nonNegativeDifference(dueAt, now)
        val minutes = maxOf(1L, delta / 60_000L)
        if (minutes < 60L) {
            return localizedText("due in $minutes min", "${minutes}分後に復習")
        }
        val hours = maxOf(1L, delta / 3_600_000L)
        if (hours < 24L) {
            return localizedText("due in $hours hr", "${hours}時間後に復習")
        }
        return localizedText("due ", "期限 ") + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(dueAt))
    }

    @JvmStatic
    fun timelineDate(occurredAt: Long): String {
        if (occurredAt <= 0L) {
            return localizedText("Unknown time", "時刻不明")
        }
        return shortDateTime(occurredAt)
    }

    @JvmStatic
    fun sameLocalDay(leftMillis: Long, rightMillis: Long): Boolean {
        return LocalDayPolicy.sameLocalDay(leftMillis, rightMillis)
    }

    @JvmStatic
    fun nextLocalDayStart(nowMillis: Long): Long {
        return LocalDayPolicy.nextLocalDayStart(nowMillis)
    }

    @JvmStatic
    fun shortDateTime(millis: Long): String {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
    }

    @JvmStatic
    fun autoUpdateLastCheckText(lastCheckAtMillis: Long): String {
        return if (lastCheckAtMillis <= 0L) localizedText("not yet", "未確認") else shortDateTime(lastCheckAtMillis)
    }

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
