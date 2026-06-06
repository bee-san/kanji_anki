package dev.bee.kanjianki.reminders

import android.annotation.SuppressLint
import android.app.AlarmManager
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
import dev.bee.kanjianki.R
import dev.bee.kanjianki.core.AdaptiveLoadPlanner
import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.ReminderCopyPolicy
import dev.bee.kanjianki.core.ReminderNotificationPolicy
import dev.bee.kanjianki.core.ReminderSchedulePolicy
import dev.bee.kanjianki.core.StudyLadderRules
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.time.AppClock

object ReminderScheduler {
    const val ACTION_DAILY_REMINDER: String = "dev.bee.kanjianki.action.DAILY_REMINDER"
    private const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"
    private const val CHANNEL_ID = "kani_study_reminders"
    private const val REQUEST_CODE = 2701
    private const val NOTIFICATION_ID = 2702
    private const val WEEK_MILLIS = 7 * 86_400_000L

    @JvmStatic
    fun schedule(context: Context?) {
        if (context == null) {
            return
        }
        LocalStore(context).use { store ->
            schedule(context, store.reminderSettings())
        }
    }

    @JvmStatic
    fun schedule(context: Context?, settings: LocalStoreBase.ReminderSettings?) {
        schedule(context, settings, AppClock.systemClock())
    }

    @JvmStatic
    fun schedule(context: Context?, settings: LocalStoreBase.ReminderSettings?, clock: AppClock?) {
        if (context == null) {
            return
        }
        schedule(context, settings, androidReminderServices(context), AppClock.orSystem(clock).nowMillis())
    }

    @JvmStatic
    fun schedule(
        context: Context?,
        settings: LocalStoreBase.ReminderSettings?,
        services: ReminderServices,
        nowMillis: Long,
    ) {
        if (context == null) {
            return
        }
        if (settings == null || !settings.enabled) {
            services.cancelAlarm()
            return
        }
        LocalStore(context).use { store ->
            val rows = store.activeDashboardRows()
            val studiedToday = store.studiedKanjiSince(startOfLocalDay(nowMillis)).isNotEmpty()
            val futureDueAtMillis = activeReminderDueAtMillis(store.studyItemsForKanji(rows.map { it.kanji }))
            services.scheduleAlarm(nextTriggerMillis(settings, nowMillis, studiedToday, futureDueAtMillis))
        }
    }

    @JvmStatic
    fun schedule(settings: LocalStoreBase.ReminderSettings?, services: ReminderServices, clock: AppClock?) {
        schedule(settings, services, AppClock.orSystem(clock).nowMillis())
    }

    @JvmStatic
    fun schedule(settings: LocalStoreBase.ReminderSettings?, services: ReminderServices, nowMillis: Long) {
        if (settings == null || !settings.enabled) {
            services.cancelAlarm()
            return
        }
        services.scheduleAlarm(nextTriggerMillis(settings, nowMillis))
    }

    @JvmStatic
    fun cancel(context: Context) {
        androidReminderServices(context).cancelAlarm()
    }

    @JvmStatic
    fun notificationsAllowed(context: Context): Boolean = notificationsAllowed(androidReminderServices(context))

    @JvmStatic
    fun notificationsAllowed(services: ReminderServices): Boolean {
        val channelImportance = services.reminderChannelImportance()
        return ReminderNotificationPolicy.notificationsAllowed(
            services.hasRuntimeNotificationPermission(),
            services.areNotificationsEnabled(),
            channelImportance != null && channelImportance == NotificationManager.IMPORTANCE_NONE
        )
    }

    @JvmStatic
    fun hasRuntimeNotificationPermission(context: Context): Boolean {
        return hasRuntimeNotificationPermission(context, Build.VERSION.SDK_INT)
    }

    @JvmStatic
    fun hasRuntimeNotificationPermission(context: Context, sdkInt: Int): Boolean {
        return sdkInt < 33 ||
            context.checkSelfPermission(POST_NOTIFICATIONS_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun ensureNotificationChannel(context: Context) {
        androidReminderServices(context).ensureNotificationChannel()
    }

    @SuppressLint("MissingPermission")
    @JvmStatic
    fun showReminderNotification(context: Context?) {
        if (context == null) {
            return
        }
        showReminderNotification(context, AppClock.systemClock())
    }

    @SuppressLint("MissingPermission")
    @JvmStatic
    fun showReminderNotification(context: Context?, clock: AppClock?) {
        if (context == null) {
            return
        }
        showReminderNotification(context, androidReminderServices(context), clock)
    }

    @SuppressLint("MissingPermission")
    @JvmStatic
    fun showReminderNotification(context: Context?, services: ReminderServices) {
        showReminderNotification(context, services, AppClock.systemClock())
    }

    @SuppressLint("MissingPermission")
    @JvmStatic
    fun showReminderNotification(context: Context?, services: ReminderServices, clock: AppClock?) {
        if (!notificationsAllowed(services) || context == null) {
            return
        }
        services.ensureNotificationChannel()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val copy = reminderCopy(context, clock)
        val open = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(copy.title)
            .setContentText(copy.message)
            .setStyle(Notification.BigTextStyle().bigText(copy.message))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setColor(Color.rgb(255, 76, 118))
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    @JvmStatic
    fun androidReminderServices(context: Context): ReminderServices = AndroidReminderServices(context)

    interface ReminderServices {
        fun scheduleAlarm(triggerAtMillis: Long)

        fun cancelAlarm()

        fun hasRuntimeNotificationPermission(): Boolean

        fun areNotificationsEnabled(): Boolean

        fun reminderChannelImportance(): Int?

        fun ensureNotificationChannel()
    }

    private class AndroidReminderServices(private val context: Context) : ReminderServices {
        override fun scheduleAlarm(triggerAtMillis: Long) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pendingIntent = alarmIntent(PendingIntent.FLAG_UPDATE_CURRENT) ?: return
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }

        override fun cancelAlarm() {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val pendingIntent = alarmIntent(PendingIntent.FLAG_NO_CREATE)
            if (alarmManager != null && pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
            }
        }

        private fun alarmIntent(lookupFlag: Int): PendingIntent? {
            val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_DAILY_REMINDER)
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                lookupFlag or PendingIntent.FLAG_IMMUTABLE
            )
        }

        override fun hasRuntimeNotificationPermission(): Boolean {
            return ReminderScheduler.hasRuntimeNotificationPermission(context)
        }

        override fun areNotificationsEnabled(): Boolean {
            return ReminderScheduler.areNotificationsEnabled(notificationStatus())
        }

        override fun reminderChannelImportance(): Int? {
            val manager = notificationManager() ?: return NotificationManager.IMPORTANCE_NONE
            val channel = manager.getNotificationChannel(CHANNEL_ID)
            return channel?.importance
        }

        override fun ensureNotificationChannel() {
            val manager = notificationManager() ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Study reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = "Friendly Kani review reminders."
            channel.setShowBadge(true)
            manager.createNotificationChannel(channel)
        }

        private fun notificationManager(): NotificationManager? {
            return context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        }

        private fun notificationStatus(): NotificationStatus? {
            val manager = notificationManager() ?: return null
            return NotificationStatus { manager.areNotificationsEnabled() }
        }
    }

    fun interface NotificationStatus {
        fun areNotificationsEnabled(): Boolean
    }

    @JvmStatic
    fun areNotificationsEnabled(status: NotificationStatus?): Boolean {
        return status != null && status.areNotificationsEnabled()
    }

    @JvmStatic
    fun nextTriggerMillis(settings: LocalStoreBase.ReminderSettings): Long {
        return nextTriggerMillis(settings, AppClock.systemClock())
    }

    @JvmStatic
    fun nextTriggerMillis(settings: LocalStoreBase.ReminderSettings, clock: AppClock?): Long {
        return nextTriggerMillis(settings, AppClock.orSystem(clock).nowMillis())
    }

    @JvmStatic
    fun nextTriggerMillis(settings: LocalStoreBase.ReminderSettings, nowMillis: Long): Long {
        return ReminderSchedulePolicy.nextTriggerMillis(settings.hour, settings.minute, nowMillis)
    }

    @JvmStatic
    fun nextTriggerMillis(
        settings: LocalStoreBase.ReminderSettings,
        nowMillis: Long,
        studiedToday: Boolean,
        futureDueAtMillis: Iterable<Long>,
    ): Long {
        return ReminderSchedulePolicy.nextTriggerMillis(
            settings.hour,
            settings.minute,
            nowMillis,
            studiedToday,
            futureDueAtMillis,
        )
    }

    private fun reminderCopy(context: Context, clock: AppClock?): ReminderCopyPolicy.ReminderCopy {
        LocalStore(context).use { store ->
            val rows = store.activeDashboardRows()
            val items = store.studyItemsForKanji(rows.map { it.kanji })
            val now = AppClock.orSystem(clock).nowMillis()
            return ReminderCopyPolicy.forPlan(
                AdaptiveLoadPlanner.PlanRequest.builder(
                    rows,
                    items,
                    store.reviewStatsSince(now - WEEK_MILLIS),
                    store.studyStreak(now).currentDays,
                    store.studiedKanjiSince(startOfLocalDay(now)),
                    AdaptiveLoadPlanner.WorkloadPolicy.fromSettings(
                        store.adaptiveLoadWorkPercent(),
                        store.adaptiveLoadMode(),
                        store.adaptiveLoadMaxItems()
                    ),
                    now
                )
                    .settings(RecordsSyncModels.Settings.kikuDefaults())
                    .build()
            )
        }
    }

    private fun startOfLocalDay(nowMillis: Long): Long = LocalDayPolicy.localDayStart(nowMillis)

    private fun activeReminderDueAtMillis(items: List<RecordsStudyModels.StudyItem>): List<Long> {
        val dueAtMillis = ArrayList<Long>()
        for (item in items) {
            if (isActiveReminderItem(item)) {
                dueAtMillis.add(item.dueAtMillis)
            }
        }
        return dueAtMillis
    }

    private fun isActiveReminderItem(item: RecordsStudyModels.StudyItem): Boolean {
        return StudyLadderRules.STATE_RETIRED != item.state && item.suppressedByTaskType.isEmpty()
    }
}
