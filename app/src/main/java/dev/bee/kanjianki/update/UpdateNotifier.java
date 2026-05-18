package dev.bee.kanjianki.update;

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
import dev.bee.kanjianki.updatecore.UpdateNotificationPolicy;
import dev.bee.kanjianki.updatecore.UpdateTextPolicy;

final class UpdateNotifier {
    private static final String CHANNEL_ID = "kani_app_updates";
    private static final String POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS";
    private static final int REQUEST_CODE = 2801;
    private static final int NOTIFICATION_ID = 2802;

    private UpdateNotifier() {
    }

    static boolean showPendingUpdate(Context context, String version, String message) {
        return showPendingUpdate(version, message, androidController(context));
    }

    static boolean showPendingUpdate(String version, String message, NotificationController controller) {
        boolean hasRuntimeNotificationPermission = controller.hasRuntimeNotificationPermission();
        boolean notificationsEnabled = hasRuntimeNotificationPermission && controller.areNotificationsEnabled();
        if (!UpdateNotificationPolicy.shouldShowPendingUpdate(
                hasRuntimeNotificationPermission,
                notificationsEnabled
        )) {
            return false;
        }
        String body = UpdateTextPolicy.notificationBody(version, message);
        controller.ensureChannel();
        controller.notifyUpdate("Kani update ready", body);
        return true;
    }

    interface NotificationController {
        boolean hasRuntimeNotificationPermission();

        boolean areNotificationsEnabled();

        void ensureChannel();

        void notifyUpdate(String title, String body);
    }

    static NotificationController androidController(Context context) {
        return new AndroidNotificationController(context, Build.VERSION.SDK_INT);
    }

    static final class AndroidNotificationController implements NotificationController {
        private final Context context;
        private final int sdkInt;

        AndroidNotificationController(Context context, int sdkInt) {
            this.context = context.getApplicationContext();
            this.sdkInt = sdkInt;
        }

        @Override
        public boolean hasRuntimeNotificationPermission() {
            return sdkInt < 33
                    || context.checkSelfPermission(POST_NOTIFICATIONS_PERMISSION) == PackageManager.PERMISSION_GRANTED;
        }

        @Override
        public boolean areNotificationsEnabled() {
            return notificationsEnabled(manager(), NotificationManager::areNotificationsEnabled);
        }

        @Override
        public void ensureChannel() {
            NotificationManager manager = manager();
            if (manager == null) {
                return;
            }
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "App updates",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Verified Kani APK updates waiting for install confirmation.");
            channel.setShowBadge(true);
            manager.createNotificationChannel(channel);
        }

        @Override
        public void notifyUpdate(String title, String body) {
            NotificationManager manager = manager();
            if (manager == null) {
                return;
            }
            Intent open = new Intent(context, MainActivity.class)
                    .putExtra(MainActivity.EXTRA_OPEN_UPDATE, true)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent contentIntent = PendingIntent.getActivity(
                    context,
                    REQUEST_CODE,
                    open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            Notification notification = new Notification.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new Notification.BigTextStyle().bigText(body))
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true)
                    .setColor(Color.rgb(110, 92, 230))
                    .build();
            manager.notify(NOTIFICATION_ID, notification);
        }

        private NotificationManager manager() {
            return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        }
    }

    static boolean notificationsEnabled(NotificationManager manager, NotificationEnabledCheck check) {
        return manager != null && check.areNotificationsEnabled(manager);
    }

    interface NotificationEnabledCheck {
        boolean areNotificationsEnabled(NotificationManager manager);
    }
}
