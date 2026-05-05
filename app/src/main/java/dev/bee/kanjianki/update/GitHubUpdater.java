package dev.bee.kanjianki.update;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import dev.bee.kanjianki.BuildConfig;
import dev.bee.kanjianki.core.GitHubReleaseParser;
import dev.bee.kanjianki.core.Records;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

public final class GitHubUpdater {
    private final Context context;

    public GitHubUpdater(Context context) {
        this.context = context.getApplicationContext();
    }

    public UpdateResult checkDownloadAndPrepareInstaller() {
        try {
            String api = "https://api.github.com/repos/" + BuildConfig.RELEASE_OWNER + "/" + BuildConfig.RELEASE_REPO + "/releases/latest";
            String json = getText(api);
            Records.ReleaseInfo latest = GitHubReleaseParser.parseLatest(json);
            if (!GitHubReleaseParser.isNewerSemver(BuildConfig.VERSION_NAME, latest.tagName)) {
                return new UpdateResult(false, "Already on " + BuildConfig.VERSION_NAME + ".", null, false);
            }
            Records.ReleaseAsset apk = latest.apkAsset();
            if (apk == null) {
                return new UpdateResult(false, "Latest release has no APK asset.", null, false);
            }
            Records.ReleaseAsset sha = latest.checksumAssetFor(apk.name);
            if (sha == null) {
                return new UpdateResult(false, "Latest release has no SHA-256 checksum asset.", null, false);
            }
            String expected = GitHubReleaseParser.parseSha256(getText(sha.downloadUrl));
            if (expected.isEmpty()) {
                return new UpdateResult(false, "Checksum asset does not contain a SHA-256 digest.", null, false);
            }
            File apkFile = new File(context.getCacheDir(), "updates/" + apk.name);
            File parent = apkFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            download(apk.downloadUrl, apkFile);
            String actual = sha256(apkFile);
            if (!expected.equalsIgnoreCase(actual)) {
                apkFile.delete();
                return new UpdateResult(false, "Checksum mismatch. Install blocked.", null, false);
            }
            if (!context.getPackageManager().canRequestPackageInstalls()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(Uri.parse("package:" + context.getPackageName()))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                return new UpdateResult(true, "APK verified. Grant install permission to continue.", intent, true);
            }
            Intent install = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(ApkContentProvider.uriFor(context, apkFile.getName()), "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            return new UpdateResult(true, "APK verified. Installer is ready.", install, false);
        } catch (Exception error) {
            return new UpdateResult(false, "Update check failed: " + readableMessage(error), null, false);
        }
    }

    static String readableMessage(Throwable error) {
        if (error == null) {
            return "unknown error";
        }
        String message = error.getMessage();
        if (message != null && !message.trim().isEmpty()) {
            return message;
        }
        return error.getClass().getSimpleName();
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

    public static final class UpdateResult {
        public final boolean success;
        public final String message;
        public final Intent intent;
        public final boolean needsInstallPermission;

        private UpdateResult(boolean success, String message, Intent intent, boolean needsInstallPermission) {
            this.success = success;
            this.message = message;
            this.intent = intent;
            this.needsInstallPermission = needsInstallPermission;
        }
    }
}
