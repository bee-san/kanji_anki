package dev.bee.kanjianki.sync

import dev.bee.kanjianki.data.LocalStoreBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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

        assertTrue(AutoSyncJobService.stopJob(true))
        assertFalse(AutoSyncJobService.stopJob(false))
        AutoSyncJobService.destroyJob { shutdown.set(true) }
        assertTrue(shutdown.get())
    }

    @Test
    fun jobRunKeepsStopStateScopedToTheMatchingExecution() {
        val first = AutoSyncJobService.JobRun(null)
        val second = AutoSyncJobService.JobRun(null)

        assertTrue(first.matches(null))
        assertTrue(AutoSyncJobService.jobIdsMatch(3801, 3801))
        assertFalse(AutoSyncJobService.jobIdsMatch(3801, 3802))
        assertFalse(AutoSyncJobService.jobIdsMatch(3801, null))
        assertEquals(3801, AutoSyncJobService.jobKey(3801))
        assertEquals(Int.MIN_VALUE, AutoSyncJobService.jobKey(null))
        assertFalse(first.isStopped())
        first.markStopped()

        assertTrue(first.isStopped())
        assertFalse(second.isStopped())
    }

    @Test
    fun stopDuringDurableCompletionReturnsPromptlyAndRequestsPlatformRetry() {
        val run = AutoSyncJobService.JobRun(null)
        val schedulingStarted = CountDownLatch(1)
        val allowSchedulingToFinish = CountDownLatch(1)
        val shouldReschedule = AtomicReference<Boolean?>()
        val completion = Thread {
            run.complete {
                schedulingStarted.countDown()
                assertTrue(allowSchedulingToFinish.await(5L, TimeUnit.SECONDS))
            }
        }
        val stop = Thread {
            shouldReschedule.set(run.markStoppedAndShouldReschedule())
        }

        completion.start()
        assertTrue(schedulingStarted.await(5L, TimeUnit.SECONDS))
        stop.start()
        stop.join(5_000L)
        assertFalse(stop.isAlive)
        assertTrue(shouldReschedule.get() == true)
        allowSchedulingToFinish.countDown()
        completion.join(5_000L)

        assertFalse(completion.isAlive)
        assertTrue(run.isStopped())
    }

    @Test
    fun stopAfterDurableCompletionDoesNotRequestDuplicatePlatformRetry() {
        val run = AutoSyncJobService.JobRun(null)

        assertTrue(run.complete { })

        assertFalse(run.markStoppedAndShouldReschedule())
    }

    @Test
    fun stopBeforeFailedCompletionCallbackStillRequestsPlatformRecovery() {
        val run = AutoSyncJobService.JobRun(null)

        assertTrue(run.complete { run.markCompletionPending(true) })

        assertTrue(run.markStoppedAndShouldReschedule())
    }

    @Test
    fun transientCompletionPersistsDailyAndRetryWorkBeforeFinishing() {
        val events = ArrayList<String>()

        AutoSyncJobService.finishJob(
            null,
            null,
            true,
            AutoSyncJobService.JobRun(null),
            {
                events += "settings"
                LocalStoreBase.AutoSyncSettings(true, true, 7, 30, 0L, 0L, 0L)
            },
            { events += "close" },
            { _, _, _ ->
                events += "daily"
                true
            },
            retryScheduler(events),
            { _, needsReschedule ->
                assertFalse(needsReschedule)
                events += "finish"
            },
        )

        assertEquals(listOf("settings", "daily", "retry", "close", "finish"), events)
    }

    @Test
    fun stoppedCompletionOnlyClosesBecauseJobSchedulerOwnsReschedule() {
        val events = ArrayList<String>()
        val stoppedRun = AutoSyncJobService.JobRun(null).apply { markStopped() }

        AutoSyncJobService.finishJob(
            null,
            null,
            true,
            stoppedRun,
            { throw AssertionError("stopped jobs must not read settings") },
            { events += "close" },
            { _, _, _ ->
                events += "daily"
                true
            },
            retryScheduler(events),
            { _, _ -> events += "finish" },
        )

        assertEquals(listOf("close"), events)
    }

    @Test
    fun terminalOrDisabledCompletionCancelsRetryWork() {
        val terminalEvents = ArrayList<String>()
        AutoSyncJobService.finishJob(
            null,
            null,
            false,
            AutoSyncJobService.JobRun(null),
            { LocalStoreBase.AutoSyncSettings(true, true, 7, 30, 0L, 0L, 0L) },
            { terminalEvents += "close" },
            { _, _, _ ->
                terminalEvents += "daily"
                true
            },
            retryScheduler(terminalEvents),
            { _, _ -> terminalEvents += "finish" },
        )
        assertEquals(listOf("daily", "cancel-retry", "close", "finish"), terminalEvents)

        val disabledEvents = ArrayList<String>()
        AutoSyncJobService.finishJob(
            null,
            null,
            true,
            AutoSyncJobService.JobRun(null),
            { LocalStoreBase.AutoSyncSettings(true, false, 7, 30, 0L, 0L, 0L) },
            { disabledEvents += "close" },
            { _, _, _ ->
                disabledEvents += "daily"
                true
            },
            retryScheduler(disabledEvents),
            { _, _ -> disabledEvents += "finish" },
        )
        assertEquals(listOf("cancel-retry", "close", "finish"), disabledEvents)
    }

    @Test
    fun retryPersistenceFailureClosesAndRequestsPromptPlatformRecovery() {
        val events = ArrayList<String>()
        val retryFailure = IllegalStateException("WorkManager unavailable")

        AutoSyncJobService.finishJob(
            null,
            null,
            true,
            AutoSyncJobService.JobRun(null),
            { LocalStoreBase.AutoSyncSettings(true, true, 7, 30, 0L, 0L, 0L) },
            { events += "close" },
            { _, _, _ ->
                events += "daily"
                true
            },
            object : AutoSyncJobService.RetryScheduler {
                override fun schedule(context: android.content.Context?) {
                    events += "retry"
                    throw retryFailure
                }

                override fun cancel(context: android.content.Context?) {
                    events += "cancel-retry"
                }
            },
            { _, needsReschedule -> events += "finish-$needsReschedule" },
        )

        assertEquals(listOf("daily", "retry", "close", "finish-true"), events)
    }

    @Test
    fun rejectedNextDailyJobRequestsPromptPlatformRecovery() {
        val events = ArrayList<String>()

        AutoSyncJobService.finishJob(
            null,
            null,
            false,
            AutoSyncJobService.JobRun(null),
            { LocalStoreBase.AutoSyncSettings(true, true, 7, 30, 0L, 0L, 0L) },
            { events += "close" },
            { _, _, _ ->
                events += "daily-rejected"
                false
            },
            retryScheduler(events),
            { _, needsReschedule -> events += "finish-$needsReschedule" },
        )

        assertEquals(
            listOf("daily-rejected", "cancel-retry", "close", "finish-true"),
            events,
        )
    }

    private fun retryScheduler(events: MutableList<String>): AutoSyncJobService.RetryScheduler {
        return object : AutoSyncJobService.RetryScheduler {
            override fun schedule(context: android.content.Context?) {
                events += "retry"
            }

            override fun cancel(context: android.content.Context?) {
                events += "cancel-retry"
            }
        }
    }
}
