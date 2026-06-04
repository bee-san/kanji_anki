package dev.bee.kanjianki.updatecore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoUpdateRunPolicyTest {
    @Test
    fun enabledAutoUpdateWithoutPendingInstallShouldRun() {
        assertTrue(AutoUpdateRunPolicy.shouldRun(true, false))
    }

    @Test
    fun disabledAutoUpdateShouldNotRun() {
        assertFalse(AutoUpdateRunPolicy.shouldRun(false, false))
    }

    @Test
    fun pendingInstallShouldNotRunAnotherCheck() {
        assertFalse(AutoUpdateRunPolicy.shouldRun(true, true))
    }

    @Test
    fun retryableUpdateResultMapsToRetryOutcome() {
        assertEquals(AutoUpdateRunPolicy.WorkerOutcome.SUCCESS, AutoUpdateRunPolicy.workerOutcome(false))
        assertEquals(AutoUpdateRunPolicy.WorkerOutcome.RETRY, AutoUpdateRunPolicy.workerOutcome(true))
    }
}
