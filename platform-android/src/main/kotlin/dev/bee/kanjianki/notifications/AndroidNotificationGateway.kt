package dev.bee.kanjianki.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

class AndroidNotificationGateway(
    context: Context,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) {
    private val context = context.applicationContext

    fun hasRuntimePermission(): Boolean {
        return sdkInt < 33 ||
            context.checkSelfPermission(POST_NOTIFICATIONS_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    fun hasManager(): Boolean = manager() != null

    fun areNotificationsEnabled(): Boolean = manager()?.areNotificationsEnabled() == true

    fun channelImportance(channelId: String): Int? {
        return manager()?.getNotificationChannel(channelId)?.importance
    }

    fun ensureChannel(
        channelId: String,
        name: String,
        description: String,
        importance: Int,
    ): Boolean {
        val manager = manager() ?: return false
        val channel = NotificationChannel(channelId, name, importance)
        channel.description = description
        channel.setShowBadge(true)
        manager.createNotificationChannel(channel)
        return true
    }

    fun post(notificationId: Int, notification: Notification): Boolean {
        val manager = manager() ?: return false
        return NotificationDeliveryPolicy.attempt {
            manager.notify(notificationId, notification)
        }
    }

    fun cancel(notificationId: Int): Boolean {
        val manager = manager() ?: return false
        manager.cancel(notificationId)
        return true
    }

    private fun manager(): NotificationManager? {
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    }

    private companion object {
        const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"
    }
}
