package dev.bee.kanjianki.sync

import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AutoSyncJobServiceTest {
    @Test
    fun startStopAndDestroyDelegateToLifecycleCollaborators() {
        val markedRunning = AtomicBoolean()
        val ranJob = AtomicBoolean()
        val shutdown = AtomicBoolean()

        assertTrue(
            AutoSyncJobService.startJob(
                { markedRunning.set(true) },
                { job ->
                    ranJob.set(true)
                    job.run()
                },
                Runnable { },
            ),
        )
        assertTrue(markedRunning.get())
        assertTrue(ranJob.get())

        assertTrue(AutoSyncJobService.stopJob())
        AutoSyncJobService.destroyJob { shutdown.set(true) }
        assertTrue(shutdown.get())
    }

    @Test
    fun finishJobSchedulesEnabledSettingsThenAlwaysClosesAndFinishes() {
        val scheduled = AtomicInteger()
        val closed = AtomicBoolean()
        val finished = AtomicBoolean()
        val rescheduled = AtomicBoolean()

        AutoSyncJobService.finishJob(
            null,
            null,
            true,
            { LocalStoreBase.AutoSyncSettings(true, true, 7, 30, 0L, 0L, 0L) },
            { closed.set(true) },
            { _, _ -> scheduled.incrementAndGet() },
            { _, needsReschedule ->
                finished.set(true)
                rescheduled.set(needsReschedule)
            },
        )

        assertTrue(closed.get())
        assertTrue(finished.get())
        assertTrue(rescheduled.get())
        assertEquals(1, scheduled.get())
    }

    @Test
    fun finishJobDoesNotScheduleDisabledSettings() {
        val scheduled = AtomicInteger()
        val closed = AtomicBoolean()
        val finished = AtomicBoolean()

        AutoSyncJobService.finishJob(
            null,
            null,
            false,
            { LocalStoreBase.AutoSyncSettings(true, false, 7, 30, 0L, 0L, 0L) },
            { closed.set(true) },
            { _, _ -> scheduled.incrementAndGet() },
            { _, needsReschedule ->
                finished.set(true)
                assertFalse(needsReschedule)
            },
        )

        assertTrue(closed.get())
        assertTrue(finished.get())
        assertEquals(0, scheduled.get())
    }
}
