package dev.bee.kanjianki.updatecore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoUpdateRunPolicyTest {
    @Test
    fun enabledAutoUpdateWithoutPendingInstallShouldRun() {
        assertTrue(AutoUpdateRunPolicy.shouldRun(enabled = true, hasPendingUpdate = false))
    }

    @Test
    fun disabledAutoUpdateShouldNotRun() {
        assertFalse(AutoUpdateRunPolicy.shouldRun(enabled = false, hasPendingUpdate = false))
    }

    @Test
    fun pendingInstallShouldNotRunAnotherCheck() {
        assertFalse(AutoUpdateRunPolicy.shouldRun(enabled = true, hasPendingUpdate = true))
    }

    @Test
    fun retryableUpdateResultMapsToRetryOutcome() {
        assertEquals(AutoUpdateRunPolicy.WorkerOutcome.SUCCESS, AutoUpdateRunPolicy.workerOutcome(retryable = false))
        assertEquals(AutoUpdateRunPolicy.WorkerOutcome.RETRY, AutoUpdateRunPolicy.workerOutcome(retryable = true))
    }
}
