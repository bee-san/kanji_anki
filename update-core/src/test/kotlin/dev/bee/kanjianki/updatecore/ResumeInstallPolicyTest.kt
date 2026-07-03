package dev.bee.kanjianki.updatecore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeInstallPolicyTest {
    @Test
    fun pendingVerifiedUpdateInstallsOnResumeWhenPermitted() {
        assertTrue(ResumeInstallPolicy.shouldInstall(true, true, true, false))
    }

    @Test
    fun disabledAutomaticUpdatesDoNotInstallOnResume() {
        assertFalse(ResumeInstallPolicy.shouldInstall(false, true, true, false))
    }

    @Test
    fun missingInstallPermissionDoesNotInstallOnResume() {
        assertFalse(ResumeInstallPolicy.shouldInstall(true, false, true, false))
    }

    @Test
    fun noPendingUpdateDoesNothingOnResume() {
        assertFalse(ResumeInstallPolicy.shouldInstall(true, true, false, false))
    }

    @Test
    fun inFlightInstallAttemptIsNotRepeated() {
        assertFalse(ResumeInstallPolicy.shouldInstall(true, true, true, true))
    }
}
