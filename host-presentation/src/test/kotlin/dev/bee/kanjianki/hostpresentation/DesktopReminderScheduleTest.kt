package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.core.ReminderSchedulePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopReminderScheduleTest {
    private val config = DesktopReminderSchedule.Config(enabled = true, hour = 9, minute = 0)

    @Test
    fun disabledOrInvalidConfigNeverWakesOrEvaluates() {
        for (bad in listOf(
            config.copy(enabled = false),
            config.copy(hour = 24),
            config.copy(minute = 60),
        )) {
            val tick = DesktopReminderSchedule.tick(bad, nowMillis = someMoment(), lastEvaluatedAtMillis = null)
            assertFalse(bad.toString(), tick.evaluateNow)
            assertNull(bad.toString(), tick.nextWakeAtMillis)
        }
    }

    @Test
    fun afterTheTriggerWithNoPriorEvaluationItEvaluatesOnce() {
        // A moment just after today's trigger, never evaluated: due.
        val justAfterTrigger = mostRecentTrigger(someMoment()) + 60_000L
        val tick = DesktopReminderSchedule.tick(config, justAfterTrigger, lastEvaluatedAtMillis = null)

        assertTrue(tick.evaluateNow)
        // And the next wake is strictly in the future.
        assertTrue((tick.nextWakeAtMillis ?: 0L) > justAfterTrigger)
    }

    @Test
    fun havingEvaluatedThisPeriodItDoesNotEvaluateAgain() {
        val justAfterTrigger = mostRecentTrigger(someMoment()) + 60_000L
        val trigger = mostRecentTrigger(justAfterTrigger)
        // Last evaluation was at (or after) this period's trigger: not due again.
        val tick = DesktopReminderSchedule.tick(config, justAfterTrigger, lastEvaluatedAtMillis = trigger)

        assertFalse(tick.evaluateNow)
    }

    @Test
    fun aWakeThatMissedSeveralDaysEvaluatesOnceNotPerMissedDay() {
        // Last evaluated four days ago; now is well past today's trigger. Even though
        // several triggers elapsed, evaluateNow is a single boolean — one evaluation.
        val now = mostRecentTrigger(someMoment()) + 60_000L
        val fourDaysAgo = now - 4L * 24 * 60 * 60 * 1000
        val tick = DesktopReminderSchedule.tick(config, now, lastEvaluatedAtMillis = fourDaysAgo)

        assertTrue("missed days still evaluate", tick.evaluateNow)
        // A follow-up tick, having just evaluated, does not re-fire.
        val settled = DesktopReminderSchedule.tick(config, now, lastEvaluatedAtMillis = now)
        assertFalse(settled.evaluateNow)
    }

    @Test
    fun aBackwardClockJumpDoesNotRefireAnAlreadyHandledPeriod() {
        val now = mostRecentTrigger(someMoment()) + 60_000L
        // Evaluated at now; then the clock jumps back a few minutes (still same period).
        val jumpedBack = now - 5L * 60 * 1000
        val tick = DesktopReminderSchedule.tick(config, jumpedBack, lastEvaluatedAtMillis = now)

        assertFalse("a handled period does not re-fire after a backward jump", tick.evaluateNow)
    }

    @Test
    fun beforeTheFirstEverTriggerElapsesTheMostRecentIsYesterdaysUnhandledPeriod() {
        // Before today's trigger, never evaluated: yesterday's trigger is unhandled.
        val beforeTrigger = mostRecentTrigger(someMoment()) - 1L // just before "now" period boundary
        val tick = DesktopReminderSchedule.tick(config, beforeTrigger, lastEvaluatedAtMillis = null)
        // Whether due depends on yesterday's trigger vs null last-eval → due.
        assertTrue(tick.evaluateNow)
    }

    /** The most recent trigger at or before [nowMillis], via the shared policy. */
    private fun mostRecentTrigger(nowMillis: Long): Long =
        ReminderSchedulePolicy.nextTriggerMillis(
            hour = config.hour,
            minute = config.minute,
            nowMillis = nowMillis - 24L * 60 * 60 * 1000,
            allowToday = true,
        )

    // A fixed, timezone-independent reference moment well inside an ordinary day.
    private fun someMoment(): Long = 1_800_000_000_000L
}
