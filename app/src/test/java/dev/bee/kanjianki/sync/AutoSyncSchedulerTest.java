package dev.bee.kanjianki.sync;

import dev.bee.kanjianki.data.LocalStore;

import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class AutoSyncSchedulerTest {
    @Test
    public void scheduleWithStateCancelsAndClearsWhenDisabledOrMissing() {
        Recorder recorder = new Recorder();
        Backend backend = new Backend();

        AutoSyncScheduler.scheduleWithState(null, nowAt(9, 0), false, recorder, backend);
        assertTrue(backend.cancelled);
        assertEquals(0L, recorder.nextRunAt);

        backend.cancelled = false;
        recorder.nextRunAt = 123L;
        AutoSyncScheduler.scheduleWithState(settings(false, 8, 30), nowAt(9, 0), false, recorder, backend);

        assertTrue(backend.cancelled);
        assertEquals(0L, recorder.nextRunAt);
        assertFalse(backend.scheduleCalled);
    }

    @Test
    public void scheduleWithStateStoresTriggerWhenBackendAcceptsJob() {
        long now = nowAt(7, 45);
        Recorder recorder = new Recorder();
        Backend backend = new Backend();

        AutoSyncScheduler.scheduleWithState(settings(true, 8, 30), now, false, recorder, backend);

        assertTrue(backend.scheduleCalled);
        assertEquals(nowAt(8, 30), recorder.nextRunAt);
        assertEquals(45L * 60L * 1000L, backend.minimumLatencyMillis);
        assertEquals(backend.minimumLatencyMillis + 6L * 60L * 60L * 1000L, backend.overrideDeadlineMillis);
    }

    @Test
    public void scheduleWithStateUsesTomorrowWhenAlreadySyncedToday() {
        long now = nowAt(7, 45);
        Recorder recorder = new Recorder();
        Backend backend = new Backend();

        AutoSyncScheduler.scheduleWithState(settings(true, 8, 30), now, true, recorder, backend);

        assertEquals(nowAt(8, 30) + 24L * 60L * 60L * 1000L, recorder.nextRunAt);
    }

    @Test
    public void scheduleWithStateClearsWhenBackendRejectsOrThrows() {
        Recorder recorder = new Recorder();
        Backend backend = new Backend();
        backend.scheduleResult = false;

        AutoSyncScheduler.scheduleWithState(settings(true, 8, 30), nowAt(7, 45), false, recorder, backend);
        assertEquals(0L, recorder.nextRunAt);

        backend.scheduleResult = true;
        backend.throwOnSchedule = true;
        recorder.nextRunAt = 123L;

        AutoSyncScheduler.scheduleWithState(settings(true, 8, 30), nowAt(7, 45), false, recorder, backend);

        assertEquals(0L, recorder.nextRunAt);
    }

    @Test
    public void scheduleAtAppliesMinimumLatencyForNearFutureTriggers() {
        Recorder recorder = new Recorder();
        Backend backend = new Backend();
        long now = nowAt(8, 0);
        long trigger = now + 1_000L;

        AutoSyncScheduler.scheduleAt(recorder, backend, trigger, now);

        assertEquals(trigger, recorder.nextRunAt);
        assertEquals(10_000L, backend.minimumLatencyMillis);
        assertEquals(10_000L + 6L * 60L * 60L * 1000L, backend.overrideDeadlineMillis);
    }

    private static LocalStore.AutoSyncSettings settings(boolean enabled, int hour, int minute) {
        return new LocalStore.AutoSyncSettings(true, enabled, hour, minute, 0L, 0L, 0L);
    }

    private static long nowAt(int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2026, Calendar.MAY, 15, hour, minute, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static final class Recorder implements AutoSyncScheduler.ScheduleRecorder {
        long nextRunAt = -1L;

        @Override
        public void markAutoSyncScheduled(long nextRunAt) {
            this.nextRunAt = nextRunAt;
        }
    }

    private static final class Backend implements AutoSyncScheduler.SchedulerBackend {
        boolean scheduleCalled;
        boolean scheduleResult = true;
        boolean throwOnSchedule;
        boolean cancelled;
        long minimumLatencyMillis;
        long overrideDeadlineMillis;

        @Override
        public boolean schedule(long minimumLatencyMillis, long overrideDeadlineMillis) {
            scheduleCalled = true;
            if (throwOnSchedule) {
                throw new IllegalStateException("scheduler unavailable");
            }
            this.minimumLatencyMillis = minimumLatencyMillis;
            this.overrideDeadlineMillis = overrideDeadlineMillis;
            return scheduleResult;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
