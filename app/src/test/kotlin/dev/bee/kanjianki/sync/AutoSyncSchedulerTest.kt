package dev.bee.kanjianki.sync

import dev.bee.kanjianki.data.LocalStoreBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AutoSyncSchedulerTest {
    @Test
    fun scheduleWithStateCancelsAndClearsWhenDisabledOrMissing() {
        val recorder = Recorder()
        val backend = Backend()

        AutoSyncScheduler.scheduleWithState(null, nowAt(9, 0), false, recorder, backend)
        assertTrue(backend.cancelled)
        assertEquals(0L, recorder.nextRunAt)

        backend.cancelled = false
        recorder.nextRunAt = 123L
        AutoSyncScheduler.scheduleWithState(settings(false, 8, 30), nowAt(9, 0), false, recorder, backend)

        assertTrue(backend.cancelled)
        assertEquals(0L, recorder.nextRunAt)
        assertFalse(backend.scheduleCalled)
    }

    @Test
    fun scheduleWithStateStoresTriggerWhenBackendAcceptsJob() {
        val now = nowAt(7, 45)
        val recorder = Recorder()
        val backend = Backend()

        AutoSyncScheduler.scheduleWithState(settings(true, 8, 30), now, false, recorder, backend)

        assertTrue(backend.scheduleCalled)
        assertEquals(nowAt(8, 30), recorder.nextRunAt)
        assertEquals(45L * 60L * 1000L, backend.minimumLatencyMillis)
        assertEquals(backend.minimumLatencyMillis + 6L * 60L * 60L * 1000L, backend.overrideDeadlineMillis)
    }

    @Test
    fun scheduleWithStateUsesTomorrowWhenAlreadySyncedToday() {
        val now = nowAt(7, 45)
        val recorder = Recorder()
        val backend = Backend()

        AutoSyncScheduler.scheduleWithState(settings(true, 8, 30), now, true, recorder, backend)

        assertEquals(nowAt(8, 30) + 24L * 60L * 60L * 1000L, recorder.nextRunAt)
    }

    @Test
    fun scheduleWithStateClearsWhenBackendRejectsOrThrows() {
        val recorder = Recorder()
        val backend = Backend()
        backend.scheduleResult = false

        AutoSyncScheduler.scheduleWithState(settings(true, 8, 30), nowAt(7, 45), false, recorder, backend)
        assertEquals(0L, recorder.nextRunAt)

        backend.scheduleResult = true
        backend.throwOnSchedule = true
        recorder.nextRunAt = 123L

        AutoSyncScheduler.scheduleWithState(settings(true, 8, 30), nowAt(7, 45), false, recorder, backend)

        assertEquals(0L, recorder.nextRunAt)
    }

    @Test
    fun scheduleAtAppliesMinimumLatencyForNearFutureTriggers() {
        val recorder = Recorder()
        val backend = Backend()
        val now = nowAt(8, 0)
        val trigger = now + 1_000L

        AutoSyncScheduler.scheduleAt(recorder, backend, trigger, now)

        assertEquals(trigger, recorder.nextRunAt)
        assertEquals(10_000L, backend.minimumLatencyMillis)
        assertEquals(10_000L + 6L * 60L * 60L * 1000L, backend.overrideDeadlineMillis)
    }

    private fun settings(enabled: Boolean, hour: Int, minute: Int): LocalStoreBase.AutoSyncSettings {
        return LocalStoreBase.AutoSyncSettings(true, enabled, hour, minute, 0L, 0L, 0L)
    }

    private fun nowAt(hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.set(2026, Calendar.MAY, 15, hour, minute, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private class Recorder : AutoSyncScheduler.ScheduleRecorder {
        var nextRunAt = -1L

        override fun markAutoSyncScheduled(nextRunAt: Long) {
            this.nextRunAt = nextRunAt
        }
    }

    private class Backend : AutoSyncScheduler.SchedulerBackend {
        var scheduleCalled = false
        var scheduleResult = true
        var throwOnSchedule = false
        var cancelled = false
        var minimumLatencyMillis = 0L
        var overrideDeadlineMillis = 0L

        override fun schedule(minimumLatencyMillis: Long, overrideDeadlineMillis: Long): Boolean {
            scheduleCalled = true
            if (throwOnSchedule) {
                throw IllegalStateException("scheduler unavailable")
            }
            this.minimumLatencyMillis = minimumLatencyMillis
            this.overrideDeadlineMillis = overrideDeadlineMillis
            return scheduleResult
        }

        override fun cancel() {
            cancelled = true
        }
    }
}
