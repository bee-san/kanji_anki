package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.core.ReminderSchedulePolicy
import dev.bee.kanjianki.hostpresentation.DesktopAutoSyncSchedule
import dev.bee.kanjianki.hostpresentation.DesktopAutomationPlan
import dev.bee.kanjianki.hostpresentation.DesktopReminderSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopAutomationWorkerTest {
    private val reminder = DesktopReminderSchedule.Config(enabled = true, hour = 9, minute = 0)
    private val autoSync = DesktopAutoSyncSchedule.Config(enabled = false, hour = 3, minute = 0)

    @Test
    fun startArmsAWakeAndFiringItEvaluatesRemindersThenReArms() {
        val scheduler = FakeScheduler()
        var reminders = 0
        var lastEval: Long? = null
        var now = beforeTrigger()
        val worker = DesktopAutomationWorker(
            scheduler = scheduler,
            clock = { now },
            state = { DesktopAutomationPlan.State(reminder, autoSync, lastEval, null) },
            onEvaluateReminders = { reminders++; lastEval = now },
            onRunAutoSync = {},
        )

        worker.start()
        assertTrue("a wake is armed", scheduler.hasPending)

        // Advance to just after the trigger and fire the wake.
        now = triggerMoment() + 60_000L
        scheduler.runNext()

        assertEquals(1, reminders)
        // It re-armed for the next day rather than stopping.
        assertTrue("re-armed after firing", scheduler.hasPending)
    }

    @Test
    fun aWakeWithNothingDueRunsNoWorkButStillReArms() {
        val scheduler = FakeScheduler()
        var reminders = 0
        val now = beforeTrigger()
        val worker = DesktopAutomationWorker(
            scheduler = scheduler,
            clock = { now },
            // Already evaluated at this moment: nothing due when the wake fires early.
            state = { DesktopAutomationPlan.State(reminder, autoSync, now, null) },
            onEvaluateReminders = { reminders++ },
            onRunAutoSync = {},
        )

        worker.start()
        scheduler.runNext()

        assertEquals(0, reminders)
        assertTrue(scheduler.hasPending)
    }

    @Test
    fun stopCancelsThePendingWakeAndFurtherFiringsDoNothing() {
        val scheduler = FakeScheduler()
        var reminders = 0
        var now = beforeTrigger()
        val worker = DesktopAutomationWorker(
            scheduler = scheduler,
            clock = { now },
            state = { DesktopAutomationPlan.State(reminder, autoSync, null, null) },
            onEvaluateReminders = { reminders++ },
            onRunAutoSync = {},
        )

        worker.start()
        worker.stop()
        assertTrue("stop cancels the pending wake", !scheduler.hasPending)

        // Even a stale task that somehow fires after stop does nothing.
        now = triggerMoment() + 60_000L
        scheduler.forceRunLastCancelled()
        assertEquals(0, reminders)
    }

    @Test
    fun disabledSchedulesArmNoTimer() {
        val scheduler = FakeScheduler()
        val worker = DesktopAutomationWorker(
            scheduler = scheduler,
            clock = { beforeTrigger() },
            state = {
                DesktopAutomationPlan.State(reminder.copy(enabled = false), autoSync, null, null)
            },
            onEvaluateReminders = {},
            onRunAutoSync = {},
        )

        worker.start()
        assertTrue("nothing armed when all schedules are off", !scheduler.hasPending)
    }

    private fun triggerMoment(): Long = ReminderSchedulePolicy.nextTriggerMillis(9, 0, beforeTrigger())

    // A moment a little before today's 09:00 trigger, timezone-independent enough for
    // the relative assertions here.
    private fun beforeTrigger(): Long =
        ReminderSchedulePolicy.nextTriggerMillis(9, 0, NOW - DAY, allowToday = true) - 60_000L

    /** A scheduler that holds one pending task and runs it on demand. */
    private class FakeScheduler : DesktopAutomationWorker.Scheduler {
        private var pending: (() -> Unit)? = null
        private var lastCancelled: (() -> Unit)? = null

        val hasPending: Boolean get() = pending != null

        override fun schedule(delayMillis: Long, task: () -> Unit): DesktopAutomationWorker.Cancellable {
            pending = task
            return DesktopAutomationWorker.Cancellable {
                if (pending === task) {
                    lastCancelled = task
                    pending = null
                }
            }
        }

        fun runNext() {
            val task = pending ?: return
            pending = null
            task()
        }

        fun forceRunLastCancelled() {
            lastCancelled?.invoke()
        }
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val DAY = 24L * 60 * 60 * 1000
    }
}
