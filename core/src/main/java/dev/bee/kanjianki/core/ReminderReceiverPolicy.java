package dev.bee.kanjianki.core;

public final class ReminderReceiverPolicy {
    private static final String ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED";
    private static final String ACTION_MY_PACKAGE_REPLACED = "android.intent.action.MY_PACKAGE_REPLACED";
    private static final String ACTION_TIME_CHANGED = "android.intent.action.TIME_SET";
    private static final String ACTION_TIMEZONE_CHANGED = "android.intent.action.TIMEZONE_CHANGED";

    public enum ReceiverCommand {
        NONE,
        SCHEDULE_FROM_STORED_SETTINGS,
        HANDLE_DAILY_REMINDER
    }

    private ReminderReceiverPolicy() {
    }

    public static boolean shouldReschedule(String action) {
        return ACTION_BOOT_COMPLETED.equals(action)
                || ACTION_MY_PACKAGE_REPLACED.equals(action)
                || ACTION_TIME_CHANGED.equals(action)
                || ACTION_TIMEZONE_CHANGED.equals(action);
    }

    public static ReceiverCommand commandFor(String action, String dailyReminderAction) {
        if (ACTION_BOOT_COMPLETED.equals(action)) {
            return ReceiverCommand.SCHEDULE_FROM_STORED_SETTINGS;
        }
        if (dailyReminderAction != null && dailyReminderAction.equals(action)) {
            return ReceiverCommand.HANDLE_DAILY_REMINDER;
        }
        return ReceiverCommand.NONE;
    }

    public static boolean shouldHandleDailyReminder(boolean reminderEnabled) {
        return reminderEnabled;
    }
}
