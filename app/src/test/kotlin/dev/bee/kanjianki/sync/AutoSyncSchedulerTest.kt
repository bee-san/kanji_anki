package dev.bee.kanjianki.sync

import dev.bee.kanjianki.data.LocalStoreBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AutoSyncSchedulerTest {
    @Test
    fun retryWorkIsCancelledOnlyWhenDailySyncCannotNeedIt() {
        assertTrue(AutoSyncScheduler.shouldCancelRetry(null, false))
        assertTrue(AutoSyncScheduler.shouldCancelRetry(settings(false, 8, 30), false))
        assertTrue(AutoSyncScheduler.shouldCancelRetry(settings(true, 8, 30), true))
        assertFalse(AutoSyncScheduler.shouldCancelRetry(settings(true, 8, 30), false))
    }

    @Test
    fun nextDailyJobAlternatesAwayFromTheExecutingId() {
        assertEquals(
            AutoSyncScheduler.SECONDARY_JOB_ID,
            AutoSyncScheduler.nextJobId(AutoSyncScheduler.PRIMARY_JOB_ID),
        )
        assertEquals(
            AutoSyncScheduler.PRIMARY_JOB_ID,
            AutoSyncScheduler.nextJobId(AutoSyncScheduler.SECONDARY_JOB_ID),
        )
        assertEquals(AutoSyncScheduler.PRIMARY_JOB_ID, AutoSyncScheduler.nextJobId(null))
    }

    @Test
    fun existingDailyJobIsKeptWhenCurrentOrAlreadyDue() {
        val now = 10_000L

        assertTrue(AutoSyncScheduler.shouldKeepExistingJob(20_000L, 20_000L, now, false))
        assertTrue(AutoSyncScheduler.shouldKeepExistingJob(9_000L, 30_000L, now, false))
        assertFalse(AutoSyncScheduler.shouldKeepExistingJob(0L, 30_000L, now, false))
        assertFalse(AutoSyncScheduler.shouldKeepExistingJob(20_000L, 30_000L, now, false))
        assertFalse(AutoSyncScheduler.shouldKeepExistingJob(9_000L, 30_000L, now, true))
    }

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

        val scheduled = AutoSyncScheduler.scheduleWithState(
            settings(true, 8, 30),
            now,
            false,
            recorder,
            backend,
        )

        assertTrue(scheduled)
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

        val rejected = AutoSyncScheduler.scheduleWithState(
            settings(true, 8, 30),
            nowAt(7, 45),
            false,
            recorder,
            backend,
        )
        assertFalse(rejected)
        assertEquals(0L, recorder.nextRunAt)

        backend.scheduleResult = true
        backend.throwOnSchedule = true
        recorder.nextRunAt = 123L

        val failed = AutoSyncScheduler.scheduleWithState(
            settings(true, 8, 30),
            nowAt(7, 45),
            false,
            recorder,
            backend,
        )

        assertFalse(failed)
        assertEquals(0L, recorder.nextRunAt)
    }

    @Test
    fun disabledScheduleClearsRecordedRunWhenBackendCancellationThrows() {
        val recorder = Recorder().apply { nextRunAt = 123L }
        val backend = Backend().apply { throwOnCancel = true }

        val cancelled = AutoSyncScheduler.scheduleWithState(
            settings(false, 8, 30),
            nowAt(9, 0),
            false,
            recorder,
            backend,
        )

        assertFalse(cancelled)
        assertTrue(backend.cancelled)
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
        var throwOnCancel = false
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
            if (throwOnCancel) {
                throw IllegalStateException("scheduler cancellation unavailable")
            }
        }
    }
}
