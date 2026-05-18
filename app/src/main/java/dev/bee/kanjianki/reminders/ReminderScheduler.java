package dev.bee.kanjianki.reminders;

import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;

import dev.bee.kanjianki.MainActivity;
import dev.bee.kanjianki.R;
import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.ReminderSchedulePolicy;
import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.time.AppClock;

import java.util.List;
import java.util.Locale;

public final class ReminderScheduler {
    public static final String ACTION_DAILY_REMINDER = "dev.bee.kanjianki.action.DAILY_REMINDER";
    private static final String POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS";
    private static final String CHANNEL_ID = "kani_study_reminders";
    private static final int REQUEST_CODE = 2701;
    private static final int NOTIFICATION_ID = 2702;

    private ReminderScheduler() {
    }

    public static void schedule(Context context) {
        try (LocalStore store = new LocalStore(context)) {
            schedule(context, store.reminderSettings());
        }
    }

    public static void schedule(Context context, LocalStore.ReminderSettings settings) {
        schedule(context, settings, AppClock.systemClock());
    }

    public static void schedule(Context context, LocalStore.ReminderSettings settings, AppClock clock) {
        schedule(settings, androidReminderServices(context), AppClock.orSystem(clock));
    }

    static void schedule(LocalStore.ReminderSettings settings, ReminderServices services, AppClock clock) {
        schedule(settings, services, AppClock.orSystem(clock).nowMillis());
    }

    static void schedule(LocalStore.ReminderSettings settings, ReminderServices services, long nowMillis) {
        if (settings == null || !settings.enabled) {
            services.cancelAlarm();
            return;
        }
        services.scheduleAlarm(nextTriggerMillis(settings, nowMillis));
    }

    public static void cancel(Context context) {
        androidReminderServices(context).cancelAlarm();
    }

    public static boolean notificationsAllowed(Context context) {
        return notificationsAllowed(androidReminderServices(context));
    }

    static boolean notificationsAllowed(ReminderServices services) {
        if (!services.hasRuntimeNotificationPermission()) {
            return false;
        }
        if (!services.areNotificationsEnabled()) {
            return false;
        }
        Integer channelImportance = services.reminderChannelImportance();
        return channelImportance == null || channelImportance != NotificationManager.IMPORTANCE_NONE;
    }

    public static boolean hasRuntimeNotificationPermission(Context context) {
        return hasRuntimeNotificationPermission(context, Build.VERSION.SDK_INT);
    }

    static boolean hasRuntimeNotificationPermission(Context context, int sdkInt) {
        return sdkInt < 33
                || context.checkSelfPermission(POST_NOTIFICATIONS_PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    public static void ensureNotificationChannel(Context context) {
        androidReminderServices(context).ensureNotificationChannel();
    }

    @SuppressLint("MissingPermission")
    public static void showReminderNotification(Context context) {
        showReminderNotification(context, AppClock.systemClock());
    }

    @SuppressLint("MissingPermission")
    public static void showReminderNotification(Context context, AppClock clock) {
        showReminderNotification(context, androidReminderServices(context), clock);
    }

    @SuppressLint("MissingPermission")
    static void showReminderNotification(Context context, ReminderServices services) {
        showReminderNotification(context, services, AppClock.systemClock());
    }

    @SuppressLint("MissingPermission")
    static void showReminderNotification(Context context, ReminderServices services, AppClock clock) {
        if (!notificationsAllowed(services)) {
            return;
        }
        services.ensureNotificationChannel();
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        ReminderCopy copy = reminderCopy(context, clock);
        Intent open = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(copy.title)
                .setContentText(copy.message)
                .setStyle(new Notification.BigTextStyle().bigText(copy.message))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setColor(Color.rgb(255, 76, 118))
                .build();
        manager.notify(NOTIFICATION_ID, notification);
    }

    static ReminderServices androidReminderServices(Context context) {
        return new AndroidReminderServices(context);
    }

    interface ReminderServices {
        void scheduleAlarm(long triggerAtMillis);

        void cancelAlarm();

        boolean hasRuntimeNotificationPermission();

        boolean areNotificationsEnabled();

        Integer reminderChannelImportance();

        void ensureNotificationChannel();
    }

    private static final class AndroidReminderServices implements ReminderServices {
        private final Context context;

        AndroidReminderServices(Context context) {
            this.context = context;
        }

        @Override
        public void scheduleAlarm(long triggerAtMillis) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) {
                return;
            }
            alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    alarmIntent(PendingIntent.FLAG_UPDATE_CURRENT)
            );
        }

        @Override
        public void cancelAlarm() {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            PendingIntent pendingIntent = alarmIntent(PendingIntent.FLAG_NO_CREATE);
            if (alarmManager != null && pendingIntent != null) {
                alarmManager.cancel(pendingIntent);
            }
        }

        private PendingIntent alarmIntent(int lookupFlag) {
            Intent intent = new Intent(context, ReminderReceiver.class).setAction(ACTION_DAILY_REMINDER);
            return PendingIntent.getBroadcast(
                    context,
                    REQUEST_CODE,
                    intent,
                    lookupFlag | PendingIntent.FLAG_IMMUTABLE
            );
        }

        @Override
        public boolean hasRuntimeNotificationPermission() {
            return ReminderScheduler.hasRuntimeNotificationPermission(context);
        }

        @Override
        public boolean areNotificationsEnabled() {
            return ReminderScheduler.areNotificationsEnabled(notificationStatus());
        }

        @Override
        public Integer reminderChannelImportance() {
            NotificationManager manager = notificationManager();
            if (manager == null) {
                return NotificationManager.IMPORTANCE_NONE;
            }
            NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
            return channel == null ? null : channel.getImportance();
        }

        @Override
        public void ensureNotificationChannel() {
            NotificationManager manager = notificationManager();
            if (manager == null) {
                return;
            }
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Study reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Daily reminders to study problem kanji in Kani.");
            channel.setShowBadge(true);
            manager.createNotificationChannel(channel);
        }

        private NotificationManager notificationManager() {
            return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        }

        private NotificationStatus notificationStatus() {
            NotificationManager manager = notificationManager();
            return manager == null ? null : manager::areNotificationsEnabled;
        }
    }

    interface NotificationStatus {
        boolean areNotificationsEnabled();
    }

    static boolean areNotificationsEnabled(NotificationStatus status) {
        return status != null && status.areNotificationsEnabled();
    }

    static long nextTriggerMillis(LocalStore.ReminderSettings settings) {
        return nextTriggerMillis(settings, AppClock.systemClock());
    }

    static long nextTriggerMillis(LocalStore.ReminderSettings settings, AppClock clock) {
        return nextTriggerMillis(settings, AppClock.orSystem(clock).nowMillis());
    }

    static long nextTriggerMillis(LocalStore.ReminderSettings settings, long nowMillis) {
        return ReminderSchedulePolicy.nextTriggerMillis(settings.hour, settings.minute, nowMillis);
    }

    private static ReminderCopy reminderCopy(Context context, AppClock clock) {
        try (LocalStore store = new LocalStore(context)) {
            List<RecordsImportModels.DashboardRow> rows = store.activeDashboardRows();
            List<RecordsStudyModels.StudyItem> items = store.studyItems();
            long now = AppClock.orSystem(clock).nowMillis();
            return reminderCopy(AdaptiveLoadPlanner.PlanRequest.builder(
                            rows,
                            items,
                            store.reviewStatsSince(now - 7 * 86_400_000L),
                            store.studyStreak(now).currentDays,
                            store.studiedKanjiSince(startOfLocalDay(now)),
                            AdaptiveLoadPlanner.WorkloadPolicy.fromSettings(
                                    store.adaptiveLoadWorkPercent(),
                                    store.adaptiveLoadMode(),
                                    store.adaptiveLoadMaxItems()),
                            now
                    )
                    .settings(RecordsSyncModels.Settings.kikuDefaults())
                    .build());
        }
    }

    static ReminderCopy reminderCopy(AdaptiveLoadPlanner.PlanRequest request) {
        if (request.rows().isEmpty()) {
            return new ReminderCopy("Sync Kani", "Sync AnkiDroid to find the kanji your reviews keep exposing.");
        }
        RecordsSchedulerModels.AdaptiveLoadPlan plan = new AdaptiveLoadPlanner().plan(request);
        return reminderCopyFor(plan.remaining, currentDueCount(request.rows(), request.items(), request.nowMillis()));
    }

    static ReminderCopy reminderCopyFor(int focusRemaining, int due) {
        if (focusRemaining > 0) {
            return new ReminderCopy(
                    "Kani focus is ready",
                    String.format(Locale.ROOT, "%d focus kanji %s left today. Draw one now.", focusRemaining, focusRemaining == 1 ? "is" : "are")
            );
        }
        if (due > 0) {
            return new ReminderCopy(
                    "Kani recovery is due",
                    String.format(Locale.ROOT, "%d problem kanji %s ready. Draw one now.", due, due == 1 ? "is" : "are")
            );
        }
        return new ReminderCopy("Check Kani", "Your queue can rest today. Open Kani if you want an extra problem kanji rep.");
    }

    private static int currentDueCount(List<RecordsImportModels.DashboardRow> rows, List<RecordsStudyModels.StudyItem> items, long now) {
        return new BridgeScheduler().dueCount(items, rows, now);
    }

    static final class ReminderCopy {
        final String title;
        final String message;

        ReminderCopy(String title, String message) {
            this.title = title;
            this.message = message;
        }
    }

    private static long startOfLocalDay(long nowMillis) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(nowMillis);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}
