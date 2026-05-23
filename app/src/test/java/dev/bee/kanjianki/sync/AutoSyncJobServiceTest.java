package dev.bee.kanjianki.sync;

import dev.bee.kanjianki.data.LocalStore;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class AutoSyncJobServiceTest {
    @Test
    public void startStopAndDestroyDelegateToLifecycleCollaborators() {
        AtomicBoolean markedRunning = new AtomicBoolean();
        AtomicBoolean ranJob = new AtomicBoolean();
        AtomicBoolean shutdown = new AtomicBoolean();

        assertTrue(AutoSyncJobService.startJob(
                () -> markedRunning.set(true),
                job -> {
                    ranJob.set(true);
                    job.run();
                },
                () -> {
                }
        ));
        assertTrue(markedRunning.get());
        assertTrue(ranJob.get());

        assertTrue(AutoSyncJobService.stopJob());
        AutoSyncJobService.destroyJob(() -> shutdown.set(true));
        assertTrue(shutdown.get());
    }

    @Test
    public void finishJobSchedulesEnabledSettingsThenAlwaysClosesAndFinishes() {
        AtomicInteger scheduled = new AtomicInteger();
        AtomicBoolean closed = new AtomicBoolean();
        AtomicBoolean finished = new AtomicBoolean();
        AtomicBoolean rescheduled = new AtomicBoolean();

        AutoSyncJobService.finishJob(
                null,
                null,
                true,
                () -> new LocalStore.AutoSyncSettings(true, true, 7, 30, 0L, 0L, 0L),
                () -> closed.set(true),
                (context, settings) -> scheduled.incrementAndGet(),
                (params, needsReschedule) -> {
                    finished.set(true);
                    rescheduled.set(needsReschedule);
                }
        );

        assertTrue(closed.get());
        assertTrue(finished.get());
        assertTrue(rescheduled.get());
        assertEquals(1, scheduled.get());
    }

    @Test
    public void finishJobDoesNotScheduleDisabledSettings() {
        AtomicInteger scheduled = new AtomicInteger();
        AtomicBoolean closed = new AtomicBoolean();
        AtomicBoolean finished = new AtomicBoolean();

        AutoSyncJobService.finishJob(
                null,
                null,
                false,
                () -> new LocalStore.AutoSyncSettings(true, false, 7, 30, 0L, 0L, 0L),
                () -> closed.set(true),
                (context, settings) -> scheduled.incrementAndGet(),
                (params, needsReschedule) -> {
                    finished.set(true);
                    assertFalse(needsReschedule);
                }
        );

        assertTrue(closed.get());
        assertTrue(finished.get());
        assertEquals(0, scheduled.get());
    }
}
