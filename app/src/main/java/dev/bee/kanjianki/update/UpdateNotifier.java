package dev.bee.kanjianki.update;

import android.Manifest;
import android.annotation.SuppressLint;
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

final class UpdateNotifier {
    private static final String CHANNEL_ID = "kani_app_updates";
    private static final int REQUEST_CODE = 2801;
    private static final int NOTIFICATION_ID = 2802;

    private UpdateNotifier() {
    }

    @SuppressLint("MissingPermission")
    static boolean showPendingUpdate(Context context, String version, String message) {
        if (!notificationsAllowed(context)) {
            return false;
        }
        ensureChannel(context);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return false;
        }
        String title = "Kani update ready";
        String body = version == null || version.isEmpty()
                ? message
                : "Version " + version.replaceFirst("^v", "") + " is verified and ready.";
        if (body == null || body.trim().isEmpty()) {
            body = "Open Kani to finish installing the verified update.";
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
        return true;
    }

    private static boolean notificationsAllowed(Context context) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        return manager != null && manager.areNotificationsEnabled();
    }

    private static void ensureChannel(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
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
}
