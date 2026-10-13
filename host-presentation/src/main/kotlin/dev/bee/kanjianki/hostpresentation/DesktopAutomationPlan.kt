package dev.bee.kanjianki.hostpresentation

/**
 * The pure per-wake decision of the desktop automation worker: what to do now, and
 * when to wake next.
 *
 * The worker (Goal 201) is one in-process timer serving both reminders and periodic
 * sync rather than two, so the "what fires at this wake, and when is the next wake"
 * choice has to combine both schedules — and it stays pure here so the combination is
 * fake-clock testable, leaving only the executor loop to the composition root. It folds
 * the two timing cores ([DesktopReminderSchedule], [DesktopAutoSyncSchedule]) into one
 * decision: run whichever are due now, and re-arm to the earliest of their next wakes.
 */
object DesktopAutomationPlan {
    data class State(
        val reminder: DesktopReminderSchedule.Config,
        val autoSync: DesktopAutoSyncSchedule.Config,
        val lastReminderEvaluatedAtMillis: Long?,
        val lastSyncSuccessAtMillis: Long?,
    )

    data class Wake(
        val evaluateReminders: Boolean,
        val runAutoSync: Boolean,
        /**
         * The earliest next wake across the enabled schedules, or null when neither is
         * enabled — the worker then arms no timer until settings change.
         */
        val nextWakeAtMillis: Long?,
    ) {
        /** Whether anything at all should happen at this wake. */
        val hasWork: Boolean get() = evaluateReminders || runAutoSync
    }

    /** The decision for a wake at [nowMillis] given [state]. */
    fun wake(state: State, nowMillis: Long): Wake {
        val reminder = DesktopReminderSchedule.tick(state.reminder, nowMillis, state.lastReminderEvaluatedAtMillis)
        val autoSync = DesktopAutoSyncSchedule.tick(state.autoSync, nowMillis, state.lastSyncSuccessAtMillis)
        return Wake(
            evaluateReminders = reminder.evaluateNow,
            runAutoSync = autoSync.syncNow,
            nextWakeAtMillis = earliest(reminder.nextWakeAtMillis, autoSync.nextWakeAtMillis),
        )
    }

    /** The earlier of two optional wake times; null only when both are null. */
    private fun earliest(a: Long?, b: Long?): Long? = when {
        a == null -> b
        b == null -> a
        else -> minOf(a, b)
    }
}
