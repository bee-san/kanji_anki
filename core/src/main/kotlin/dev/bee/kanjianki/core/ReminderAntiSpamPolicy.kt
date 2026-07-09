package dev.bee.kanjianki.core

/**
 * User-facing anti-spam knobs and their normalization: quiet hours (default
 * 22:00–08:00, aligned with the existing 22:00 review cutoff) and the max study
 * reminders per local day (default 2, range 1–3).
 *
 * Quiet hours are stored as minute-of-day values so the window can wrap past
 * midnight. [quietLeadMinutesFor] converts a start/end window into the
 * "start minute + lead minutes" shape [DailyReminderDecisionPolicy] expects:
 * the lead is the distance from now-relative arming, so the decision policy
 * pulls a late cluster earlier rather than into the quiet window.
 */
object ReminderAntiSpamPolicy {
    const val DEFAULT_QUIET_START_MINUTE: Int = 22 * 60
    const val DEFAULT_QUIET_END_MINUTE: Int = 8 * 60
    const val DEFAULT_MAX_PER_DAY: Int = 2
    const val MIN_MAX_PER_DAY: Int = 1
    const val MAX_MAX_PER_DAY: Int = 3

    private const val MINUTES_PER_DAY = 24 * 60

    @JvmStatic
    fun normalizeMinuteOfDay(minuteOfDay: Int, fallback: Int): Int {
        if (minuteOfDay < 0 || minuteOfDay >= MINUTES_PER_DAY) {
            return fallback
        }
        return minuteOfDay
    }

    @JvmStatic
    fun normalizeMaxPerDay(maxPerDay: Int): Int {
        return maxPerDay.coerceIn(MIN_MAX_PER_DAY, MAX_MAX_PER_DAY)
    }

    /**
     * Minutes from `nowMinuteOfDay` until the quiet window opens, or null when the
     * window is invalid/degenerate. Used as `quietHoursLeadMinutes` so a reminder
     * planned to land inside quiet hours is pulled to the boundary instead.
     */
    @JvmStatic
    fun quietLeadMinutesUntilStart(nowMinuteOfDay: Int, quietStartMinuteOfDay: Int, quietEndMinuteOfDay: Int): Int? {
        if (!quietHoursActive(quietStartMinuteOfDay, quietEndMinuteOfDay)) {
            return null
        }
        val now = nowMinuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
        val delta = (quietStartMinuteOfDay - now + MINUTES_PER_DAY) % MINUTES_PER_DAY
        // A zero delta means the quiet window opens exactly now; treat as one
        // minute of lead so the boundary math still pulls the reminder forward.
        return if (delta == 0) MINUTES_PER_DAY else delta
    }

    /** True when the window is a real, non-empty span (start != end). */
    @JvmStatic
    fun quietHoursActive(quietStartMinuteOfDay: Int, quietEndMinuteOfDay: Int): Boolean {
        return quietStartMinuteOfDay in 0 until MINUTES_PER_DAY &&
            quietEndMinuteOfDay in 0 until MINUTES_PER_DAY &&
            quietStartMinuteOfDay != quietEndMinuteOfDay
    }
}
