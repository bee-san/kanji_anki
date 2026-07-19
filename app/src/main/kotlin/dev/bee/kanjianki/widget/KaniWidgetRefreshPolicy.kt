package dev.bee.kanjianki.widget

import java.time.Instant
import java.time.ZoneId

/** Event-driven freshness policy for widget day and due-time boundaries. */
internal object KaniWidgetRefreshPolicy {
    const val ACTION_WIDGET_REFRESH = "dev.bee.kanjianki.widget.action.REFRESH"

    private const val ACTION_TIME_CHANGED = "android.intent.action.TIME_SET"
    private const val ACTION_TIMEZONE_CHANGED = "android.intent.action.TIMEZONE_CHANGED"
    private const val ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED"
    private const val ACTION_PACKAGE_REPLACED = "android.intent.action.MY_PACKAGE_REPLACED"
    private const val ACTION_LOCALE_CHANGED = "android.intent.action.LOCALE_CHANGED"

    fun shouldRefreshOnBroadcast(action: String?): Boolean {
        return action == ACTION_TIME_CHANGED ||
            action == ACTION_TIMEZONE_CHANGED ||
            action == ACTION_BOOT_COMPLETED ||
            action == ACTION_PACKAGE_REPLACED ||
            action == ACTION_LOCALE_CHANGED ||
            action == ACTION_WIDGET_REFRESH
    }

    fun oneShotRefreshAtMillis(nowMillis: Long, nextUsefulAtMillis: Long): Long {
        if (nextUsefulAtMillis <= nowMillis) return 0L
        return nextUsefulAtMillis
    }

    fun nextLocalMidnightMillis(nowMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        val tomorrow = Instant.ofEpochMilli(nowMillis)
            .atZone(zoneId)
            .toLocalDate()
            .plusDays(1L)
        return tomorrow.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    fun earliestBoundaryAtMillis(
        nowMillis: Long,
        nextUsefulAtMillis: Long,
        nextMidnightAtMillis: Long,
    ): Long {
        val dueAt = oneShotRefreshAtMillis(nowMillis, nextUsefulAtMillis)
        val midnightAt = nextMidnightAtMillis.takeIf { it > nowMillis } ?: 0L
        return listOf(dueAt, midnightAt).filter { it > 0L }.minOrNull() ?: 0L
    }
}
