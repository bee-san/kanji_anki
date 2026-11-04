package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.core.AutoSyncSchedulePolicy

/**
 * The pure timing core of the desktop auto-sync scheduler.
 *
 * The auto-sync counterpart to [DesktopReminderSchedule]: Goal 201 wants periodic sync
 * from an in-process scheduler, so "is a sync due now, and when do I wake next" is code
 * Kani owns and must be fake-clock testable — suspend/resume, sleep/wake, a clock jump,
 * a wake that crossed the daily trigger while asleep. It runs over the shared
 * [AutoSyncSchedulePolicy], the same policy the Android worker schedules from, so the
 * two hosts agree on the daily-trigger and once-per-day semantics.
 *
 * "Already synced today" is derived from the last successful sync against the local day
 * start, exactly as the policy expects: a sync that already succeeded today pushes the
 * trigger to tomorrow. [syncNow] is true only when the day's trigger has passed and no
 * successful sync has landed since it — so a wake that missed the trigger syncs once,
 * and a second tick after a success does not re-sync.
 */
object DesktopAutoSyncSchedule {
    data class Config(
        val enabled: Boolean,
        val hour: Int,
        val minute: Int,
    ) {
        val isValid: Boolean get() = enabled && hour in 0..23 && minute in 0..59
    }

    data class Tick(
        /** True when the caller should run a sync now. */
        val syncNow: Boolean,
        /** When to wake next for the daily trigger, or null when auto-sync is off. */
        val nextWakeAtMillis: Long?,
    )

    /**
     * Decides, at [nowMillis], whether to sync and when to wake next.
     *
     * [lastSuccessAtMillis] is the last successful sync (null if never). A sync is due
     * when the most recent trigger at/before now has passed and no successful sync has
     * happened since that trigger.
     */
    fun tick(config: Config, nowMillis: Long, lastSuccessAtMillis: Long?): Tick {
        if (!config.isValid) return Tick(syncNow = false, nextWakeAtMillis = null)

        val alreadySyncedToday = lastSuccessAtMillis != null &&
            lastSuccessAtMillis >= AutoSyncSchedulePolicy.localDayStart(nowMillis)

        // The most recent trigger at or before now, computed from a day earlier.
        val mostRecentTrigger = AutoSyncSchedulePolicy.nextTriggerMillis(
            config.hour,
            config.minute,
            nowMillis - MILLIS_PER_DAY,
            false,
        )
        val elapsedUnsynced = mostRecentTrigger <= nowMillis &&
            (lastSuccessAtMillis == null || lastSuccessAtMillis < mostRecentTrigger)

        val plan = AutoSyncSchedulePolicy.plan(
            enabled = true,
            hour = config.hour,
            minute = config.minute,
            nowMillis = nowMillis,
            alreadySyncedToday = alreadySyncedToday,
        )
        return Tick(syncNow = elapsedUnsynced, nextWakeAtMillis = plan.triggerAtMillis)
    }

    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
}
