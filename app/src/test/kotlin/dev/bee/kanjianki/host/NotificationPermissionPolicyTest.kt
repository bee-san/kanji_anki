package dev.bee.kanjianki.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When asking for POST_NOTIFICATIONS would actually show the user a dialog.
 *
 * The two "no" cases matter more than the "yes" one, because they fail the same way and
 * silently: the caller parks the user's in-flight reminder change before launching the
 * dialog, so a request that shows nothing leaves that change parked with no callback coming
 * to settle it.
 */
class NotificationPermissionPolicyTest {
    @Test
    fun aModernDeviceWithoutThePermissionIsAsked() {
        assertTrue(NotificationPermissionPolicy.shouldRequest(apiLevel = 33, granted = false))
        assertTrue(NotificationPermissionPolicy.shouldRequest(apiLevel = 36, granted = false))
    }

    @Test
    fun anAlreadyGrantedPermissionIsNotAskedForAgain() {
        // A dialog that answers itself: the framework returns the existing grant, and the
        // user sees a flicker at best.
        assertFalse(NotificationPermissionPolicy.shouldRequest(apiLevel = 33, granted = true))
        assertFalse(NotificationPermissionPolicy.shouldRequest(apiLevel = 36, granted = true))
    }

    @Test
    fun anOsWithoutThePermissionIsNotAskedEitherWay() {
        // Below API 33 the permission does not exist: `requestPermissions` returns
        // immediately having asked nothing, so treating it as askable would park a reminder
        // change against a callback that conveys no user decision.
        for (api in 26..NotificationPermissionPolicy.MIN_API_WITH_PERMISSION - 1) {
            assertFalse("api $api", NotificationPermissionPolicy.shouldRequest(api, granted = false))
            assertFalse("api $api", NotificationPermissionPolicy.shouldRequest(api, granted = true))
        }
    }

    @Test
    fun theBoundaryIsTheVersionThatIntroducedThePermission() {
        // Pinned rather than left to the constant, so a change to the constant is a
        // deliberate decision rather than a silently redefined boundary.
        assertEquals(33, NotificationPermissionPolicy.MIN_API_WITH_PERMISSION)
        assertFalse(NotificationPermissionPolicy.shouldRequest(apiLevel = 32, granted = false))
        assertTrue(NotificationPermissionPolicy.shouldRequest(apiLevel = 33, granted = false))
    }
}
