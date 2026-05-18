package dev.bee.kanjianki.updatecore;

public final class UpdateNotificationPolicy {
    private UpdateNotificationPolicy() {
    }

    public static boolean shouldShowPendingUpdate(
            boolean hasRuntimeNotificationPermission,
            boolean notificationsEnabled
    ) {
        return hasRuntimeNotificationPermission && notificationsEnabled;
    }
}
