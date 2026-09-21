package dev.bee.kanjianki.fsrs

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import dev.bee.kanjianki.automation.PendingWorkOperation
import dev.bee.kanjianki.automation.WorkManagerGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class FsrsFitSchedulerTest {
    @Test
    fun periodicFitSchedulesOnlyWhenOptedInWithChargingAndBatteryConstraints() {
        val enabled = Backend()
        FsrsFitScheduler.schedule(true, enabled)

        assertEquals(FsrsFitScheduler.UNIQUE_PERIODIC_WORK_NAME, enabled.periodicName)
        assertEquals(ExistingPeriodicWorkPolicy.KEEP, enabled.periodicPolicy)
        assertEquals(TimeUnit.DAYS.toMillis(7), enabled.periodicRequest!!.workSpec.intervalDuration)
        assertTrue(enabled.periodicRequest!!.workSpec.constraints.requiresCharging())
        assertTrue(enabled.periodicRequest!!.workSpec.constraints.requiresBatteryNotLow())
        assertTrue(enabled.cancelled.isEmpty())

        val disabled = Backend()
        FsrsFitScheduler.schedule(false, disabled)
        assertEquals(
            listOf(FsrsFitScheduler.UNIQUE_PERIODIC_WORK_NAME, FsrsFitScheduler.UNIQUE_NOW_WORK_NAME),
            disabled.cancelled,
        )
        assertFalse(disabled.periodicEnqueued)

        val immediate = Backend()
        FsrsFitScheduler.fitNow(immediate)
        assertEquals(FsrsFitScheduler.UNIQUE_NOW_WORK_NAME, immediate.oneShotName)
        assertEquals(ExistingWorkPolicy.REPLACE, immediate.oneShotPolicy)
        assertTrue(immediate.oneShotRequest!!.workSpec.expedited)
    }

    @Test
    fun periodicAndFitNowExecutionsShareOneProcessGate() {
        assertTrue(FsrsFitExecutionGate.tryAcquire())
        try {
            assertFalse(FsrsFitExecutionGate.tryAcquire())
        } finally {
            FsrsFitExecutionGate.release()
        }
        assertTrue(FsrsFitExecutionGate.tryAcquire())
        FsrsFitExecutionGate.release()
    }

    private class Backend : WorkManagerGateway {
        var periodicEnqueued = false
        var periodicName: String? = null
        var periodicPolicy: ExistingPeriodicWorkPolicy? = null
        var periodicRequest: PeriodicWorkRequest? = null
        var oneShotName: String? = null
        var oneShotPolicy: ExistingWorkPolicy? = null
        var oneShotRequest: OneTimeWorkRequest? = null
        val cancelled = ArrayList<String>()

        override fun enqueueUniquePeriodicWork(
            uniqueWorkName: String,
            policy: ExistingPeriodicWorkPolicy,
            request: PeriodicWorkRequest,
        ): PendingWorkOperation {
            periodicEnqueued = true
            periodicName = uniqueWorkName
            periodicPolicy = policy
            periodicRequest = request
            return PendingWorkOperation { }
        }

        override fun enqueueUniqueWork(
            uniqueWorkName: String,
            policy: ExistingWorkPolicy,
            request: OneTimeWorkRequest,
        ): PendingWorkOperation {
            oneShotName = uniqueWorkName
            oneShotPolicy = policy
            oneShotRequest = request
            return PendingWorkOperation { }
        }

        override fun cancelUniqueWork(uniqueWorkName: String): PendingWorkOperation {
            cancelled += uniqueWorkName
            return PendingWorkOperation { }
        }
    }
}
