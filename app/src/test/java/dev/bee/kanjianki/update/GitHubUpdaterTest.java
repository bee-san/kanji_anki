package dev.bee.kanjianki.update;

import dev.bee.kanjianki.core.RecordsSchedulerModels;
import android.app.PendingIntent;


import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public final class GitHubUpdaterTest {
    @Test
    public void readableMessageFallsBackToExceptionClassWhenMessageIsNull() {
        assertEquals("RuntimeException", GitHubUpdater.readableMessage(new RuntimeException()));
    }

    @Test
    public void readableMessageKeepsSpecificExceptionMessage() {
        assertEquals("HTTP 403", GitHubUpdater.readableMessage(new RuntimeException("HTTP 403")));
    }

    @Test
    public void cacheFileNameStripsTraversalAndDefaultsMissingNames() throws Exception {
        assertEquals("kani-0.4.3.apk", invokeSafeFileName("../release/kani-0.4.3.apk"));
        assertEquals("kani-0.4.3.apk", invokeSafeFileName("../../kani-0.4.3.apk"));
        assertEquals("kani-update.apk", invokeSafeFileName(""));
        assertEquals("kani-update.apk", invokeSafeFileName(null));
    }

    @Test
    public void cachedApkFileCreatesUpdateDirectoryAndReportsCreationFailure() throws Exception {
        File root = java.nio.file.Files.createTempDirectory("kani-cache-root").toFile();
        File first = GitHubUpdater.cachedApkFile(root, "../release.apk", File::mkdirs);

        assertEquals("release.apk", first.getName());
        assertTrue(first.getParentFile().isDirectory());

        File reused = GitHubUpdater.cachedApkFile(root, null, dir -> {
            throw new AssertionError("Existing update directory should not be recreated.");
        });

        assertEquals("kani-update.apk", reused.getName());

        File failingRoot = java.nio.file.Files.createTempDirectory("kani-cache-failure").toFile();
        IOException failure;
        try {
            GitHubUpdater.cachedApkFile(failingRoot, "kani.apk", dir -> false);
            throw new AssertionError("Expected IOException");
        } catch (IOException caught) {
            failure = caught;
        }

        assertEquals("Could not create update cache.", failure.getMessage());
    }

    @Test
    public void cachedApkDeleteSeamHandlesMissingAndDeletedFiles() throws Exception {
        File existing = File.createTempFile("kani-delete-", ".apk");
        assertTrue(existing.isFile());

        assertFalse(GitHubUpdater.deleteCachedApk(null, File::delete));
        assertFalse(GitHubUpdater.deleteCachedApk(new File(existing.getParentFile(), "missing.apk"), File::delete));
        assertTrue(GitHubUpdater.deleteCachedApk(existing, File::delete));
        assertFalse(existing.exists());
    }

    @Test
    public void sha256ReadsCompleteFileAndReturnsLowercaseDigest() throws Exception {
        File apk = File.createTempFile("kani-update-large-", ".apk");
        apk.deleteOnExit();
        byte[] content = new byte[70_000];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i * 31 + 7);
        }
        try (FileOutputStream output = new FileOutputStream(apk)) {
            output.write(content);
        }

        String digest = invokeSha256(apk);

        assertEquals(expectedSha256(content), digest);
        assertEquals(digest.toLowerCase(Locale.ROOT), digest);
        assertTrue(digest.matches("[0-9a-f]{64}"));
    }

    @Test
    public void failedHttpResponseIncludesTrimmedAndTruncatedBody() throws Exception {
        String body = "  first line\nsecond line " + repeat("x", 180) + " tail that should be hidden  ";
        IOException error = assertThrowsIOException(new ErrorResponseConnection(503, body));
        String message = error.getMessage();

        assertTrue(message.startsWith("HTTP 503 while trying to download https://example.invalid/kani.apk: first line second line "));
        assertFalse(message.contains("\n"));
        assertFalse(message.contains("tail that should be hidden"));
        assertEquals(
                "HTTP 503 while trying to download https://example.invalid/kani.apk: ".length() + 160,
                message.length()
        );
    }

    @Test
    public void successfulHttpResponseReturnsWithoutReadingErrorBody() throws Exception {
        SuccessResponseConnection connection = new SuccessResponseConnection();

        GitHubUpdater.requireSuccess(connection, "fetch https://example.invalid/releases/latest");

        assertFalse(connection.errorStreamWasRead());
    }

    @Test
    public void failedHttpResponseWithoutBodyOmitsSuffix() throws Exception {
        IOException error = assertThrowsIOException(new EmptyErrorResponseConnection(404));

        assertEquals("HTTP 404 while trying to download https://example.invalid/kani.apk", error.getMessage());
    }

    @Test
    public void failedHttpResponseWithShortBodyKeepsBodyWithoutTruncating() throws Exception {
        IOException error = assertThrowsIOException(new ErrorResponseConnection(429, "  slow down\nretry later  "));

        assertEquals(
                "HTTP 429 while trying to download https://example.invalid/kani.apk: slow down retry later",
                error.getMessage()
        );
    }

    @Test
    public void informationalAndRedirectHttpStatusesAreRejected() throws Exception {
        IOException informational = assertThrowsIOException(new EmptyErrorResponseConnection(199));
        IOException redirect = assertThrowsIOException(new EmptyErrorResponseConnection(300));

        assertEquals("HTTP 199 while trying to download https://example.invalid/kani.apk", informational.getMessage());
        assertEquals("HTTP 300 while trying to download https://example.invalid/kani.apk", redirect.getMessage());
    }

    @Test
    public void getTextReadsLocalServerResponseAndSendsExpectedHeaders() throws Exception {
        byte[] body = "{\"tag_name\":\"v9.9.9\"}".getBytes(StandardCharsets.UTF_8);
        try (OneShotHttpServer server = OneShotHttpServer.start(body)) {
            String text = GitHubUpdater.getText(server.url("/latest"));

            assertEquals("{\"tag_name\":\"v9.9.9\"}", text);
            assertTrue(server.acceptHeader().contains("application/vnd.github+json"));
            assertTrue(server.userAgentHeader().startsWith("Kani/"));
        }
    }

    @Test
    public void getTextPropagatesHttpErrorBodyFromLocalServer() throws Exception {
        byte[] body = "rate limit\ntry later".getBytes(StandardCharsets.UTF_8);
        try (OneShotHttpServer server = OneShotHttpServer.start(500, "Internal Server Error", body)) {
            IOException error;
            try {
                GitHubUpdater.getText(server.url("/latest"));
                throw new AssertionError("Expected IOException");
            } catch (IOException caught) {
                error = caught;
            }

            assertEquals(
                    "HTTP 500 while trying to fetch " + server.url("/latest") + ": rate limit try later",
                    error.getMessage()
            );
            assertTrue(server.acceptHeader().contains("application/vnd.github+json"));
            assertTrue(server.userAgentHeader().startsWith("Kani/"));
        }
    }

    @Test
    public void downloadStreamsCompleteApkResponseToCacheFile() throws Exception {
        byte[] body = new byte[80_000];
        for (int i = 0; i < body.length; i++) {
            body[i] = (byte) (i * 17 + 3);
        }
        File target = File.createTempFile("kani-downloaded-", ".apk");
        target.deleteOnExit();
        try (OneShotHttpServer server = OneShotHttpServer.start(body)) {
            GitHubUpdater.download(server.url("/kani.apk"), target);

            assertEquals(expectedSha256(body), invokeSha256(target));
            assertTrue(server.userAgentHeader().startsWith("Kani/"));
        }
    }

    @Test
    public void downloadPropagatesHttpErrorBeforeCreatingTargetFile() throws Exception {
        File target = File.createTempFile("kani-failed-download-", ".apk");
        assertTrue(target.delete());
        byte[] body = "blocked".getBytes(StandardCharsets.UTF_8);
        try (OneShotHttpServer server = OneShotHttpServer.start(403, "Forbidden", body)) {
            IOException error;
            try {
                GitHubUpdater.download(server.url("/kani.apk"), target);
                throw new AssertionError("Expected IOException");
            } catch (IOException caught) {
                error = caught;
            }

            assertEquals(
                    "HTTP 403 while trying to download " + server.url("/kani.apk") + ": blocked",
                    error.getMessage()
            );
            assertFalse(target.exists());
            assertTrue(server.userAgentHeader().startsWith("Kani/"));
        }
    }

    @Test
    public void rejectsReleaseWithoutApkAsset() {
        RecordsSchedulerModels.ReleaseInfo release = new RecordsSchedulerModels.ReleaseInfo(
                "v0.4.3",
                "https://example/releases/v0.4.3",
                Collections.singletonList(new RecordsSchedulerModels.ReleaseAsset("kani-android-0.4.3.apk.sha256", "https://example/sha"))
        );

        UpdatePolicy.AssetSelection selection = UpdatePolicy.selectAssets(release);

        assertFalse(selection.ok);
        assertEquals("Latest release has no APK asset.", selection.message);
    }

    @Test
    public void rejectsReleaseWithoutMatchingChecksumAsset() {
        RecordsSchedulerModels.ReleaseInfo release = new RecordsSchedulerModels.ReleaseInfo(
                "v0.4.3",
                "https://example/releases/v0.4.3",
                Arrays.asList(
                        new RecordsSchedulerModels.ReleaseAsset("kani-android-0.4.3.apk", "https://example/apk"),
                        new RecordsSchedulerModels.ReleaseAsset("other.apk.sha256", "https://example/sha")
                )
        );

        UpdatePolicy.AssetSelection selection = UpdatePolicy.selectAssets(release);

        assertFalse(selection.ok);
        assertEquals("Latest release has no SHA-256 checksum asset.", selection.message);
    }

    @Test
    public void rejectsChecksumMismatch() {
        UpdatePolicy.ValidationResult result = UpdatePolicy.validateChecksum(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        );

        assertFalse(result.ok);
        assertEquals("Checksum mismatch. Install blocked.", result.message);
    }

    @Test
    public void rejectsEmptyChecksumDigestBeforeApkDownload() {
        UpdatePolicy.ValidationResult result = UpdatePolicy.validateExpectedChecksum("");

        assertFalse(result.ok);
        assertEquals("Checksum asset does not contain a SHA-256 digest.", result.message);
    }

    @Test
    public void installerUserActionPolicyRequiresBothRequestedAndRuntimeApiSupport() {
        assertFalse(GitHubUpdater.shouldAllowInstallerWithoutExtraUserAction(30, 31));
        assertFalse(GitHubUpdater.shouldAllowInstallerWithoutExtraUserAction(31, 30));
        assertTrue(GitHubUpdater.shouldAllowInstallerWithoutExtraUserAction(31, 31));
    }

    @Test
    public void archiveMetadataReflectionFailureReportsDiagnosticError() {
        IllegalStateException error;
        try {
            GitHubUpdater.packageArchiveInfo(null, "missing.apk", () -> {
                throw new NoSuchMethodException("getPackageArchiveInfo");
            });
            throw new AssertionError("Expected IllegalStateException");
        } catch (IllegalStateException caught) {
            error = caught;
        }

        assertEquals("Could not inspect APK metadata.", error.getMessage());
        assertTrue(error.getCause() instanceof NoSuchMethodException);
    }

    @Test
    public void acceptsExpectedPackageNameAndNewerVersion() {
        UpdatePolicy.ValidationResult result = UpdatePolicy.validatePackageMetadata(
                "dev.bee.kanjianki",
                "0.4.2",
                "v0.4.3",
                "dev.bee.kanjianki",
                "0.4.3"
        );

        assertTrue(result.ok);
    }

    @Test
    public void acceptsReleaseTagWithoutVPrefixWhenArchiveVersionMatches() {
        UpdatePolicy.ValidationResult result = UpdatePolicy.validatePackageMetadata(
                "dev.bee.kanjianki",
                "0.4.2",
                "0.4.3",
                "dev.bee.kanjianki",
                "0.4.3"
        );

        assertTrue(result.ok);
        assertEquals("APK metadata verified.", result.message);
    }

    @Test
    public void rejectsDifferentPackageName() {
        UpdatePolicy.ValidationResult result = UpdatePolicy.validatePackageMetadata(
                "dev.bee.kanjianki",
                "0.4.2",
                "v0.4.3",
                "dev.bee.other",
                "0.4.3"
        );

        assertFalse(result.ok);
        assertEquals("APK package name is dev.bee.other, expected dev.bee.kanjianki.", result.message);
    }

    @Test
    public void rejectsArchiveVersionThatDoesNotMatchReleaseTag() {
        UpdatePolicy.ValidationResult result = UpdatePolicy.validatePackageMetadata(
                "dev.bee.kanjianki",
                "0.4.2",
                "v0.4.4",
                "dev.bee.kanjianki",
                "0.4.3"
        );

        assertFalse(result.ok);
        assertEquals("APK version 0.4.3 does not match release v0.4.4.", result.message);
    }

    @Test
    public void mapsPendingUserActionInstallerStatus() {
        UpdatePolicy.InstallCallback mapped = UpdatePolicy.mapInstallStatus(UpdatePolicy.STATUS_PENDING_USER_ACTION, "");

        assertTrue(mapped.pendingUserAction);
        assertFalse(mapped.success);
        assertEquals("Android needs confirmation to finish installing.", mapped.message);
    }

    @Test
    public void startsInstallConfirmationForManualAndCachedSourcesOnly() {
        assertTrue(UpdatePolicy.shouldLaunchInstallConfirmation(GitHubUpdater.UpdateSource.MANUAL));
        assertTrue(UpdatePolicy.shouldLaunchInstallConfirmation(GitHubUpdater.UpdateSource.CACHED));
        boolean launchesForAutomatic = UpdatePolicy.shouldLaunchInstallConfirmation(GitHubUpdater.UpdateSource.AUTOMATIC);
        boolean launchesForMissingSource = UpdatePolicy.shouldLaunchInstallConfirmation(null);
        assertFalse(launchesForAutomatic);
        assertFalse(launchesForMissingSource);
    }

    @Test
    public void packageInstallerStatusReceiverIsMutable() {
        int flags = GitHubUpdater.installStatusPendingIntentFlags();

        assertNotEquals(0, flags & PendingIntent.FLAG_UPDATE_CURRENT);
        assertNotEquals(0, flags & PendingIntent.FLAG_MUTABLE);
        assertEquals(0, flags & PendingIntent.FLAG_IMMUTABLE);
    }

    @Test
    public void updateNotificationBodyPrefersVerifiedVersionThenMessageFallback() {
        assertEquals(
                "Version 0.4.3 is verified and ready.",
                UpdateNotifier.notificationBody("v0.4.3", "manual message")
        );
        assertEquals("Checksum verified.", UpdateNotifier.notificationBody("", "Checksum verified."));
        assertEquals(
                "Open Kani to finish installing the verified update.",
                UpdateNotifier.notificationBody(null, "  ")
        );
        assertEquals(
                "Open Kani to finish installing the verified update.",
                UpdateNotifier.notificationBody(null, null)
        );
    }

    private static String invokeSafeFileName(String name) throws Exception {
        return GitHubUpdater.safeFileName(name);
    }

    private static String invokeSha256(File file) throws Exception {
        return GitHubUpdater.sha256(file);
    }

    private static IOException assertThrowsIOException(HttpURLConnection connection) throws Exception {
        try {
            GitHubUpdater.requireSuccess(connection, "download https://example.invalid/kani.apk");
        } catch (IOException error) {
            return error;
        }
        throw new AssertionError("Expected IOException");
    }

    private static String expectedSha256(byte[] content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(content);
        StringBuilder out = new StringBuilder();
        for (byte b : hash) {
            out.append(String.format(Locale.ROOT, "%02x", b));
        }
        return out.toString();
    }

    private static String repeat(String text, int count) {
        StringBuilder out = new StringBuilder(text.length() * count);
        for (int i = 0; i < count; i++) {
            out.append(text);
        }
        return out.toString();
    }

    private static final class SuccessResponseConnection extends HttpURLConnection {
        private boolean errorStreamRead;

        private SuccessResponseConnection() throws Exception {
            super(new URL("https://example.invalid/releases/latest"));
        }

        @Override
        public int getResponseCode() {
            return 204;
        }

        @Override
        public InputStream getErrorStream() {
            errorStreamRead = true;
            return new ByteArrayInputStream(new byte[0]);
        }

        private boolean errorStreamWasRead() {
            return errorStreamRead;
        }

        @Override
        public void disconnect() {
            // Test connection has no external resource to release.
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
            // Test connection is preconfigured and never opens a socket.
        }
    }

    private static final class EmptyErrorResponseConnection extends HttpURLConnection {
        private final int responseCode;

        private EmptyErrorResponseConnection(int responseCode) throws Exception {
            super(new URL("https://example.invalid/releases/latest"));
            this.responseCode = responseCode;
        }

        @Override
        public int getResponseCode() {
            return responseCode;
        }

        @Override
        public InputStream getErrorStream() {
            return null;
        }

        @Override
        public void disconnect() {
            // Test connection has no external resource to release.
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
            // Test connection is preconfigured and never opens a socket.
        }
    }

    private static final class OneShotHttpServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final int status;
        private final String reason;
        private final byte[] body;
        private final AtomicReference<String> acceptHeader = new AtomicReference<>("");
        private final AtomicReference<String> userAgentHeader = new AtomicReference<>("");
        private final Thread thread;
        private volatile IOException failure;

        private OneShotHttpServer(ServerSocket serverSocket, int status, String reason, byte[] body) {
            this.serverSocket = serverSocket;
            this.status = status;
            this.reason = reason;
            this.body = body;
            this.thread = new Thread(this::serve, "github-updater-test-http");
        }

        static OneShotHttpServer start(byte[] body) throws IOException {
            return start(200, "OK", body);
        }

        static OneShotHttpServer start(int status, String reason, byte[] body) throws IOException {
            ServerSocket socket = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"));
            OneShotHttpServer server = new OneShotHttpServer(socket, status, reason, body);
            server.thread.start();
            return server;
        }

        String url(String path) {
            return "http://127.0.0.1:" + serverSocket.getLocalPort() + path;
        }

        String acceptHeader() throws IOException {
            rethrowFailure();
            return acceptHeader.get();
        }

        String userAgentHeader() throws IOException {
            rethrowFailure();
            return userAgentHeader.get();
        }

        private void serve() {
            try (Socket socket = serverSocket.accept()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    if (line.regionMatches(true, 0, "Accept:", 0, "Accept:".length())) {
                        acceptHeader.set(line.substring("Accept:".length()).trim());
                    } else if (line.regionMatches(true, 0, "User-Agent:", 0, "User-Agent:".length())) {
                        userAgentHeader.set(line.substring("User-Agent:".length()).trim());
                    }
                }
                OutputStream output = socket.getOutputStream();
                byte[] headers = (
                        "HTTP/1.1 " + status + " " + reason + "\r\n"
                                + "Content-Length: " + body.length + "\r\n"
                                + "Connection: close\r\n"
                                + "\r\n"
                ).getBytes(StandardCharsets.ISO_8859_1);
                output.write(headers);
                output.write(body);
                output.flush();
            } catch (IOException error) {
                if (!serverSocket.isClosed()) {
                    failure = error;
                }
            }
        }

        private void rethrowFailure() throws IOException {
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
            try {
                thread.join(1000L);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IOException(error);
            }
            rethrowFailure();
        }
    }

    private static final class ErrorResponseConnection extends HttpURLConnection {
        private final int responseCode;
        private final String errorBody;

        private ErrorResponseConnection(int responseCode, String errorBody) throws Exception {
            super(new URL("https://example.invalid/kani.apk"));
            this.responseCode = responseCode;
            this.errorBody = errorBody;
        }

        @Override
        public int getResponseCode() {
            return responseCode;
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(errorBody.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void disconnect() {
            // Test connection has no external resource to release.
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
            // Test connection is preconfigured and never opens a socket.
        }
    }
}
