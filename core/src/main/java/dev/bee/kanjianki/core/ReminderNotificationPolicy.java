package dev.bee.kanjianki.core;

public final class ReminderNotificationPolicy {
    private ReminderNotificationPolicy() {
    }

    public static boolean notificationsAllowed(
            boolean hasRuntimeNotificationPermission,
            boolean notificationsEnabled,
            boolean channelBlocked
    ) {
        return hasRuntimeNotificationPermission && notificationsEnabled && !channelBlocked;
    }
}
