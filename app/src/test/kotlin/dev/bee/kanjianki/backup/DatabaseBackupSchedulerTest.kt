package dev.bee.kanjianki.backup

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import dev.bee.kanjianki.automation.PendingWorkOperation
import dev.bee.kanjianki.automation.WorkManagerGateway
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseBackupSchedulerTest {
    @Test
    fun unsupportedPlatformCancelsStaleWorkWithoutScheduling() {
        val backend = Backend()

        DatabaseBackupScheduler.schedule(29, backend)

        assertEquals("kani_daily_db_backup", backend.cancelledName)
        assertFalse(backend.enqueued)
    }

    @Test
    fun supportedPlatformSchedulesTheExistingDailyPlan() {
        val backend = Backend()

        DatabaseBackupScheduler.schedule(30, backend)

        assertTrue(backend.enqueued)
        assertEquals("kani_daily_db_backup", backend.enqueuedName)
        assertEquals(ExistingPeriodicWorkPolicy.KEEP, backend.policy)
        assertNotNull(backend.request)
        assertEquals(TimeUnit.DAYS.toMillis(1), backend.request?.workSpec?.intervalDuration)
        assertEquals(TimeUnit.HOURS.toMillis(6), backend.request?.workSpec?.flexDuration)
    }

    private class Backend : WorkManagerGateway {
        var enqueued = false
        var enqueuedName: String? = null
        var cancelledName: String? = null
        var policy: ExistingPeriodicWorkPolicy? = null
        var request: PeriodicWorkRequest? = null

        override fun enqueueUniquePeriodicWork(
            uniqueWorkName: String,
            policy: ExistingPeriodicWorkPolicy,
            request: PeriodicWorkRequest,
        ): PendingWorkOperation {
            enqueued = true
            enqueuedName = uniqueWorkName
            this.policy = policy
            this.request = request
            return PendingWorkOperation { }
        }

        override fun cancelUniqueWork(uniqueWorkName: String): PendingWorkOperation {
            cancelledName = uniqueWorkName
            return PendingWorkOperation { }
        }

        override fun enqueueUniqueWork(
            uniqueWorkName: String,
            policy: ExistingWorkPolicy,
            request: OneTimeWorkRequest,
        ): PendingWorkOperation = error("Unexpected one-time work")
    }
}
