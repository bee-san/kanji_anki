package dev.bee.kanjianki.updatecore;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class UpdateNotificationPolicyTest {
    @Test
    public void pendingUpdateNotificationRequiresPermissionAndEnabledNotifications() {
        assertFalse(UpdateNotificationPolicy.shouldShowPendingUpdate(false, true));
        assertFalse(UpdateNotificationPolicy.shouldShowPendingUpdate(true, false));
        assertTrue(UpdateNotificationPolicy.shouldShowPendingUpdate(true, true));
    }
}
