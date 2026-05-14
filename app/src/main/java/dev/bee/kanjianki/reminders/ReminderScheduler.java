package dev.bee.kanjianki.reminders;

import android.Manifest;
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
import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.data.LocalStore;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public final class ReminderScheduler {
    public static final String ACTION_DAILY_REMINDER = "dev.bee.kanjianki.action.DAILY_REMINDER";
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
        if (settings == null || !settings.enabled) {
            cancel(context);
            return;
        }
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                nextTriggerMillis(settings),
                alarmIntent(context, PendingIntent.FLAG_UPDATE_CURRENT)
        );
    }

    public static void cancel(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = alarmIntent(context, PendingIntent.FLAG_NO_CREATE);
        if (alarmManager != null && pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
        }
    }

    public static boolean notificationsAllowed(Context context) {
        if (!hasRuntimeNotificationPermission(context)) {
            return false;
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || !manager.areNotificationsEnabled()) {
            return false;
        }
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
        return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    public static boolean hasRuntimeNotificationPermission(Context context) {
        return Build.VERSION.SDK_INT < 33
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    public static void ensureNotificationChannel(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
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

    @SuppressLint("MissingPermission")
    public static void showReminderNotification(Context context) {
        if (!notificationsAllowed(context)) {
            return;
        }
        ensureNotificationChannel(context);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        ReminderCopy copy = reminderCopy(context);
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

    private static PendingIntent alarmIntent(Context context, int lookupFlag) {
        Intent intent = new Intent(context, ReminderReceiver.class).setAction(ACTION_DAILY_REMINDER);
        return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                lookupFlag | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static long nextTriggerMillis(LocalStore.ReminderSettings settings) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, settings.hour);
        calendar.set(Calendar.MINUTE, settings.minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long trigger = calendar.getTimeInMillis();
        if (trigger <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
            trigger = calendar.getTimeInMillis();
        }
        return trigger;
    }

    private static ReminderCopy reminderCopy(Context context) {
        try (LocalStore store = new LocalStore(context)) {
            List<Records.DashboardRow> rows = store.activeDashboardRows();
            if (rows.isEmpty()) {
                return new ReminderCopy("Sync Kani", "Sync AnkiDroid to find the kanji your reviews keep exposing.");
            }
            long now = System.currentTimeMillis();
            List<Records.StudyItem> items = store.studyItems();
            Records.AdaptiveLoadPlan plan = new AdaptiveLoadPlanner().plan(
                    rows,
                    items,
                    store.reviewStatsSince(now - 7 * 86_400_000L),
                    store.studyStreak(now).currentDays,
                    store.studiedKanjiSince(startOfLocalDay(now)),
                    store.adaptiveLoadWorkPercent(),
                    store.adaptiveLoadMode(),
                    store.adaptiveLoadMaxItems(),
                    now,
                    Records.Settings.kikuDefaults()
            );
            if (plan.remaining > 0) {
                return new ReminderCopy(
                        "Kani focus is ready",
                        String.format(Locale.ROOT, "%d focus kanji %s left today. Draw one now.", plan.remaining, plan.remaining == 1 ? "is" : "are")
                );
            }
            int due = currentDueCount(rows, items, now);
            if (due > 0) {
                return new ReminderCopy(
                        "Kani recovery is due",
                        String.format(Locale.ROOT, "%d problem kanji %s ready. Draw one now.", due, due == 1 ? "is" : "are")
                );
            }
            return new ReminderCopy("Check Kani", "Your queue can rest today. Open Kani if you want an extra problem kanji rep.");
        }
    }

    private static int currentDueCount(List<Records.DashboardRow> rows, List<Records.StudyItem> items, long now) {
        return new BridgeScheduler().dueCount(items, rows, now);
    }

    private static final class ReminderCopy {
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
