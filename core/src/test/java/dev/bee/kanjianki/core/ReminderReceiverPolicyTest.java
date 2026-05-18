package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ReminderReceiverPolicyTest {
    private static final String ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED";
    private static final String ACTION_MY_PACKAGE_REPLACED = "android.intent.action.MY_PACKAGE_REPLACED";
    private static final String ACTION_TIME_CHANGED = "android.intent.action.TIME_SET";
    private static final String ACTION_TIMEZONE_CHANGED = "android.intent.action.TIMEZONE_CHANGED";
    private static final String ACTION_DAILY_REMINDER = "dev.bee.kanjianki.action.DAILY_REMINDER";

    @Test
    public void rescheduleActionsMatchAndroidSystemRecoveryBroadcasts() {
        assertTrue(ReminderReceiverPolicy.shouldReschedule(ACTION_BOOT_COMPLETED));
        assertTrue(ReminderReceiverPolicy.shouldReschedule(ACTION_MY_PACKAGE_REPLACED));
        assertTrue(ReminderReceiverPolicy.shouldReschedule(ACTION_TIME_CHANGED));
        assertTrue(ReminderReceiverPolicy.shouldReschedule(ACTION_TIMEZONE_CHANGED));

        assertFalse(ReminderReceiverPolicy.shouldReschedule(null));
        assertFalse(ReminderReceiverPolicy.shouldReschedule("dev.bee.kanjianki.OTHER"));
    }

    @Test
    public void receiverCommandDispatchesBootDailyAndIgnoredActions() {
        assertEquals(
                ReminderReceiverPolicy.ReceiverCommand.SCHEDULE_FROM_STORED_SETTINGS,
                ReminderReceiverPolicy.commandFor(ACTION_BOOT_COMPLETED, ACTION_DAILY_REMINDER)
        );
        assertEquals(
                ReminderReceiverPolicy.ReceiverCommand.HANDLE_DAILY_REMINDER,
                ReminderReceiverPolicy.commandFor(ACTION_DAILY_REMINDER, ACTION_DAILY_REMINDER)
        );
        assertEquals(
                ReminderReceiverPolicy.ReceiverCommand.NONE,
                ReminderReceiverPolicy.commandFor("dev.bee.kanjianki.OTHER", ACTION_DAILY_REMINDER)
        );
        assertEquals(
                ReminderReceiverPolicy.ReceiverCommand.NONE,
                ReminderReceiverPolicy.commandFor(null, ACTION_DAILY_REMINDER)
        );
        assertEquals(
                ReminderReceiverPolicy.ReceiverCommand.NONE,
                ReminderReceiverPolicy.commandFor(ACTION_DAILY_REMINDER, null)
        );
    }

    @Test
    public void dailyReminderRunsOnlyWhenStoredReminderIsEnabled() {
        assertFalse(ReminderReceiverPolicy.shouldHandleDailyReminder(false));
        assertTrue(ReminderReceiverPolicy.shouldHandleDailyReminder(true));
    }
}
