package dev.bee.kanjianki.host

/**
 * The two pieces of background work a foreground resume owes, and when it may skip them.
 *
 * Both come from `MainActivityLifecycle.onResume`, whose five concerns the thin host ports
 * one at a time. These two share a shape — cheap, idempotent, and wrong to run in a test
 * harness — so the decision lives here as a pure function and the doing stays in the host.
 *
 * The re-arm is throttled and the notification cancel is not, deliberately: cancelling a
 * posted notification is a single `NotificationManager` call, while a re-arm reads the
 * reminder settings and recomputes the next alarm, so a user flicking between apps would
 * have it run on every return. [RESUME_REARM_THROTTLE_MILLIS] is the interval
 * `MainActivityLifecycle` used and is kept identical, because shortening it changes how
 * often a device wakes for work the user did not ask for.
 */
internal class AndroidHostResume(
    private val backgroundWorkAllowed: () -> Boolean,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    /**
     * When the last re-arm ran, or null when none has.
     *
     * Null rather than 0, which is the bug this shape avoids: with a `0L` sentinel the
     * first resume compares `now - 0` against the throttle and is *skipped* whenever the
     * clock reads less than three minutes — true for any injected test clock, and true on
     * a device for an uptime-based clock shortly after boot. The first resume after launch
     * is exactly the one that must arm the alarm.
     */
    private var lastRearmAtMillis: Long? = null

    /**
     * What this resume should do.
     *
     * Called on the main thread and does no I/O: the caller performs whatever this
     * returns, so the throttle and the harness gate are assertable without a scheduler,
     * an alarm manager, or an activity.
     */
    fun onResume(): Actions {
        if (!backgroundWorkAllowed()) return Actions(cancelPostedReminder = false, rearmReminder = false)
        val now = nowMillis()
        val last = lastRearmAtMillis
        val rearm = last == null || now - last >= RESUME_REARM_THROTTLE_MILLIS
        if (rearm) {
            lastRearmAtMillis = now
        }
        // The cancel always runs when work is allowed: opening the app *is* the user
        // acknowledging the reminder, so leaving it in the shade after they arrived is the
        // one clearly wrong outcome.
        return Actions(cancelPostedReminder = true, rearmReminder = rearm)
    }

    /** What the host should do for one resume. */
    data class Actions(
        val cancelPostedReminder: Boolean,
        val rearmReminder: Boolean,
    )

    internal companion object {
        /**
         * How long a re-arm waits before the next one, matching the old host exactly.
         *
         * Three minutes: long enough that app-switching does not recompute the alarm every
         * time, short enough that a user who changes a reminder setting and returns sees it
         * take effect in the same sitting.
         */
        const val RESUME_REARM_THROTTLE_MILLIS: Long = 3L * 60L * 1000L
    }
}
