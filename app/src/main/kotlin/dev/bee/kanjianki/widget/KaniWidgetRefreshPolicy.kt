package dev.bee.kanjianki.widget

/**
 * Event-driven freshness policy for the widget at day/time boundaries.
 *
 * The widget's due count can be up to an hour stale on the provider's
 * `updatePeriodMillis` fallback, which is most visible right after a clock or
 * timezone change and around "next useful time" boundaries. Two event-driven
 * mechanisms close that gap without any periodic worker (the refresh contract
 * in AGENTS.md):
 *
 * 1. System broadcasts: `TIME_SET` and `TIMEZONE_CHANGED` are exempt implicit
 *    broadcasts, so [KaniWidgetReceiver] can receive them from the manifest.
 *    `DATE_CHANGED` is declared for completeness but is NOT on the Android 8+
 *    implicit-broadcast exemption list, so natural midnight rollover is
 *    covered by the one-shot alarm below plus the hourly fallback instead.
 * 2. A one-shot inexact alarm: when the snapshot's next useful time falls
 *    within the next hour (inside the fallback window), a single inexact
 *    alarm re-renders the widget at that moment so "More practice at 14:30"
 *    flips to "N reviews ready" on time.
 */
internal object KaniWidgetRefreshPolicy {
    /** Explicit self-broadcast used by the one-shot boundary alarm. */
    const val ACTION_WIDGET_REFRESH = "dev.bee.kanjianki.widget.action.REFRESH"

    private const val ACTION_TIME_CHANGED = "android.intent.action.TIME_SET"
    private const val ACTION_TIMEZONE_CHANGED = "android.intent.action.TIMEZONE_CHANGED"
    private const val ACTION_DATE_CHANGED = "android.intent.action.DATE_CHANGED"

    const val ONE_SHOT_WINDOW_MILLIS: Long = 60L * 60L * 1000L

    fun shouldRefreshOnBroadcast(action: String?): Boolean {
        return action == ACTION_TIME_CHANGED ||
            action == ACTION_TIMEZONE_CHANGED ||
            action == ACTION_DATE_CHANGED ||
            action == ACTION_WIDGET_REFRESH
    }

    /**
     * Returns the wall-clock time for a one-shot refresh, or 0 when no alarm
     * is useful: the next useful time must be strictly in the future and
     * inside the hourly-fallback window. Firing at the boundary makes the
     * fresh snapshot's next useful time "now", which returns 0 here, so the
     * alarm can never chain into a periodic loop.
     */
    fun oneShotRefreshAtMillis(nowMillis: Long, nextUsefulAtMillis: Long): Long {
        if (nextUsefulAtMillis <= nowMillis) {
            return 0L
        }
        if (nextUsefulAtMillis > nowMillis + ONE_SHOT_WINDOW_MILLIS) {
            return 0L
        }
        return nextUsefulAtMillis
    }
}
