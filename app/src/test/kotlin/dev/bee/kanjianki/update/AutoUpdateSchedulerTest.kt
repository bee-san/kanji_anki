package dev.bee.kanjianki.update

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
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

    private class Backend : AutoUpdateScheduler.SchedulerBackend {
        var enqueued = false
        var enqueuedName: String? = null
        var cancelledName: String? = null
        var policy: ExistingPeriodicWorkPolicy? = null
        var request: PeriodicWorkRequest? = null

        override fun enqueueUniquePeriodicWork(
            uniqueWorkName: String,
            policy: ExistingPeriodicWorkPolicy,
            request: PeriodicWorkRequest,
        ) {
            enqueued = true
            enqueuedName = uniqueWorkName
            this.policy = policy
            this.request = request
        }

        override fun cancelUniqueWork(uniqueWorkName: String) {
            cancelledName = uniqueWorkName
        }
    }
}
