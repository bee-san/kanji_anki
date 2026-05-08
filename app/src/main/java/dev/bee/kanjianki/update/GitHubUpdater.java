package dev.bee.kanjianki.update;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import dev.bee.kanjianki.BuildConfig;
import dev.bee.kanjianki.core.GitHubReleaseParser;
import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.data.LocalStore;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

public final class GitHubUpdater {
    private static final String API_BASE = "https://api.github.com/repos/";

    private final Context context;

    public GitHubUpdater(Context context) {
        this.context = context.getApplicationContext();
    }

    public UpdateResult checkDownloadAndInstall(UpdateSource source) {
        long checkedAt = System.currentTimeMillis();
        try {
            String api = API_BASE + BuildConfig.RELEASE_OWNER + "/" + BuildConfig.RELEASE_REPO + "/releases/latest";
            String json = getText(api);
            Records.ReleaseInfo latest = GitHubReleaseParser.parseLatest(json);
            if (!GitHubReleaseParser.isNewerSemver(BuildConfig.VERSION_NAME, latest.tagName)) {
                return recordResult(
                        checkedAt,
                        new UpdateResult(false, "Already on " + BuildConfig.VERSION_NAME + ".", null, false, false),
                        latest.tagName,
                        "",
                        ""
                );
            }

            UpdatePolicy.AssetSelection assets = UpdatePolicy.selectAssets(latest);
            if (!assets.ok) {
                return recordResult(checkedAt, UpdateResult.failed(assets.message), latest.tagName, "", "");
            }

            String expected = GitHubReleaseParser.parseSha256(getText(assets.checksum.downloadUrl));
            UpdatePolicy.ValidationResult expectedDigest = UpdatePolicy.validateExpectedChecksum(expected);
            if (!expectedDigest.ok) {
                return recordResult(checkedAt, UpdateResult.failed(expectedDigest.message), latest.tagName, "", "");
            }

            String safeApkName = safeFileName(assets.apk.name);
            File apkFile = cachedApkFile(safeApkName);
            download(assets.apk.downloadUrl, apkFile);

            UpdatePolicy.ValidationResult checksum = UpdatePolicy.validateChecksum(expected, sha256(apkFile));
            if (!checksum.ok) {
                apkFile.delete();
                return recordResult(checkedAt, UpdateResult.failed(checksum.message), latest.tagName, "", "");
            }

            ApkMetadata metadata = inspectApk(apkFile);
            UpdatePolicy.ValidationResult archive = UpdatePolicy.validatePackageMetadata(
                    context.getPackageName(),
                    BuildConfig.VERSION_NAME,
                    latest.tagName,
                    metadata.packageName,
                    metadata.versionName
            );
            if (!archive.ok) {
                apkFile.delete();
                return recordResult(checkedAt, UpdateResult.failed(archive.message), latest.tagName, "", "");
            }

            return installVerifiedApk(checkedAt, latest.tagName, apkFile, source);
        } catch (Exception error) {
            return recordResult(checkedAt, UpdateResult.failed("Update check failed: " + readableMessage(error)), "", "", "");
        }
    }

    public UpdateResult installCachedPendingUpdate(UpdateSource source) {
        long checkedAt = System.currentTimeMillis();
        LocalStore.AutoUpdateStatus status;
        LocalStore store = new LocalStore(context);
        try {
            status = store.autoUpdateStatus();
        } finally {
            store.close();
        }
        try {
            if (!status.hasPendingUpdate()) {
                return recordResult(checkedAt, UpdateResult.failed("No verified APK is waiting to install."), status.lastVersion, "", "");
            }
            File apkFile = cachedApkFile(status.pendingApkName);
            if (!apkFile.isFile()) {
                return recordResult(checkedAt, UpdateResult.failed("Verified APK cache is missing. Check again to download it."), status.lastVersion, "", "");
            }
            ApkMetadata metadata = inspectApk(apkFile);
            UpdatePolicy.ValidationResult archive = UpdatePolicy.validatePackageMetadata(
                    context.getPackageName(),
                    BuildConfig.VERSION_NAME,
                    status.lastVersion,
                    metadata.packageName,
                    metadata.versionName
            );
            if (!archive.ok) {
                apkFile.delete();
                return recordResult(checkedAt, UpdateResult.failed(archive.message), status.lastVersion, "", "");
            }
            return installVerifiedApk(checkedAt, status.lastVersion, apkFile, source);
        } catch (Exception error) {
            return recordResult(checkedAt, UpdateResult.failed("Update install failed: " + readableMessage(error)), status.lastVersion, status.pendingApkName, status.pendingMessage);
        }
    }

    public static String readableMessage(Throwable error) {
        if (error == null) {
            return "unknown error";
        }
        String message = error.getMessage();
        if (message != null && !message.trim().isEmpty()) {
            return message;
        }
        return error.getClass().getSimpleName();
    }

    private UpdateResult installVerifiedApk(long checkedAt, String version, File apkFile, UpdateSource source) throws Exception {
        if (!context.getPackageManager().canRequestPackageInstalls()) {
            Intent permission = installPermissionIntent(context);
            String message = "APK verified. Grant install permission to continue.";
            UpdateResult result = new UpdateResult(true, message, permission, true, false);
            notifyIfAutomatic(source, version, message);
            return recordResult(checkedAt, result, version, apkFile.getName(), message);
        }

        startPackageInstaller(apkFile, version, source);
        String message = "APK verified. Android installer started.";
        return recordResult(checkedAt, new UpdateResult(true, message, null, false, false), version, apkFile.getName(), "");
    }

    private void notifyIfAutomatic(UpdateSource source, String version, String message) {
        if (source == UpdateSource.AUTOMATIC) {
            UpdateNotifier.showPendingUpdate(context, version, message);
        }
    }

    private UpdateResult recordResult(long checkedAt, UpdateResult result, String version, String pendingApkName, String pendingMessage) {
        try (LocalStore store = new LocalStore(context)) {
            store.recordAutoUpdateResult(checkedAt, result.message, version, pendingApkName, pendingMessage);
        }
        return result;
    }

    @SuppressLint("NewApi")
    private void startPackageInstaller(File apkFile, String version, UpdateSource source) throws Exception {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        }

        int sessionId = installer.createSession(params);
        PackageInstaller.Session session = null;
        boolean committed = false;
        try {
            session = installer.openSession(sessionId);
            try (InputStream input = new BufferedInputStream(new java.io.FileInputStream(apkFile));
                 OutputStream output = session.openWrite("kani-update.apk", 0, apkFile.length())) {
                byte[] buffer = new byte[32_768];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                }
                session.fsync(output);
            }
            Intent callback = PackageInstallStatusReceiver.callbackIntent(context, apkFile.getName(), version, source);
            PendingIntent pending = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    callback,
                    installStatusPendingIntentFlags()
            );
            session.commit(pending.getIntentSender());
            committed = true;
        } finally {
            if (session != null) {
                session.close();
            }
            if (!committed) {
                installer.abandonSession(sessionId);
            }
        }
    }

    static int installStatusPendingIntentFlags() {
        // PackageInstaller adds status extras to this callback intent after commit().
        return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE;
    }

    private ApkMetadata inspectApk(File apkFile) {
        @SuppressWarnings("deprecation")
        PackageInfo info = context.getPackageManager().getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
        if (info == null) {
            return new ApkMetadata("", "");
        }
        return new ApkMetadata(info.packageName == null ? "" : info.packageName, info.versionName == null ? "" : info.versionName);
    }

    private File cachedApkFile(String name) throws IOException {
        File updates = new File(context.getCacheDir(), "updates");
        if (!updates.exists() && !updates.mkdirs()) {
            throw new IOException("Could not create update cache.");
        }
        return new File(updates, safeFileName(name));
    }

    private static String safeFileName(String name) {
        String safe = new File(name == null ? "" : name).getName();
        return safe.isEmpty() ? "kani-update.apk" : safe;
    }

    public static Intent installPermissionIntent(Context context) {
        return new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:" + context.getPackageName()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private static String getText(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestProperty("Accept", "application/vnd.github+json,text/plain,*/*");
        connection.setRequestProperty("User-Agent", "Kani/" + BuildConfig.VERSION_NAME);
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(20_000);
        try {
            requireSuccess(connection, "fetch " + url);
            return readText(connection.getInputStream());
        } finally {
            connection.disconnect();
        }
    }

    private static void download(String url, File file) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestProperty("User-Agent", "Kani/" + BuildConfig.VERSION_NAME);
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(60_000);
        try {
            requireSuccess(connection, "download " + url);
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream output = new FileOutputStream(file)) {
                byte[] buffer = new byte[32_768];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void requireSuccess(HttpURLConnection connection, String action) throws IOException {
        int status = connection.getResponseCode();
        if (status >= 200 && status < 300) {
            return;
        }
        String body = "";
        InputStream error = connection.getErrorStream();
        if (error != null) {
            body = readText(error).replace('\n', ' ').trim();
            if (body.length() > 160) {
                body = body.substring(0, 160);
            }
        }
        String suffix = body.isEmpty() ? "" : ": " + body;
        throw new IOException("HTTP " + status + " while trying to " + action + suffix);
    }

    private static String readText(InputStream stream) throws IOException {
        try (InputStream input = new BufferedInputStream(stream)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[32_768];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toString("UTF-8");
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new BufferedInputStream(new java.io.FileInputStream(file))) {
            byte[] buffer = new byte[32_768];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder out = new StringBuilder();
        for (byte b : digest.digest()) {
            out.append(String.format(Locale.ROOT, "%02x", b));
        }
        return out.toString();
    }

    public enum UpdateSource {
        MANUAL,
        AUTOMATIC,
        CACHED
    }

    public static final class UpdateResult {
        public final boolean success;
        public final String message;
        public final Intent intent;
        public final boolean needsInstallPermission;
        public final boolean retryable;

        private UpdateResult(boolean success, String message, Intent intent, boolean needsInstallPermission, boolean retryable) {
            this.success = success;
            this.message = message;
            this.intent = intent;
            this.needsInstallPermission = needsInstallPermission;
            this.retryable = retryable;
        }

        private static UpdateResult failed(String message) {
            return new UpdateResult(false, message, null, false, false);
        }
    }

    private static final class ApkMetadata {
        final String packageName;
        final String versionName;

        private ApkMetadata(String packageName, String versionName) {
            this.packageName = packageName;
            this.versionName = versionName;
        }
    }
}
