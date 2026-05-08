package dev.bee.kanjianki.update;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;

import dev.bee.kanjianki.data.LocalStore;

import java.io.File;

public final class PackageInstallStatusReceiver extends BroadcastReceiver {
    static final String ACTION_INSTALL_STATUS = "dev.bee.kanjianki.action.INSTALL_STATUS";
    private static final String TAG = "KaniUpdate";
    private static final String EXTRA_APK_NAME = "dev.bee.kanjianki.extra.APK_NAME";
    private static final String EXTRA_VERSION = "dev.bee.kanjianki.extra.VERSION";
    private static final String EXTRA_SOURCE = "dev.bee.kanjianki.extra.SOURCE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_INSTALL_STATUS.equals(intent.getAction())) {
            return;
        }
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String apkName = intent.getStringExtra(EXTRA_APK_NAME);
        String version = intent.getStringExtra(EXTRA_VERSION);
        GitHubUpdater.UpdateSource source = sourceFrom(intent.getStringExtra(EXTRA_SOURCE));
        String statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        UpdatePolicy.InstallCallback mapped = UpdatePolicy.mapInstallStatus(status, statusMessage);
        long now = System.currentTimeMillis();

        try (LocalStore store = new LocalStore(context)) {
            if (mapped.pendingUserAction) {
                String message = mapped.message;
                store.recordAutoUpdateResult(now, message, version, apkName, message);
                handlePendingUserAction(context, intent, source, version, message);
            } else {
                deleteCachedApk(context, apkName);
                store.recordAutoUpdateResult(now, mapped.message, version, "", "");
            }
        }
    }

    static Intent callbackIntent(Context context, String apkName, String version, GitHubUpdater.UpdateSource source) {
        return new Intent(context, PackageInstallStatusReceiver.class)
                .setAction(ACTION_INSTALL_STATUS)
                .putExtra(EXTRA_APK_NAME, apkName == null ? "" : apkName)
                .putExtra(EXTRA_VERSION, version == null ? "" : version)
                .putExtra(EXTRA_SOURCE, source == null ? GitHubUpdater.UpdateSource.AUTOMATIC.name() : source.name());
    }

    @SuppressWarnings("deprecation")
    private static void handlePendingUserAction(Context context, Intent intent, GitHubUpdater.UpdateSource source, String version, String message) {
        Intent confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
        if (confirmation != null && UpdatePolicy.shouldLaunchInstallConfirmation(source)) {
            confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(confirmation);
            return;
        }
        UpdateNotifier.showPendingUpdate(context, version, message);
    }

    private static void deleteCachedApk(Context context, String apkName) {
        if (apkName == null || apkName.trim().isEmpty()) {
            return;
        }
        File updatesDir = new File(context.getCacheDir(), "updates");
        File cached = new File(updatesDir, new File(apkName).getName());
        if (cached.isFile() && !cached.delete()) {
            Log.w(TAG, "Could not delete update cache file: " + cached.getName());
        }
    }

    private static GitHubUpdater.UpdateSource sourceFrom(String raw) {
        if (raw == null) {
            return GitHubUpdater.UpdateSource.AUTOMATIC;
        }
        try {
            return GitHubUpdater.UpdateSource.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return GitHubUpdater.UpdateSource.AUTOMATIC;
        }
    }
}
