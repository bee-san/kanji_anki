package dev.bee.kanjianki.sync

import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class AutoSyncRetrySchedulerTest {
    @Test
    fun transientRetryUsesOneDelayedExponentialChain() {
        val backend = Backend()

        AutoSyncRetryScheduler.schedule(backend)

        assertEquals(AutoSyncRetryScheduler.UNIQUE_WORK_NAME, backend.enqueuedName)
        assertEquals(ExistingWorkPolicy.KEEP, backend.policy)
        val spec = requireNotNull(backend.request).workSpec
        assertEquals(
            TimeUnit.MINUTES.toMillis(AutoSyncRetryScheduler.BASE_DELAY_MINUTES),
            spec.initialDelay,
        )
        assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
        assertEquals(
            TimeUnit.MINUTES.toMillis(AutoSyncRetryScheduler.BASE_DELAY_MINUTES),
            spec.backoffDelayDuration,
        )
        assertNull(backend.cancelledName)
        assertFalse(backend.awaited)
    }

    @Test
    fun jobServiceSchedulingWaitsForWorkManagerPersistence() {
        val backend = Backend()

        AutoSyncRetryScheduler.schedule(backend).await()

        assertTrue(backend.awaited)
    }

    @Test
    fun cancelTargetsOnlyTheUniqueRetryChain() {
        val backend = Backend()

        AutoSyncRetryScheduler.cancel(backend)

        assertEquals(AutoSyncRetryScheduler.UNIQUE_WORK_NAME, backend.cancelledName)
        assertNull(backend.request)
        assertFalse(backend.awaited)
    }

    @Test
    fun jobServiceCancellationWaitsForWorkManagerPersistence() {
        val backend = Backend()

        AutoSyncRetryScheduler.cancel(backend).await()

        assertTrue(backend.awaited)
    }

    private class Backend : AutoSyncRetryScheduler.SchedulerBackend {
        var enqueuedName: String? = null
        var cancelledName: String? = null
        var policy: ExistingWorkPolicy? = null
        var request: OneTimeWorkRequest? = null
        var awaited = false

        override fun enqueueUniqueWork(
            uniqueWorkName: String,
            policy: ExistingWorkPolicy,
            request: OneTimeWorkRequest,
        ): AutoSyncRetryScheduler.PendingOperation {
            enqueuedName = uniqueWorkName
            this.policy = policy
            this.request = request
            return AutoSyncRetryScheduler.PendingOperation { awaited = true }
        }

        override fun cancelUniqueWork(
            uniqueWorkName: String,
        ): AutoSyncRetryScheduler.PendingOperation {
            cancelledName = uniqueWorkName
            return AutoSyncRetryScheduler.PendingOperation { awaited = true }
        }
    }
}
