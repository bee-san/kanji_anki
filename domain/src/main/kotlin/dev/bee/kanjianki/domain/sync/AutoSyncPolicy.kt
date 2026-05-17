package dev.bee.kanjianki.domain.sync

import dev.bee.kanjianki.domain.model.sync.AutoSyncSettings
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.max

class AutoSyncPolicy(
    private val timeZoneProvider: () -> TimeZone = TimeZone::getDefault,
) {
    fun localDayStartMillis(nowMillis: Long): Long =
        calendarAt(nowMillis).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    fun nextTriggerMillis(
        settings: AutoSyncSettings,
        nowMillis: Long,
        alreadySyncedToday: Boolean = false,
    ): Long {
        var calendar = configuredTimeOnDate(nowMillis, settings)
        if (calendar.timeInMillis <= nowMillis || alreadySyncedToday) {
            calendar = calendarAt(nowMillis).apply {
                add(Calendar.DAY_OF_YEAR, 1)
            }
            calendar = configuredTimeOnDate(calendar.timeInMillis, settings)
        }
        return calendar.timeInMillis
    }

    fun minimumLatencyMillis(
        triggerAtMillis: Long,
        nowMillis: Long,
    ): Long = max(MINIMUM_DELAY_MILLIS, triggerAtMillis - nowMillis)

    fun overrideDeadlineMillis(minimumLatencyMillis: Long): Long =
        minimumLatencyMillis + DEADLINE_WINDOW_MILLIS

    private fun calendarAt(nowMillis: Long): Calendar =
        Calendar.getInstance(timeZoneProvider()).apply {
            timeInMillis = nowMillis
        }

    private fun configuredTimeOnDate(
        dateMillis: Long,
        settings: AutoSyncSettings,
    ): Calendar = calendarAt(dateMillis).apply {
        set(Calendar.HOUR_OF_DAY, settings.hour)
        set(Calendar.MINUTE, settings.minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    companion object {
        const val MINIMUM_DELAY_MILLIS = 10_000L
        const val DEADLINE_WINDOW_MILLIS = 6L * 60L * 60L * 1000L
    }
}
