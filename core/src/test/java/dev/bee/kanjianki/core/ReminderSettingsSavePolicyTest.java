package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ReminderSettingsSavePolicyTest {
    @Test
    public void fieldsNormalizeReminderTimeBounds() {
        ReminderSettingsSavePolicy.ReminderFields fields = ReminderSettingsSavePolicy.fields(true, 30, -4);

        assertTrue(fields.enabled());
        assertEquals(23, fields.hour());
        assertEquals(0, fields.minute());
    }

    @Test
    public void fieldsPreserveDisabledState() {
        ReminderSettingsSavePolicy.ReminderFields fields = ReminderSettingsSavePolicy.fields(false, 8, 30);

        assertFalse(fields.enabled());
        assertEquals(8, fields.hour());
        assertEquals(30, fields.minute());
    }

    @Test
    public void savedMessagePreservesNotificationAwareCopy() {
        assertEquals("Reminder saved for around 08:05.", ReminderSettingsSavePolicy.savedMessage(8, 5, true));
        assertEquals(
                "Reminder saved, but Android notifications are off.",
                ReminderSettingsSavePolicy.savedMessage(8, 5, false)
        );
    }

    @Test
    public void disabledAndDeniedCopyIsCentralized() {
        assertEquals("Reminder turned off.", ReminderSettingsSavePolicy.DISABLED_MESSAGE);
        assertEquals("Notifications are off, so reminders are disabled.", ReminderSettingsSavePolicy.PERMISSION_DENIED_MESSAGE);
    }
}
