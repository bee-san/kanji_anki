package dev.bee.kanjianki

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreSyncGateTest {
    @Test
    fun refusesRestoreWhileManualSyncIsRunning() {
        assertFalse(BackupRestoreSyncGate { true }.restoreAllowed())
        assertTrue(BackupRestoreSyncGate { false }.restoreAllowed())
    }
}
