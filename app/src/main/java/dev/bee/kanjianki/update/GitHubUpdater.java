package dev.bee.kanjianki.update;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.RequiresApi;

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
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

public final class GitHubUpdater {
    private static final String API_BASE = "https://api.github.com/repos/";
    private static final String TAG = "KaniUpdate";

    private final Context context;
    private final UpdateClient client;

    public GitHubUpdater(Context context) {
        this(context, androidClient(context));
    }

    GitHubUpdater(Context context, UpdateClient client) {
        this.context = context.getApplicationContext();
        this.client = client;
    }

    public UpdateResult checkDownloadAndInstall(UpdateSource source) {
        long checkedAt = System.currentTimeMillis();
        try {
            String api = API_BASE + BuildConfig.RELEASE_OWNER + "/" + BuildConfig.RELEASE_REPO + "/releases/latest";
            String json = client.getText(api);
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

            String expected = GitHubReleaseParser.parseSha256(client.getText(assets.checksum.downloadUrl));
            UpdatePolicy.ValidationResult expectedDigest = UpdatePolicy.validateExpectedChecksum(expected);
            if (!expectedDigest.ok) {
                return recordResult(checkedAt, UpdateResult.failed(expectedDigest.message), latest.tagName, "", "");
            }

            String safeApkName = safeFileName(assets.apk.name);
            File apkFile = cachedApkFile(safeApkName);
            client.download(assets.apk.downloadUrl, apkFile);

            UpdatePolicy.ValidationResult checksum = UpdatePolicy.validateChecksum(expected, sha256(apkFile));
            if (!checksum.ok) {
                deleteCachedApk(apkFile);
                return recordResult(checkedAt, UpdateResult.failed(checksum.message), latest.tagName, "", "");
            }

            ApkMetadata metadata = client.inspectApk(apkFile);
            UpdatePolicy.ValidationResult archive = UpdatePolicy.validatePackageMetadata(
                    context.getPackageName(),
                    BuildConfig.VERSION_NAME,
                    latest.tagName,
                    metadata.packageName,
                    metadata.versionName
            );
            if (!archive.ok) {
                deleteCachedApk(apkFile);
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
        try (LocalStore store = new LocalStore(context)) {
            status = store.autoUpdateStatus();
        }
        try {
            if (!status.hasPendingUpdate()) {
                return recordResult(checkedAt, UpdateResult.failed("No verified APK is waiting to install."), status.lastVersion, "", "");
            }
            File apkFile = cachedApkFile(status.pendingApkName);
            if (!apkFile.isFile()) {
                return recordResult(checkedAt, UpdateResult.failed("Verified APK cache is missing. Check again to download it."), status.lastVersion, "", "");
            }
            ApkMetadata metadata = client.inspectApk(apkFile);
            UpdatePolicy.ValidationResult archive = UpdatePolicy.validatePackageMetadata(
                    context.getPackageName(),
                    BuildConfig.VERSION_NAME,
                    status.lastVersion,
                    metadata.packageName,
                    metadata.versionName
            );
            if (!archive.ok) {
                deleteCachedApk(apkFile);
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
        if (!client.canRequestPackageInstalls()) {
            Intent permission = installPermissionIntent(context);
            String message = "APK verified. Grant install permission to continue.";
            UpdateResult result = new UpdateResult(true, message, permission, true, false);
            notifyIfAutomatic(source, version, message);
            return recordResult(checkedAt, result, version, apkFile.getName(), message);
        }

        client.startPackageInstaller(apkFile, version, source);
        String message = "APK verified. Android installer started.";
        return recordResult(checkedAt, new UpdateResult(true, message, null, false, false), version, apkFile.getName(), "");
    }

    private void notifyIfAutomatic(UpdateSource source, String version, String message) {
        if (source == UpdateSource.AUTOMATIC) {
            client.showPendingUpdate(version, message);
        }
    }

    private UpdateResult recordResult(long checkedAt, UpdateResult result, String version, String pendingApkName, String pendingMessage) {
        try (LocalStore store = new LocalStore(context)) {
            store.recordAutoUpdateResult(checkedAt, result.message, version, pendingApkName, pendingMessage);
        }
        return result;
    }

    static int installStatusPendingIntentFlags() {
        // PackageInstaller adds status extras to this callback intent after commit().
        return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE;
    }

    private File cachedApkFile(String name) throws IOException {
        return cachedApkFile(context.getCacheDir(), name, File::mkdirs);
    }

    static File cachedApkFile(File cacheDir, String name, DirectoryCreation creation) throws IOException {
        File updates = new File(cacheDir, "updates");
        if (!updates.exists() && !creation.mkdirs(updates)) {
            throw new IOException("Could not create update cache.");
        }
        return new File(updates, safeFileName(name));
    }

    private static void deleteCachedApk(File apkFile) {
        deleteCachedApk(apkFile, File::delete);
    }

    static boolean deleteCachedApk(File apkFile, CacheFileDeletion deletion) {
        if (apkFile == null || !apkFile.exists()) {
            return false;
        }
        if (!deletion.delete(apkFile)) {
            Log.w(TAG, "Could not delete update cache file: " + apkFile.getName());
            return false;
        }
        return true;
    }

    static String safeFileName(String name) {
        String safe = new File(name == null ? "" : name).getName();
        return safe.isEmpty() ? "kani-update.apk" : safe;
    }

    public static Intent installPermissionIntent(Context context) {
        return new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:" + context.getPackageName()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    static String getText(String url) throws Exception {
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

    static void download(String url, File file) throws Exception {
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

    static void requireSuccess(HttpURLConnection connection, String action) throws IOException {
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

    static String sha256(File file) throws Exception {
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

        UpdateResult(boolean success, String message, Intent intent, boolean needsInstallPermission, boolean retryable) {
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

    interface UpdateClient {
        String getText(String url) throws Exception;

        void download(String url, File file) throws Exception;

        ApkMetadata inspectApk(File apkFile);

        boolean canRequestPackageInstalls();

        void startPackageInstaller(File apkFile, String version, UpdateSource source) throws Exception;

        boolean showPendingUpdate(String version, String message);
    }

    interface CacheFileDeletion {
        boolean delete(File file);
    }

    interface DirectoryCreation {
        boolean mkdirs(File dir);
    }

    static UpdateClient androidClient(Context context) {
        return androidClient(context, appContext -> installerBackend(appContext.getPackageManager().getPackageInstaller()));
    }

    static UpdateClient androidClient(Context context, InstallerBackendFactory installerBackendFactory) {
        return androidClient(context, installerBackendFactory, GitHubUpdater::getText, GitHubUpdater::download);
    }

    static UpdateClient androidClient(
            Context context,
            InstallerBackendFactory installerBackendFactory,
            TextFetcher textFetcher,
            FileDownloader fileDownloader
    ) {
        return new AndroidUpdateClient(context.getApplicationContext(), installerBackendFactory, textFetcher, fileDownloader);
    }

    private static final class AndroidUpdateClient implements UpdateClient {
        private final Context context;
        private final InstallerBackendFactory installerBackendFactory;
        private final TextFetcher textFetcher;
        private final FileDownloader fileDownloader;

        private AndroidUpdateClient(
                Context context,
                InstallerBackendFactory installerBackendFactory,
                TextFetcher textFetcher,
                FileDownloader fileDownloader
        ) {
            this.context = context;
            this.installerBackendFactory = installerBackendFactory;
            this.textFetcher = textFetcher;
            this.fileDownloader = fileDownloader;
        }

        @Override
        public String getText(String url) throws Exception {
            return textFetcher.getText(url);
        }

        @Override
        public void download(String url, File file) throws Exception {
            fileDownloader.download(url, file);
        }

        @Override
        public ApkMetadata inspectApk(File apkFile) {
            PackageInfo info = packageArchiveInfo(context.getPackageManager(), apkFile.getAbsolutePath());
            return metadataFromPackageInfo(info);
        }

        @Override
        public boolean canRequestPackageInstalls() {
            return context.getPackageManager().canRequestPackageInstalls();
        }

        @Override
        public void startPackageInstaller(File apkFile, String version, UpdateSource source) throws Exception {
            GitHubUpdater.startPackageInstaller(context, installerBackendFactory.create(context), apkFile, version, source);
        }

        @Override
        public boolean showPendingUpdate(String version, String message) {
            return UpdateNotifier.showPendingUpdate(context, version, message);
        }
    }

    static ApkMetadata metadataFromPackageInfo(PackageInfo info) {
        if (info == null) {
            return new ApkMetadata("", "");
        }
        return new ApkMetadata(info.packageName == null ? "" : info.packageName, info.versionName == null ? "" : info.versionName);
    }

    static void startPackageInstaller(
            Context context,
            InstallerBackend installer,
            File apkFile,
            String version,
            UpdateSource source
    ) throws Exception {
        PackageInstaller.SessionParams params = sessionParams(context.getPackageName(), Build.VERSION.SDK_INT);

        int sessionId = installer.createSession(params);
        InstallerSession session = null;
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

    static PackageInstaller.SessionParams sessionParams(String packageName, int sdkInt) {
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(packageName);
        if (shouldAllowInstallerWithoutExtraUserAction(sdkInt, Build.VERSION.SDK_INT)) {
            allowInstallerWithoutExtraUserAction(params);
        }
        return params;
    }

    static boolean shouldAllowInstallerWithoutExtraUserAction(int requestedSdk, int runtimeSdk) {
        return requestedSdk >= Build.VERSION_CODES.S && runtimeSdk >= Build.VERSION_CODES.S;
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private static void allowInstallerWithoutExtraUserAction(PackageInstaller.SessionParams params) {
        params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
    }

    interface InstallerBackend {
        int createSession(PackageInstaller.SessionParams params) throws IOException;

        InstallerSession openSession(int sessionId) throws IOException;

        void abandonSession(int sessionId);
    }

    interface InstallerBackendFactory {
        InstallerBackend create(Context context);
    }

    static PackageInfo packageArchiveInfo(PackageManager packageManager, String apkPath) {
        return packageArchiveInfo(packageManager, apkPath, () -> PackageManager.class.getMethod("getPackageArchiveInfo", String.class, int.class));
    }

    static PackageInfo packageArchiveInfo(PackageManager packageManager, String apkPath, ArchiveInfoMethodFinder methodFinder) {
        try {
            return (PackageInfo) methodFinder.find().invoke(packageManager, apkPath, 0);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not inspect APK metadata.", error);
        }
    }

    interface ArchiveInfoMethodFinder {
        Method find() throws ReflectiveOperationException;
    }

    interface TextFetcher {
        String getText(String url) throws Exception;
    }

    interface FileDownloader {
        void download(String url, File file) throws Exception;
    }

    interface InstallerSession {
        OutputStream openWrite(String name, long offsetBytes, long lengthBytes) throws IOException;

        void fsync(OutputStream output) throws IOException;

        void commit(IntentSender statusReceiver);

        void close();
    }

    interface PackageInstallerAccess {
        int createSession(PackageInstaller.SessionParams params) throws IOException;

        InstallerSession openSession(int sessionId) throws IOException;

        void abandonSession(int sessionId);
    }

    interface PackageInstallerSessionAccess {
        OutputStream openWrite(String name, long offsetBytes, long lengthBytes) throws IOException;

        void fsync(OutputStream output) throws IOException;

        void commit(IntentSender statusReceiver);

        void close();
    }

    static InstallerBackend installerBackend(PackageInstaller installer) {
        return new AndroidInstallerBackend(new PackageInstallerAccess() {
            @Override
            public int createSession(PackageInstaller.SessionParams params) throws IOException {
                return installer.createSession(params);
            }

            @Override
            public InstallerSession openSession(int sessionId) throws IOException {
                return installerSession(installer.openSession(sessionId));
            }

            @Override
            public void abandonSession(int sessionId) {
                installer.abandonSession(sessionId);
            }
        });
    }

    static InstallerSession installerSession(PackageInstaller.Session session) {
        return installerSession(session::openWrite, session::fsync, session::commit, session::close);
    }

    static InstallerSession installerSession(
            SessionOpenWrite openWrite,
            SessionFsync fsync,
            SessionCommit commit,
            SessionClose close
    ) {
        return new AndroidInstallerSession(new PackageInstallerSessionAccess() {
            @Override
            public OutputStream openWrite(String name, long offsetBytes, long lengthBytes) throws IOException {
                return openWrite.openWrite(name, offsetBytes, lengthBytes);
            }

            @Override
            public void fsync(OutputStream output) throws IOException {
                fsync.fsync(output);
            }

            @Override
            public void commit(IntentSender statusReceiver) {
                commit.commit(statusReceiver);
            }

            @Override
            public void close() {
                close.close();
            }
        });
    }

    interface SessionOpenWrite {
        OutputStream openWrite(String name, long offsetBytes, long lengthBytes) throws IOException;
    }

    interface SessionFsync {
        void fsync(OutputStream output) throws IOException;
    }

    interface SessionCommit {
        void commit(IntentSender statusReceiver);
    }

    interface SessionClose {
        void close();
    }

    static final class AndroidInstallerBackend implements InstallerBackend {
        private final PackageInstallerAccess installer;

        AndroidInstallerBackend(PackageInstallerAccess installer) {
            this.installer = installer;
        }

        @Override
        public int createSession(PackageInstaller.SessionParams params) throws IOException {
            return installer.createSession(params);
        }

        @Override
        public InstallerSession openSession(int sessionId) throws IOException {
            return installer.openSession(sessionId);
        }

        @Override
        public void abandonSession(int sessionId) {
            installer.abandonSession(sessionId);
        }
    }

    static final class AndroidInstallerSession implements InstallerSession {
        private final PackageInstallerSessionAccess session;

        AndroidInstallerSession(PackageInstallerSessionAccess session) {
            this.session = session;
        }

        @Override
        public OutputStream openWrite(String name, long offsetBytes, long lengthBytes) throws IOException {
            return session.openWrite(name, offsetBytes, lengthBytes);
        }

        @Override
        public void fsync(OutputStream output) throws IOException {
            session.fsync(output);
        }

        @Override
        public void commit(IntentSender statusReceiver) {
            session.commit(statusReceiver);
        }

        @Override
        public void close() {
            session.close();
        }
    }

    static final class ApkMetadata {
        final String packageName;
        final String versionName;

        ApkMetadata(String packageName, String versionName) {
            this.packageName = packageName;
            this.versionName = versionName;
        }
    }
}
