package dev.bee.kanjianki.core

object ReminderNotificationPolicy {
    @JvmStatic
    fun notificationsAllowed(
        hasRuntimeNotificationPermission: Boolean,
        notificationsEnabled: Boolean,
        channelBlocked: Boolean,
    ): Boolean {
        return hasRuntimeNotificationPermission && notificationsEnabled && !channelBlocked
    }
}
