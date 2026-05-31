package dev.bee.kanjianki.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderNotificationPolicyTest {
    @Test
    fun notificationsRequirePermissionEnabledStatusAndUnblockedChannel() {
        assertFalse(ReminderNotificationPolicy.notificationsAllowed(false, true, false))
        assertFalse(ReminderNotificationPolicy.notificationsAllowed(true, false, false))
        assertFalse(ReminderNotificationPolicy.notificationsAllowed(true, true, true))
        assertTrue(ReminderNotificationPolicy.notificationsAllowed(true, true, false))
    }
}
