package dev.bee.kanjianki.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import dev.bee.kanjianki.MainActivity
import dev.bee.kanjianki.MainActivityBase
import dev.bee.kanjianki.R
import dev.bee.kanjianki.updatecore.UpdateNotificationPolicy
import dev.bee.kanjianki.updatecore.UpdateTextPolicy

object UpdateNotifier {
    private const val CHANNEL_ID = "kani_app_updates"
    private const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"
    private const val REQUEST_CODE = 2801
    private const val NOTIFICATION_ID = 2802

    @JvmStatic
    fun showPendingUpdate(context: Context, version: String?, message: String?): Boolean {
        return showPendingUpdate(version, message, androidController(context))
    }

    @JvmStatic
    fun showPendingUpdate(
        version: String?,
        message: String?,
        controller: NotificationController,
    ): Boolean {
        val hasRuntimeNotificationPermission = controller.hasRuntimeNotificationPermission()
        val notificationsEnabled = hasRuntimeNotificationPermission && controller.areNotificationsEnabled()
        if (!UpdateNotificationPolicy.shouldShowPendingUpdate(
                hasRuntimeNotificationPermission,
                notificationsEnabled,
            )
        ) {
            return false
        }
        val body = UpdateTextPolicy.notificationBody(version, message)
        controller.ensureChannel(
            UpdateTextPolicy.notificationChannelName(),
            UpdateTextPolicy.notificationChannelDescription(),
        )
        controller.notifyUpdate(UpdateTextPolicy.notificationTitle(), body)
        return true
    }

    interface NotificationController {
        fun hasRuntimeNotificationPermission(): Boolean

        fun areNotificationsEnabled(): Boolean

        fun ensureChannel(name: String, description: String)

        fun notifyUpdate(title: String, body: String)
    }

    @JvmStatic
    fun androidController(context: Context): NotificationController {
        return AndroidNotificationController(context, Build.VERSION.SDK_INT)
    }

    class AndroidNotificationController(context: Context, private val sdkInt: Int) : NotificationController {
        private val context: Context = context.applicationContext

        override fun hasRuntimeNotificationPermission(): Boolean {
            return sdkInt < 33 ||
                context.checkSelfPermission(POST_NOTIFICATIONS_PERMISSION) == PackageManager.PERMISSION_GRANTED
        }

        override fun areNotificationsEnabled(): Boolean {
            return notificationsEnabled(manager(), NotificationManager::areNotificationsEnabled)
        }

        override fun ensureChannel(name: String, description: String) {
            val manager = manager() ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                name,
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            channel.description = description
            channel.setShowBadge(true)
            manager.createNotificationChannel(channel)
        }

        override fun notifyUpdate(title: String, body: String) {
            val manager = manager() ?: return
            val open = Intent(context, MainActivity::class.java)
                .putExtra(MainActivityBase.EXTRA_OPEN_UPDATE, true)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val contentIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setColor(Color.rgb(110, 92, 230))
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        }

        private fun manager(): NotificationManager? {
            return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        }
    }

    @JvmStatic
    fun notificationsEnabled(manager: NotificationManager?, check: NotificationEnabledCheck): Boolean {
        return manager != null && check.areNotificationsEnabled(manager)
    }

    fun interface NotificationEnabledCheck {
        fun areNotificationsEnabled(manager: NotificationManager): Boolean
    }
}
