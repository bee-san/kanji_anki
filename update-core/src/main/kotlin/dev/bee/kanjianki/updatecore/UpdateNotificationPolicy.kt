package dev.bee.kanjianki.updatecore

object UpdateNotificationPolicy {
    @JvmStatic
    fun shouldShowPendingUpdate(
        hasRuntimeNotificationPermission: Boolean,
        notificationsEnabled: Boolean,
    ): Boolean {
        return hasRuntimeNotificationPermission && notificationsEnabled
    }
}
