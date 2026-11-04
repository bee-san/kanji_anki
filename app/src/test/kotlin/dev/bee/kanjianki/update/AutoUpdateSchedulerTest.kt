package dev.bee.kanjianki.update

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import dev.bee.kanjianki.automation.PendingWorkOperation
import dev.bee.kanjianki.automation.WorkManagerGateway
import dev.bee.kanjianki.updatecore.AutoUpdateSchedulePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoUpdateSchedulerTest {
    @Test
    fun disabledAutoUpdatesCancelUniqueWorkWithoutScheduling() {
        val backend = Backend()

        AutoUpdateScheduler.schedule(false, backend)

        assertEquals(AutoUpdateSchedulePolicy.UNIQUE_WORK_NAME, backend.cancelledName)
        assertFalse(backend.enqueued)
    }

    @Test
    fun enabledAutoUpdatesScheduleDailyNetworkConstrainedWork() {
        val backend = Backend()

        AutoUpdateScheduler.schedule(true, backend)

        assertTrue(backend.enqueued)
        assertEquals(AutoUpdateSchedulePolicy.UNIQUE_WORK_NAME, backend.enqueuedName)
        assertEquals(ExistingPeriodicWorkPolicy.KEEP, backend.policy)
        assertNotNull(backend.request)
        assertEquals(NetworkType.CONNECTED, backend.request?.workSpec?.constraints?.requiredNetworkType)
        assertTrue(backend.request?.workSpec?.constraints?.requiresBatteryNotLow() == true)
        assertEquals(AutoUpdateSchedulePolicy.INTERVAL_MILLIS, backend.request?.workSpec?.intervalDuration)
        assertEquals(AutoUpdateSchedulePolicy.FLEX_MILLIS, backend.request?.workSpec?.flexDuration)
    }

    @Test
    fun backendFailuresDoNotEscapeSchedulerEntryPoints() {
        val cancelFailure = Backend().apply { throwOnCancel = true }
        val enqueueFailure = Backend().apply { throwOnEnqueue = true }

        AutoUpdateScheduler.schedule(false, cancelFailure)
        AutoUpdateScheduler.schedule(true, enqueueFailure)

        assertEquals(AutoUpdateSchedulePolicy.UNIQUE_WORK_NAME, cancelFailure.cancelledName)
        assertTrue(enqueueFailure.enqueued)
    }

    private class Backend : WorkManagerGateway {
        var enqueued = false
        var enqueuedName: String? = null
        var cancelledName: String? = null
        var policy: ExistingPeriodicWorkPolicy? = null
        var request: PeriodicWorkRequest? = null
        var throwOnEnqueue = false
        var throwOnCancel = false

        override fun enqueueUniquePeriodicWork(
            uniqueWorkName: String,
            policy: ExistingPeriodicWorkPolicy,
            request: PeriodicWorkRequest,
        ): PendingWorkOperation {
            enqueued = true
            enqueuedName = uniqueWorkName
            this.policy = policy
            this.request = request
            if (throwOnEnqueue) {
                throw IllegalStateException("work manager unavailable")
            }
            return PendingWorkOperation { }
        }

        override fun cancelUniqueWork(uniqueWorkName: String): PendingWorkOperation {
            cancelledName = uniqueWorkName
            if (throwOnCancel) {
                throw IllegalStateException("work manager unavailable")
            }
            return PendingWorkOperation { }
        }

        override fun enqueueUniqueWork(
            uniqueWorkName: String,
            policy: ExistingWorkPolicy,
            request: OneTimeWorkRequest,
        ): PendingWorkOperation = error("Unexpected one-time work")
    }
}
