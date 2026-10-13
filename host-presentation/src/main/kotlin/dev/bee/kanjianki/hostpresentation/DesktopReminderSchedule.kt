package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.core.ReminderSchedulePolicy

/**
 * The pure timing core of the desktop reminder scheduler.
 *
 * Goal 201 wants an in-process scheduler rather than an OS alarm, so the "when does the
 * next reminder evaluation fire, and is it due now" decision has to live in code Kani
 * owns — and it must be fake-clock testable, because the cases that matter (suspend/
 * resume, sleep/wake, a clock jump, a long-running process crossing several days) are
 * exactly the ones a wall-clock test cannot reach. So this is a pure function of the
 * clock, the user's configured time, and the last evaluation: it says whether to
 * evaluate now and when to wake next. The actual timer, the `decide → present → post`
 * chain, and persistence of [lastEvaluatedAtMillis] live in the composition root.
 *
 * Firing is idempotent across a jump: [evaluateNow] is true only when the configured
 * trigger for the current period has passed and this period has not been evaluated yet,
 * so a wake that finds three missed days evaluates once, not three times, and a clock
 * that jumps backward does not re-fire a period already handled.
 */
object DesktopReminderSchedule {
    /** Reminders off, or an unusable time, means never wake for one. */
    data class Config(
        val enabled: Boolean,
        val hour: Int,
        val minute: Int,
    ) {
        val isValid: Boolean get() = enabled && hour in 0..23 && minute in 0..59
    }

    data class Tick(
        /** True when the caller should run the reminder evaluation now. */
        val evaluateNow: Boolean,
        /**
         * When to wake next, in epoch millis — the next daily trigger. Null when
         * reminders are disabled or misconfigured, so the caller arms no timer.
         */
        val nextWakeAtMillis: Long?,
    )

    /**
     * Decides, at [nowMillis], whether to evaluate reminders and when to wake next.
     *
     * [lastEvaluatedAtMillis] is the last time the caller ran the evaluation (null if
     * never). Evaluation is due when the most recent trigger instant at/before now is
     * newer than the last evaluation — i.e. a scheduled time has passed unhandled.
     */
    fun tick(config: Config, nowMillis: Long, lastEvaluatedAtMillis: Long?): Tick {
        if (!config.isValid) return Tick(evaluateNow = false, nextWakeAtMillis = null)

        // The most recent trigger at or before now: the next trigger computed from a
        // moment one day earlier, which lands on today's (or the last elapsed) trigger.
        val mostRecentTrigger = ReminderSchedulePolicy.nextTriggerMillis(
            hour = config.hour,
            minute = config.minute,
            nowMillis = nowMillis - MILLIS_PER_DAY,
            allowToday = true,
        )
        val elapsedUnhandled = mostRecentTrigger <= nowMillis &&
            (lastEvaluatedAtMillis == null || lastEvaluatedAtMillis < mostRecentTrigger)

        // The next trigger strictly after now: today's if it is still ahead, otherwise
        // tomorrow's (allowToday=true advances only once the time has passed).
        val nextWake = ReminderSchedulePolicy.nextTriggerMillis(
            hour = config.hour,
            minute = config.minute,
            nowMillis = nowMillis,
        )
        return Tick(evaluateNow = elapsedUnhandled, nextWakeAtMillis = nextWake)
    }

    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
}
