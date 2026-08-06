package dev.bee.kanjianki.update

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import dev.bee.kanjianki.host.KaniHostActivity
import dev.bee.kanjianki.MainActivityBase
import dev.bee.kanjianki.R
import dev.bee.kanjianki.notifications.AndroidNotificationGateway
import dev.bee.kanjianki.updatecore.UpdateNotificationPolicy
import dev.bee.kanjianki.updatecore.UpdateTextPolicy

object UpdateNotifier {
    private const val CHANNEL_ID = "kani_app_updates"
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
        if (!controller.ensureChannel(
                UpdateTextPolicy.notificationChannelName(),
                UpdateTextPolicy.notificationChannelDescription(),
            )
        ) {
            return false
        }
        if (controller.channelImportance() == NotificationManager.IMPORTANCE_NONE) {
            return false
        }
        return controller.notifyUpdate(UpdateTextPolicy.notificationTitle(), body)
    }

    interface NotificationController {
        fun hasRuntimeNotificationPermission(): Boolean

        fun areNotificationsEnabled(): Boolean

        fun ensureChannel(name: String, description: String): Boolean

        fun channelImportance(): Int?

        fun notifyUpdate(title: String, body: String): Boolean
    }

    @JvmStatic
    fun androidController(context: Context): NotificationController {
        return AndroidNotificationController(context, Build.VERSION.SDK_INT)
    }

    class AndroidNotificationController(context: Context, sdkInt: Int) : NotificationController {
        private val context: Context = context.applicationContext
        private val notifications = AndroidNotificationGateway(context, sdkInt)

        override fun hasRuntimeNotificationPermission(): Boolean {
            return notifications.hasRuntimePermission()
        }

        override fun areNotificationsEnabled(): Boolean {
            return notifications.areNotificationsEnabled()
        }

        override fun ensureChannel(name: String, description: String): Boolean {
            return notifications.ensureChannel(
                CHANNEL_ID,
                name,
                description,
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        }

        override fun channelImportance(): Int? = notifications.channelImportance(CHANNEL_ID)

        override fun notifyUpdate(title: String, body: String): Boolean {
            val open = updateOpenIntent(context)
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
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .setColor(Color.rgb(110, 92, 230))
                .build()
            return notifications.post(NOTIFICATION_ID, notification)
        }
    }

    @JvmStatic
    fun updateOpenIntent(context: Context): Intent {
        return Intent(context, KaniHostActivity::class.java)
            .putExtra(MainActivityBase.EXTRA_OPEN_UPDATE, true)
            .setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
    }

    @JvmStatic
    fun cancelPendingUpdate(context: Context?): Boolean {
        return context?.let { AndroidNotificationGateway(it).cancel(NOTIFICATION_ID) } ?: false
    }

    @JvmStatic
    fun notificationsEnabled(manager: NotificationManager?, check: NotificationEnabledCheck): Boolean {
        return manager != null && check.areNotificationsEnabled(manager)
    }

    fun interface NotificationEnabledCheck {
        fun areNotificationsEnabled(manager: NotificationManager): Boolean
    }
}
