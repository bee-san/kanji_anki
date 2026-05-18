package dev.bee.kanjianki.update;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;

import androidx.core.content.IntentCompat;

import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.updatecore.PackageInstallStatusPolicy;

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

    private static void handlePendingUserAction(Context context, Intent intent, GitHubUpdater.UpdateSource source, String version, String message) {
        handlePendingUserAction(intent, source, version, message, androidPendingUserActionHandler(context));
    }

    static void handlePendingUserAction(
            Intent intent,
            GitHubUpdater.UpdateSource source,
            String version,
            String message,
            PendingUserActionHandler handler
    ) {
        Intent confirmation = pendingUserAction(intent);
        if (confirmation != null && UpdatePolicy.shouldLaunchInstallConfirmation(source)) {
            confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            handler.startActivity(confirmation);
            return;
        }
        handler.showPendingUpdate(version, message);
    }

    private static Intent pendingUserAction(Intent intent) {
        if (intent == null) {
            return null;
        }
        return IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent.class);
    }

    private static void deleteCachedApk(Context context, String apkName) {
        deleteCachedApk(context, apkName, File::delete);
    }

    static boolean deleteCachedApk(Context context, String apkName, CacheFileDeletion deletion) {
        if (apkName == null || apkName.trim().isEmpty()) {
            return false;
        }
        File updatesDir = new File(context.getCacheDir(), "updates");
        File cached = new File(updatesDir, new File(apkName).getName());
        boolean cachedFile = cached.isFile();
        if (cachedFile && !deletion.delete(cached)) {
            Log.w(TAG, "Could not delete update cache file: " + cached.getName());
            return false;
        }
        return cachedFile;
    }

    static GitHubUpdater.UpdateSource sourceFrom(String raw) {
        return GitHubUpdater.UpdateSource.valueOf(PackageInstallStatusPolicy.sourceNameOrDefault(raw));
    }

    interface PendingUserActionHandler {
        void startActivity(Intent intent);

        boolean showPendingUpdate(String version, String message);
    }

    interface CacheFileDeletion {
        boolean delete(File file);
    }

    static PendingUserActionHandler androidPendingUserActionHandler(Context context) {
        return new AndroidPendingUserActionHandler(context);
    }

    private static final class AndroidPendingUserActionHandler implements PendingUserActionHandler {
        private final Context context;

        private AndroidPendingUserActionHandler(Context context) {
            this.context = context.getApplicationContext();
        }

        @Override
        public void startActivity(Intent intent) {
            context.startActivity(intent);
        }

        @Override
        public boolean showPendingUpdate(String version, String message) {
            return UpdateNotifier.showPendingUpdate(context, version, message);
        }
    }
}
