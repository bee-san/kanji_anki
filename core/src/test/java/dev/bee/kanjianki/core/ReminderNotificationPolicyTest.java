package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ReminderNotificationPolicyTest {
    @Test
    public void notificationsRequirePermissionEnabledStatusAndUnblockedChannel() {
        assertFalse(ReminderNotificationPolicy.notificationsAllowed(false, true, false));
        assertFalse(ReminderNotificationPolicy.notificationsAllowed(true, false, false));
        assertFalse(ReminderNotificationPolicy.notificationsAllowed(true, true, true));
        assertTrue(ReminderNotificationPolicy.notificationsAllowed(true, true, false));
    }
}
