package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.core.AutoSyncSchedulePolicy
import dev.bee.kanjianki.core.ReminderSchedulePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopAutomationPlanTest {
    private val reminder = DesktopReminderSchedule.Config(enabled = true, hour = 9, minute = 0)
    private val autoSync = DesktopAutoSyncSchedule.Config(enabled = true, hour = 3, minute = 0)

    @Test
    fun bothDisabledMeansNoWorkAndNoTimer() {
        val wake = DesktopAutomationPlan.wake(
            DesktopAutomationPlan.State(
                reminder.copy(enabled = false),
                autoSync.copy(enabled = false),
                null,
                null,
            ),
            NOW,
        )
        assertFalse(wake.hasWork)
        assertNull(wake.nextWakeAtMillis)
    }

    @Test
    fun eachDueScheduleFiresIndependentlyAtItsOwnWake() {
        // Just after the reminder trigger, sync already done today: only reminders fire.
        val afterReminder = reminderTrigger(NOW) + 60_000L
        val remindersOnly = DesktopAutomationPlan.wake(
            DesktopAutomationPlan.State(reminder, autoSync, null, afterReminder),
            afterReminder,
        )
        assertTrue(remindersOnly.evaluateReminders)
        assertFalse(remindersOnly.runAutoSync)

        // Just after the sync trigger, reminders already evaluated today: only sync.
        val afterSync = syncTrigger(NOW) + 60_000L
        val syncOnly = DesktopAutomationPlan.wake(
            DesktopAutomationPlan.State(reminder, autoSync, afterSync, null),
            afterSync,
        )
        assertTrue(syncOnly.runAutoSync)
        assertFalse(syncOnly.evaluateReminders)
    }

    @Test
    fun theNextWakeIsTheEarlierOfTheTwoSchedules() {
        val wake = DesktopAutomationPlan.wake(
            DesktopAutomationPlan.State(reminder, autoSync, NOW, NOW),
            NOW,
        )
        val nextReminder = ReminderSchedulePolicy.nextTriggerMillis(9, 0, NOW)
        val nextSync = AutoSyncSchedulePolicy.plan(true, 3, 0, NOW, alreadySyncedToday = false).triggerAtMillis
        assertEquals(minOf(nextReminder, nextSync), wake.nextWakeAtMillis)
    }

    @Test
    fun oneScheduleDisabledLeavesTheOthersWakeAsTheNextWake() {
        val wake = DesktopAutomationPlan.wake(
            DesktopAutomationPlan.State(reminder, autoSync.copy(enabled = false), NOW, NOW),
            NOW,
        )
        assertEquals(ReminderSchedulePolicy.nextTriggerMillis(9, 0, NOW), wake.nextWakeAtMillis)
    }

    private fun reminderTrigger(now: Long): Long =
        ReminderSchedulePolicy.nextTriggerMillis(9, 0, now - DAY, allowToday = true)

    private fun syncTrigger(now: Long): Long =
        AutoSyncSchedulePolicy.nextTriggerMillis(3, 0, now - DAY, false)

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val DAY = 24L * 60 * 60 * 1000
    }
}
