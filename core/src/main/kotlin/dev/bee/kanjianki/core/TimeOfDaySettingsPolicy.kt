package dev.bee.kanjianki.core

import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object TimeOfDaySettingsPolicy {
    const val DEFAULT_REMINDER_HOUR: Int = 19
    const val DEFAULT_REMINDER_MINUTE: Int = 0
    const val DEFAULT_AUTO_SYNC_HOUR: Int = DEFAULT_REMINDER_HOUR
    const val DEFAULT_AUTO_SYNC_MINUTE: Int = DEFAULT_REMINDER_MINUTE

    private const val MIN_HOUR = 0
    private const val MAX_HOUR = 23
    private const val MIN_MINUTE = 0
    private const val MAX_MINUTE = 59

    @JvmStatic
    fun normalizeReminder(enabled: Boolean, hour: Int, minute: Int): ReminderFields {
        return ReminderFields(enabled, normalizeHour(hour), normalizeMinute(minute))
    }

    @JvmStatic
    fun normalizeAutoSync(
        configured: Boolean,
        enabled: Boolean,
        hour: Int,
        minute: Int,
        lastAttemptAtMillis: Long,
        lastSuccessAtMillis: Long,
        nextRunAtMillis: Long,
    ): AutoSyncFields {
        return AutoSyncFields(
            configured,
            configured && enabled,
            normalizeHour(hour),
            normalizeMinute(minute),
            normalizeTimestampMillis(lastAttemptAtMillis),
            normalizeTimestampMillis(lastSuccessAtMillis),
            normalizeTimestampMillis(nextRunAtMillis),
        )
    }

    @JvmStatic
    fun displayTime(hour: Int, minute: Int): String {
        return String.format(Locale.ROOT, "%02d:%02d", hour, minute)
    }

    private fun normalizeHour(hour: Int): Int {
        return max(MIN_HOUR, min(MAX_HOUR, hour))
    }

    private fun normalizeMinute(minute: Int): Int {
        return max(MIN_MINUTE, min(MAX_MINUTE, minute))
    }

    private fun normalizeTimestampMillis(timestampMillis: Long): Long {
        return max(0L, timestampMillis)
    }

    @JvmRecord
    data class ReminderFields(val enabled: Boolean, val hour: Int, val minute: Int) {
        override fun toString(): String {
            return "ReminderFields[enabled=$enabled, hour=$hour, minute=$minute]"
        }
    }

    @JvmRecord
    data class AutoSyncFields(
        val configured: Boolean,
        val enabled: Boolean,
        val hour: Int,
        val minute: Int,
        val lastAttemptAtMillis: Long,
        val lastSuccessAtMillis: Long,
        val nextRunAtMillis: Long,
    ) {
        override fun toString(): String {
            return "AutoSyncFields[configured=$configured, enabled=$enabled, hour=$hour, minute=$minute, " +
                "lastAttemptAtMillis=$lastAttemptAtMillis, lastSuccessAtMillis=$lastSuccessAtMillis, " +
                "nextRunAtMillis=$nextRunAtMillis]"
        }
    }
}
