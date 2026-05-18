package dev.bee.kanjianki.core;

public final class ReminderSettingsSavePolicy {
    public static final String DISABLED_MESSAGE = "Reminder turned off.";
    public static final String NOTIFICATIONS_BLOCKED_MESSAGE = "Reminder saved, but Android notifications are off.";
    public static final String PERMISSION_DENIED_MESSAGE = "Notifications are off, so reminders are disabled.";

    private ReminderSettingsSavePolicy() {
    }

    public static ReminderFields fields(boolean enabled, int hour, int minute) {
        TimeOfDaySettingsPolicy.ReminderFields normalized = TimeOfDaySettingsPolicy.normalizeReminder(enabled, hour, minute);
        return new ReminderFields(normalized.enabled(), normalized.hour(), normalized.minute());
    }

    public static String savedMessage(int hour, int minute, boolean notificationsAllowed) {
        if (!notificationsAllowed) {
            return NOTIFICATIONS_BLOCKED_MESSAGE;
        }
        return "Reminder saved for around " + TimeOfDaySettingsPolicy.displayTime(hour, minute) + ".";
    }

    public record ReminderFields(boolean enabled, int hour, int minute) {
    }
}
