package dev.bee.kanjianki.reminders

import dev.bee.kanjianki.AppLocalStoreFactory

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import dev.bee.kanjianki.MainActivity
import dev.bee.kanjianki.MainActivityBase
import dev.bee.kanjianki.R
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.DailyReminderDecisionPolicy
import dev.bee.kanjianki.core.DailyReminderDecisionRequest
import dev.bee.kanjianki.core.DailyStudyPlan
import dev.bee.kanjianki.core.DailyStudyPlanPolicy
import dev.bee.kanjianki.core.DailyStudyPlanRequest
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.ReminderAntiSpamPolicy
import dev.bee.kanjianki.core.ReminderCopyPolicy
import dev.bee.kanjianki.core.ReminderEligibilityPolicy
import dev.bee.kanjianki.core.ReminderFamily
import dev.bee.kanjianki.core.ReminderNotificationPolicy
import dev.bee.kanjianki.core.ReminderReviewBatchPolicy
import dev.bee.kanjianki.core.ReminderSchedulePolicy
import dev.bee.kanjianki.core.ReminderSnoozePolicy
import dev.bee.kanjianki.core.ReminderThrottlePolicy
import dev.bee.kanjianki.core.StudyStreakPolicy
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.notifications.AndroidNotificationGateway
import dev.bee.kanjianki.time.AppClock

object ReminderScheduler {
    const val ACTION_DAILY_REMINDER: String = "dev.bee.kanjianki.action.DAILY_REMINDER"
    const val REMINDER_CHANNEL_ID = "kani_study_reminders"
    private const val CHANNEL_ID = REMINDER_CHANNEL_ID
    const val ACTION_REMINDER_DISMISSED: String = "dev.bee.kanjianki.action.REMINDER_DISMISSED"
    const val ACTION_REMINDER_SNOOZED: String = "dev.bee.kanjianki.action.REMINDER_SNOOZED"
    const val EXTRA_REMINDER_FAMILY: String = "dev.bee.kanjianki.extra.REMINDER_FAMILY"
    const val EXTRA_REMINDER_HOUR: String = "dev.bee.kanjianki.extra.REMINDER_HOUR"
    const val EXTRA_REMINDER_MINUTE: String = "dev.bee.kanjianki.extra.REMINDER_MINUTE"
    const val EXTRA_REMINDER_SNOOZE_REPOST: String = "dev.bee.kanjianki.extra.REMINDER_SNOOZE_REPOST"
    private const val REQUEST_CODE = 2701
    private const val NOTIFICATION_ID = 2702
    private const val DISMISS_REQUEST_CODE = 2703
    private const val STUDY_ACTION_REQUEST_CODE = 2704
    private const val SNOOZE_ACTION_REQUEST_CODE = 2705

    @JvmStatic
    fun schedule(context: Context?) {
        if (context == null) {
            return
        }
        AppLocalStoreFactory.create(context).use { store ->
            schedule(context, store.reminderSettings())
        }
    }

    /**
     * Activity-owned-store variant used by foreground re-arms. Reusing the same [LocalStore]
     * preserves its dashboard/study caches after a route load and avoids opening a second helper
     * that repeats the full dashboard read.
     */
    @JvmStatic
    internal fun schedule(context: Context, store: LocalStore) {
        schedule(store, androidReminderServices(context), AppClock.systemClock().nowMillis())
    }

    @JvmStatic
    internal fun schedule(
        store: LocalStore,
        services: ReminderServices,
        nowMillis: Long,
    ) {
        schedule(store.reminderSettings(), store, services, nowMillis)
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
        val nowMillis = AppClock.orSystem(clock).nowMillis()
        schedule(context, settings, androidReminderServices(context), nowMillis)
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
        AppLocalStoreFactory.create(context).use { store ->
            schedule(settings, store, services, nowMillis)
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
        services.scheduleAlarm(nextTriggerMillis(settings, nowMillis), settings.hour, settings.minute)
    }

    @JvmStatic
    fun scheduleSnooze(context: Context?, family: String) {
        if (context == null) {
            return
        }
        AppLocalStoreFactory.create(context).use { store ->
            scheduleSnooze(
                store.reminderSettings(),
                store.reminderAntiSpamSettings(),
                androidReminderServices(context),
                AppClock.systemClock().nowMillis(),
                family,
            )
        }
    }

    @JvmStatic
    internal fun scheduleSnooze(
        settings: LocalStoreBase.ReminderSettings?,
        antiSpam: LocalStoreBase.ReminderAntiSpamSettings,
        services: ReminderServices,
        nowMillis: Long,
        family: String = "",
    ) {
        if (settings == null || !settings.enabled) {
            services.cancelAlarm()
            return
        }
        services.scheduleSnoozeAlarm(
            ReminderSnoozePolicy.rearmTime(
                nowMillis,
                antiSpam.quietStartMinuteOfDay,
                antiSpam.quietEndMinuteOfDay,
            ),
            settings.hour,
            settings.minute,
            family,
        )
    }

    private fun schedule(
        settings: LocalStoreBase.ReminderSettings?,
        store: LocalStore,
        services: ReminderServices,
        nowMillis: Long,
    ) {
        if (settings == null || !settings.enabled) {
            services.cancelAlarm()
            return
        }
        services.scheduleAlarm(nextAlarmMillis(settings, store, nowMillis), settings.hour, settings.minute)
    }

    /**
     * Compute when the reminder alarm should next fire from fresh state. A
     * suppressed post (min gap / activity grace) re-arms for its next eligible
     * time rather than `now`, which structurally removes the D1 immediate-fire
     * loop instead of relying on the daily cap as a fuse.
     */
    private fun nextAlarmMillis(
        settings: LocalStoreBase.ReminderSettings,
        store: LocalStore,
        nowMillis: Long,
    ): Long {
        val streak = store.studyStreak(nowMillis)
        if (!streak.studiedToday) {
            // Not studied today: the daily nudge fires at the configured time, but a
            // useful earlier reminder (due-later cluster end / streak) pulls the
            // alarm forward when the decision policy asks for it (D3). Quiet-hour
            // pull-forward is already baked into the decision's triggerAtMillis.
            val dailyTime = ReminderSchedulePolicy.nextTriggerMillis(settings.hour, settings.minute, nowMillis)
            val plan = evaluate(store, nowMillis)
            val planTrigger = plan?.triggerAtMillis?.takeIf { it > nowMillis } ?: return dailyTime
            return minOf(dailyTime, planTrigger)
        }

        val plan = evaluate(store, nowMillis)
        val dailyFallback = ReminderSchedulePolicy.nextTriggerMillis(settings.hour, settings.minute, nowMillis, false)
        if (plan == null) {
            // Nothing useful to post today; wait for tomorrow's daily time.
            return dailyFallback
        }
        val trigger = maxOf(nowMillis, plan.triggerAtMillis)
        if (trigger > nowMillis) {
            // Work is in the future (cluster end / decision time): arm for then and
            // let the fire-time throttle re-check decide whether to post. The
            // throttle is a post-time gate; evaluating it now against a distant
            // trigger would wrongly apply the current gap/grace window.
            return trigger
        }
        // Fire-now (overdue): if the throttle denies at this instant, arm for the
        // next eligible time instead of now — this structurally removes the D1
        // immediate-fire loop rather than relying on the daily cap as a fuse.
        val throttle = plan.throttleDecision
        if (throttle.allow) {
            return nowMillis
        }
        val nextEligible = throttle.nextEligibleAtMillis
        if (nextEligible > nowMillis) {
            return minOf(nextEligible, dailyFallback).coerceAtLeast(nowMillis)
        }
        // No time-based re-arm (unchanged signature): wait for the daily time or a
        // signature change surfaced by a later re-arm.
        return dailyFallback
    }

    @JvmStatic
    fun cancel(context: Context) {
        androidReminderServices(context).cancelAlarm()
    }

    /**
     * Cancels any posted reminder (slot 2702). Called when the app is opened —
     * the user got the message, so the visible notification is cleared. Alarm
     * re-arming is a separate step so the caller controls its cadence.
     */
    @JvmStatic
    fun cancelPostedNotification(context: Context?) {
        context?.let { AndroidNotificationGateway(it).cancel(NOTIFICATION_ID) }
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
        return AndroidNotificationGateway(context, sdkInt).hasRuntimePermission()
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
        showReminderNotification(context, androidReminderServices(context), clock, false, "")
    }

    @SuppressLint("MissingPermission")
    @JvmStatic
    fun showReminderNotification(context: Context?, snoozeRepost: Boolean, snoozedFamily: String) {
        if (context == null) {
            return
        }
        showReminderNotification(
            context,
            androidReminderServices(context),
            AppClock.systemClock(),
            snoozeRepost,
            snoozedFamily,
        )
    }

    @SuppressLint("MissingPermission")
    @JvmStatic
    fun showReminderNotification(context: Context?, services: ReminderServices) {
        showReminderNotification(context, services, AppClock.systemClock())
    }

    @SuppressLint("MissingPermission")
    @JvmStatic
    fun showReminderNotification(context: Context?, services: ReminderServices, clock: AppClock?) {
        showReminderNotification(context, services, clock, false, "")
    }

    @SuppressLint("MissingPermission")
    @JvmStatic
    fun showReminderNotification(
        context: Context?,
        services: ReminderServices,
        clock: AppClock?,
        snoozeRepost: Boolean,
        snoozedFamily: String,
    ) {
        if (!notificationsAllowed(services) || context == null) {
            return
        }
        AppLocalStoreFactory.create(context).use { store ->
            val now = AppClock.orSystem(clock).nowMillis()
            val reservedFamily = if (snoozeRepost) reminderFamilyOrNull(snoozedFamily) else null
            if (snoozeRepost && reservedFamily == null) {
                return@use
            }
            val plan = evaluate(store, now, reservedFamily) ?: return@use
            // Anti-spam gate: only post when the throttle allows it right now.
            // A blocked post leaves the alarm to re-arm for the next eligible time.
            if (!plan.throttleDecision.allow && reservedFamily == null) {
                return@use
            }
            services.ensureNotificationChannel()
            val open = reminderOpenIntent(context, plan.family)
            val contentIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val studyIntent = Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivityBase.EXTRA_OPEN_STUDY, true)
            val studyPendingIntent = PendingIntent.getActivity(
                context,
                STUDY_ACTION_REQUEST_CODE,
                studyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val snoozeIntent = Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_REMINDER_SNOOZED)
                .putExtra(EXTRA_REMINDER_FAMILY, plan.family ?: "")
            val snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                SNOOZE_ACTION_REQUEST_CODE,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(plan.copy.title)
                .setContentText(plan.copy.message)
                .setStyle(Notification.BigTextStyle().bigText(plan.copy.message))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                // Replacing the single notification slot (ID 2702) in place must not
                // re-buzz: without this, an immediate re-post (D1) or a fresh-state
                // recompute alerts the user twice for one visible card.
                .setOnlyAlertOnce(true)
                .setDeleteIntent(dismissIntent(context, plan.family))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setColor(Color.rgb(255, 76, 118))
                .addAction(Notification.Action.Builder(null, HomeTextCopy.reminderStudyNowAction(), studyPendingIntent).build())
                .addAction(Notification.Action.Builder(null, HomeTextCopy.reminderSnoozeAction(), snoozePendingIntent).build())
                .build()
            if (!AndroidNotificationGateway(context).post(NOTIFICATION_ID, notification)) {
                return@use
            }
            if (reservedFamily == null) {
                store.recordReminderPosted(now, plan.family, plan.signature, plan.dailyTimeOverride)
            } else {
                store.recordReminderReposted(now, plan.signature)
            }
            if (plan.reviewBatch && reservedFamily == null) {
                // Keep the legacy per-day review counter in sync so the hard 2/day
                // cap continues to engage alongside the new throttle.
                store.recordReviewReminderNotificationShown(now)
            }
        }
    }

    @JvmStatic
    fun reminderOpenIntent(context: Context, family: String?): Intent {
        return Intent(context, MainActivity::class.java)
            .apply {
                if (family != ReminderFamily.SYNC.name) {
                    putExtra(MainActivityBase.EXTRA_OPEN_STUDY, true)
                } else {
                    putExtra(MainActivityBase.EXTRA_OPEN_HOME, true)
                }
            }
            .setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
    }

    @JvmStatic
    fun dailyReminderIntent(
        context: Context,
        hour: Int,
        minute: Int,
        snoozeRepost: Boolean = false,
        family: String = "",
    ): Intent {
        return Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_DAILY_REMINDER)
            .putExtra(EXTRA_REMINDER_HOUR, hour)
            .putExtra(EXTRA_REMINDER_MINUTE, minute)
            .putExtra(EXTRA_REMINDER_SNOOZE_REPOST, snoozeRepost)
            .putExtra(EXTRA_REMINDER_FAMILY, family)
    }

    @JvmStatic
    fun scheduleFallbackDailyReminder(context: Context?, hour: Int, minute: Int) {
        if (context == null) {
            return
        }
        val settings = LocalStoreBase.ReminderSettings(true, hour, minute).normalized()
        schedule(settings, androidReminderServices(context), AppClock.systemClock().nowMillis())
    }

    private fun dismissIntent(context: Context, family: String?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_REMINDER_DISMISSED)
            .putExtra(EXTRA_REMINDER_FAMILY, family ?: "")
        return PendingIntent.getBroadcast(
            context,
            DISMISS_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    @JvmStatic
    fun androidReminderServices(context: Context): ReminderServices = AndroidReminderServices(context)

    interface ReminderServices {
        fun scheduleAlarm(triggerAtMillis: Long, hour: Int, minute: Int)

        fun scheduleSnoozeAlarm(triggerAtMillis: Long, hour: Int, minute: Int, family: String) {
            scheduleAlarm(triggerAtMillis, hour, minute)
        }

        fun cancelAlarm()

        fun hasRuntimeNotificationPermission(): Boolean

        fun areNotificationsEnabled(): Boolean

        fun reminderChannelImportance(): Int?

        fun ensureNotificationChannel()
    }

    private class AndroidReminderServices(private val context: Context) : ReminderServices {
        private val notifications = AndroidNotificationGateway(context)

        override fun scheduleAlarm(triggerAtMillis: Long, hour: Int, minute: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pendingIntent = alarmIntent(PendingIntent.FLAG_UPDATE_CURRENT, hour, minute, false, "") ?: return
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }

        override fun scheduleSnoozeAlarm(triggerAtMillis: Long, hour: Int, minute: Int, family: String) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pendingIntent = alarmIntent(PendingIntent.FLAG_UPDATE_CURRENT, hour, minute, true, family) ?: return
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }

        override fun cancelAlarm() {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val pendingIntent = alarmIntent(PendingIntent.FLAG_NO_CREATE, 0, 0, false, "")
            if (pendingIntent != null) {
                alarmManager?.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }

        private fun alarmIntent(
            lookupFlag: Int,
            hour: Int,
            minute: Int,
            snoozeRepost: Boolean,
            family: String,
        ): PendingIntent? {
            val intent = dailyReminderIntent(context, hour, minute, snoozeRepost, family)
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                lookupFlag or PendingIntent.FLAG_IMMUTABLE
            )
        }

        override fun hasRuntimeNotificationPermission(): Boolean {
            return notifications.hasRuntimePermission()
        }

        override fun areNotificationsEnabled(): Boolean {
            return notifications.areNotificationsEnabled()
        }

        override fun reminderChannelImportance(): Int? {
            if (!notifications.hasManager()) {
                return NotificationManager.IMPORTANCE_NONE
            }
            return notifications.channelImportance(CHANNEL_ID)
        }

        override fun ensureNotificationChannel() {
            notifications.ensureChannel(
                CHANNEL_ID,
                ReminderCopyPolicy.notificationChannelName(),
                ReminderCopyPolicy.notificationChannelDescription(),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
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

    /**
     * Fresh-state reminder plan: what to post, its copy/family/signature, when the
     * alarm should ideally trigger, and the throttle verdict. Returns null when
     * there is nothing worth posting today. Both the arm path ([nextAlarmMillis])
     * and the post path ([showReminderNotification]) share this so the two never
     * disagree.
     */
    private data class PlannedReminder(
        val copy: ReminderCopyPolicy.ReminderCopy,
        val family: String,
        val signature: String,
        val triggerAtMillis: Long,
        val reviewBatch: Boolean,
        val dailyTimeOverride: Boolean,
        val throttleDecision: dev.bee.kanjianki.core.ReminderThrottlePolicy.Decision,
    )

    private fun evaluate(
        store: LocalStore,
        nowMillis: Long,
        reservedFamily: ReminderFamily? = null,
    ): PlannedReminder? {
        val rows = store.activeDashboardRows()
        val eligibleItems = eligibleReminderItems(store, rows)
        val streak = store.studyStreak(nowMillis)
        val antiSpam = store.reminderAntiSpamSettings()
        val throttleState = store.reminderThrottleState(nowMillis)
        val settings = store.reminderSettings()
        val dailyTimeOverride = reservedFamily == null &&
            isDailyReminderTime(settings, nowMillis) &&
            !throttleState.dailyOverrideUsedToday

        if (streak.studiedToday) {
            if (reservedFamily != null && reservedFamily != ReminderFamily.DUE) {
                return null
            }
            // Studied today: only a genuine review batch may fire. A daily-time
            // override lowers the minimum batch to 1 so the once-a-day nudge still
            // works even for a small tail.
            val minBatch = if (dailyTimeOverride || reservedFamily != null) {
                1
            } else {
                ReminderReviewBatchPolicy.DEFAULT_MIN_BATCH_SIZE
            }
            val batch = ReminderReviewBatchPolicy.nextBatch(
                nowMillis,
                eligibleItems,
                reviewNotificationsToday(
                    store,
                    antiSpam,
                    nowMillis,
                    reserveOriginalPost = reservedFamily == ReminderFamily.DUE,
                ),
                minBatch,
            ) ?: return null
            val signature = ReminderThrottlePolicy.signatureFor(batch.dueCount, batch.latestDueAtMillis)
            val throttle = throttleDecision(throttleState, streak, signature, antiSpam, nowMillis, dailyTimeOverride)
            return PlannedReminder(
                copy = ReminderCopyPolicy.reviewCopy(batch.dueCount),
                family = ReminderFamily.DUE.name,
                signature = signature,
                triggerAtMillis = batch.triggerAtMillis,
                reviewBatch = true,
                dailyTimeOverride = dailyTimeOverride,
                throttleDecision = throttle,
            )
        }

        // Not studied today: the decision policy drives the daily reminder,
        // wired with quiet hours, per-family caps, and dismissed families (D3).
        val decision = dailyReminderDecision(
            store,
            rows,
            eligibleItems,
            streak,
            antiSpam,
            throttleState,
            nowMillis,
            reservedFamily,
        )
        if (!decision.shouldSchedule || (reservedFamily != null && decision.family != reservedFamily)) {
            return null
        }
        val signature = ReminderThrottlePolicy.signatureFor(1, decision.triggerAtMillis)
        val throttle = throttleDecision(throttleState, streak, signature, antiSpam, nowMillis, dailyTimeOverride)
        return PlannedReminder(
            copy = ReminderCopyPolicy.ReminderCopy(decision.title, decision.body),
            family = (decision.family ?: ReminderFamily.DUE).name,
            signature = signature,
            triggerAtMillis = decision.triggerAtMillis,
            reviewBatch = false,
            dailyTimeOverride = dailyTimeOverride,
            throttleDecision = throttle,
        )
    }

    private fun throttleDecision(
        throttleState: LocalStoreBase.ReminderThrottleState,
        streak: dev.bee.kanjianki.data.StudyStatsStore.StudyStreak,
        signature: String,
        antiSpam: LocalStoreBase.ReminderAntiSpamSettings,
        nowMillis: Long,
        dailyTimeOverride: Boolean,
    ): ReminderThrottlePolicy.Decision {
        return ReminderThrottlePolicy.decide(
            ReminderThrottlePolicy.Request(
                nowMillis = nowMillis,
                lastPostedAtMillis = throttleState.lastPostedAtMillis,
                lastPostedSignature = throttleState.lastPostedSignature,
                currentSignature = signature,
                lastReviewAtMillis = streak.lastStudyAtMillis,
                dailyTimeOverride = dailyTimeOverride,
            ),
        )
    }

    private fun reviewNotificationsToday(
        store: LocalStore,
        antiSpam: LocalStoreBase.ReminderAntiSpamSettings,
        nowMillis: Long,
        reserveOriginalPost: Boolean = false,
    ): Int {
        // Feed the batch policy an effective "already shown" count that also
        // enforces the user's max-per-day setting when it is below the hard cap.
        val rawShown = store.reviewReminderNotificationsToday(nowMillis)
        val shown = if (reserveOriginalPost) (rawShown - 1).coerceAtLeast(0) else rawShown
        val allowed = antiSpam.maxRemindersPerDay
        // If the user allows fewer than the batch policy's own 2/day fuse, bump the
        // reported count so the batch stops sooner.
        return if (shown >= allowed) maxOf(shown, ReminderReviewBatchPolicy.MAX_NOTIFICATIONS_PER_DAY) else shown
    }

    private fun dailyReminderDecision(
        store: LocalStore,
        rows: List<RecordsImportModels.DashboardRow>,
        eligibleItems: List<RecordsStudyModels.StudyItem>,
        streak: dev.bee.kanjianki.data.StudyStatsStore.StudyStreak,
        antiSpam: LocalStoreBase.ReminderAntiSpamSettings,
        throttleState: LocalStoreBase.ReminderThrottleState,
        nowMillis: Long,
        reservedFamily: ReminderFamily? = null,
    ): dev.bee.kanjianki.core.DailyReminderDecision {
        val plan = dailyStudyPlan(rows, eligibleItems, streak, store.latestSuccessfulSyncFinishedAt(), nowMillis)
        val nowMinuteOfDay = minuteOfDay(nowMillis)
        val quietLead = ReminderAntiSpamPolicy.quietLeadMinutesUntilStart(
            nowMinuteOfDay,
            antiSpam.quietStartMinuteOfDay,
            antiSpam.quietEndMinuteOfDay,
        )
        val dismissed = parseDismissedFamilies(throttleState.dismissedFamilies)
        return DailyReminderDecisionPolicy.decide(
            DailyReminderDecisionRequest(
                plan = plan,
                nowMillis = nowMillis,
                quietHoursStartMinuteOfDay = if (quietLead != null) antiSpam.quietStartMinuteOfDay else null,
                quietHoursLeadMinutes = quietLead ?: 60,
                dismissedFamiliesToday = dismissed,
                dueRemindersShownToday = reservedPostCount(
                    throttleState.dueShownToday,
                    ReminderFamily.DUE,
                    reservedFamily,
                ),
                streakRemindersShownToday = reservedPostCount(
                    throttleState.streakShownToday,
                    ReminderFamily.STREAK,
                    reservedFamily,
                ),
                syncRemindersShownToday = reservedPostCount(
                    throttleState.syncShownToday,
                    ReminderFamily.SYNC,
                    reservedFamily,
                ),
                dueReminderCapPerDay = antiSpam.maxRemindersPerDay,
            ),
        )
    }

    private fun reservedPostCount(
        shownToday: Int,
        family: ReminderFamily,
        reservedFamily: ReminderFamily?,
    ): Int {
        return if (family == reservedFamily) (shownToday - 1).coerceAtLeast(0) else shownToday
    }

    private fun dailyStudyPlan(
        rows: List<RecordsImportModels.DashboardRow>,
        eligibleItems: List<RecordsStudyModels.StudyItem>,
        streak: dev.bee.kanjianki.data.StudyStatsStore.StudyStreak,
        lastSuccessfulSyncAtMillis: Long?,
        nowMillis: Long,
    ): DailyStudyPlan {
        return DailyStudyPlanPolicy.plan(
            DailyStudyPlanRequest(
                nowMillis = nowMillis,
                dueAtMillis = eligibleItems.map { it.dueAtMillis },
                studiedToday = streak.studiedToday,
                streak = StudyStreakPolicy.Streak(
                    currentDays = streak.currentDays,
                    bestDays = streak.bestDays,
                    studiedToday = streak.studiedToday,
                    reviewsToday = streak.reviewsToday,
                    lastStudyAtMillis = streak.lastStudyAtMillis,
                ),
                newProblemKanjiAvailable = if (rows.isEmpty()) 0 else eligibleItems.count { it.totalReviews == 0 },
                lastSuccessfulSyncAtMillis = lastSuccessfulSyncAtMillis,
            ),
        )
    }

    private fun parseDismissedFamilies(raw: String): Set<ReminderFamily> {
        if (raw.isBlank()) {
            return emptySet()
        }
        val out = HashSet<ReminderFamily>()
        for (token in raw.split(',')) {
            val name = token.trim()
            if (name.isEmpty()) {
                continue
            }
            runCatching { ReminderFamily.valueOf(name) }.getOrNull()?.let { out.add(it) }
        }
        return out
    }

    private fun reminderFamilyOrNull(raw: String): ReminderFamily? {
        return runCatching { ReminderFamily.valueOf(raw.trim()) }.getOrNull()
    }

    private fun isDailyReminderTime(settings: LocalStoreBase.ReminderSettings, nowMillis: Long): Boolean {
        val nowMinute = minuteOfDay(nowMillis)
        return nowMinute >= settings.hour * 60 + settings.minute
    }

    private fun minuteOfDay(nowMillis: Long): Int {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = nowMillis
        return calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
    }

    private fun eligibleReminderItems(
        store: LocalStore,
        rows: List<RecordsImportModels.DashboardRow>,
    ): List<RecordsStudyModels.StudyItem> {
        return ReminderEligibilityPolicy.eligibleReminderItems(
            store.studyItems(),
            rows,
            store.studyLadderSettings(),
        )
    }
}
