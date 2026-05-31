package dev.bee.kanjianki.updatecore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateNotificationPolicyTest {
    @Test
    fun pendingUpdateNotificationRequiresPermissionAndEnabledNotifications() {
        assertFalse(UpdateNotificationPolicy.shouldShowPendingUpdate(false, true))
        assertFalse(UpdateNotificationPolicy.shouldShowPendingUpdate(true, false))
        assertTrue(UpdateNotificationPolicy.shouldShowPendingUpdate(true, true))
    }
}
