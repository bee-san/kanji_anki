package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderReceiverPolicyTest {
    @Test
    fun rescheduleActionsMatchAndroidSystemRecoveryBroadcasts() {
        assertTrue(ReminderReceiverPolicy.shouldReschedule(ACTION_BOOT_COMPLETED))
        assertTrue(ReminderReceiverPolicy.shouldReschedule(ACTION_MY_PACKAGE_REPLACED))
        assertTrue(ReminderReceiverPolicy.shouldReschedule(ACTION_TIME_CHANGED))
        assertTrue(ReminderReceiverPolicy.shouldReschedule(ACTION_TIMEZONE_CHANGED))

        assertFalse(ReminderReceiverPolicy.shouldReschedule(null))
        assertFalse(ReminderReceiverPolicy.shouldReschedule("dev.bee.kanjianki.OTHER"))
    }

    @Test
    fun receiverCommandDispatchesBootDailyAndIgnoredActions() {
        assertEquals(
            ReminderReceiverPolicy.ReceiverCommand.SCHEDULE_FROM_STORED_SETTINGS,
            ReminderReceiverPolicy.commandFor(ACTION_BOOT_COMPLETED, ACTION_DAILY_REMINDER),
        )
        assertEquals(
            ReminderReceiverPolicy.ReceiverCommand.HANDLE_DAILY_REMINDER,
            ReminderReceiverPolicy.commandFor(ACTION_DAILY_REMINDER, ACTION_DAILY_REMINDER),
        )
        assertEquals(
            ReminderReceiverPolicy.ReceiverCommand.NONE,
            ReminderReceiverPolicy.commandFor("dev.bee.kanjianki.OTHER", ACTION_DAILY_REMINDER),
        )
        assertEquals(
            ReminderReceiverPolicy.ReceiverCommand.NONE,
            ReminderReceiverPolicy.commandFor(null, ACTION_DAILY_REMINDER),
        )
        assertEquals(
            ReminderReceiverPolicy.ReceiverCommand.NONE,
            ReminderReceiverPolicy.commandFor(ACTION_DAILY_REMINDER, null),
        )
    }

    @Test
    fun receiverCommandDispatchesDismissedAction() {
        assertEquals(
            ReminderReceiverPolicy.ReceiverCommand.HANDLE_REMINDER_DISMISSED,
            ReminderReceiverPolicy.commandFor(ACTION_REMINDER_DISMISSED, ACTION_DAILY_REMINDER, ACTION_REMINDER_DISMISSED),
        )
        // Without a dismissed-action argument, the same action is ignored.
        assertEquals(
            ReminderReceiverPolicy.ReceiverCommand.NONE,
            ReminderReceiverPolicy.commandFor(ACTION_REMINDER_DISMISSED, ACTION_DAILY_REMINDER),
        )
        // Boot still wins over a dismissed action.
        assertEquals(
            ReminderReceiverPolicy.ReceiverCommand.SCHEDULE_FROM_STORED_SETTINGS,
            ReminderReceiverPolicy.commandFor(ACTION_BOOT_COMPLETED, ACTION_DAILY_REMINDER, ACTION_REMINDER_DISMISSED),
        )
        assertEquals(
            ReminderReceiverPolicy.ReceiverCommand.NONE,
            ReminderReceiverPolicy.commandFor("dev.bee.kanjianki.OTHER", ACTION_DAILY_REMINDER, ACTION_REMINDER_DISMISSED),
        )
    }

    @Test
    fun dailyReminderRunsOnlyWhenStoredReminderIsEnabled() {
        assertFalse(ReminderReceiverPolicy.shouldHandleDailyReminder(false))
        assertTrue(ReminderReceiverPolicy.shouldHandleDailyReminder(true))
    }

    private companion object {
        private const val ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED"
        private const val ACTION_MY_PACKAGE_REPLACED = "android.intent.action.MY_PACKAGE_REPLACED"
        private const val ACTION_TIME_CHANGED = "android.intent.action.TIME_SET"
        private const val ACTION_TIMEZONE_CHANGED = "android.intent.action.TIMEZONE_CHANGED"
        private const val ACTION_DAILY_REMINDER = "dev.bee.kanjianki.action.DAILY_REMINDER"
        private const val ACTION_REMINDER_DISMISSED = "dev.bee.kanjianki.action.REMINDER_DISMISSED"
    }
}
