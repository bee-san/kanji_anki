package dev.bee.kanjianki.update

import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageInstaller
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.BuildConfig
import dev.bee.kanjianki.updatecore.GitHubReleaseMetadata
import dev.bee.kanjianki.updatecore.PackageInstallStatusPolicy
import dev.bee.kanjianki.updatecore.UpdateArtifactValidator
import dev.bee.kanjianki.updatecore.UpdateReleaseAssetSelector
import dev.bee.kanjianki.updatecore.UpdateTextPolicy
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GitHubUpdaterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context.deleteDatabase("kanji_anki_simple.db")
    }

    @After
    fun tearDown() {
        context.deleteDatabase("kanji_anki_simple.db")
    }
    @Test
    fun readableMessageFallsBackToExceptionClassWhenMessageIsNull() {
        assertEquals("RuntimeException", GitHubUpdater.readableMessage(RuntimeException()))
    }

    @Test
    fun readableMessageKeepsSpecificExceptionMessage() {
        assertEquals("HTTP 403", GitHubUpdater.readableMessage(RuntimeException("HTTP 403")))
    }

    @Test
    fun checkDownloadAndInstallReportsAlreadyOnVersionWhenReleaseMatches() {
        context.deleteDatabase("kanji_anki_simple.db")
        val updater = GitHubUpdater(context, clientReturningText("{\"tag_name\":\"${BuildConfig.VERSION_NAME}\"}"))

        val result = updater.checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL)

        assertFalse(result.success)
        assertEquals(UpdateTextPolicy.alreadyOnVersionMessage(BuildConfig.VERSION_NAME), result.message)
    }

    @Test
    fun checkDownloadAndInstallReportsLocalizedFailureWhenFetchingReleaseFails() {
        context.deleteDatabase("kanji_anki_simple.db")
        val updater = GitHubUpdater(context, failingTextClient(IOException("network down")))

        val result = updater.checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL)

        assertFalse(result.success)
        assertEquals(
            UpdateTextPolicy.updateCheckFailedMessage("network down"),
            result.message,
        )
    }

    @Test
    fun cacheFileNameStripsTraversalAndDefaultsMissingNames() {
        assertEquals("kani-0.4.3.apk", invokeSafeFileName("../release/kani-0.4.3.apk"))
        assertEquals("kani-0.4.3.apk", invokeSafeFileName("../../kani-0.4.3.apk"))
        assertEquals("kani-update.apk", invokeSafeFileName(""))
        assertEquals("kani-update.apk", invokeSafeFileName(null))
    }

    @Test
    fun cachedApkFileCreatesUpdateDirectoryAndReportsCreationFailure() {
        val root = java.nio.file.Files.createTempDirectory("kani-cache-root").toFile()
        val first = GitHubUpdater.cachedApkFile(root, "../release.apk") { dir -> dir.mkdirs() }

        assertEquals("release.apk", first.name)
        assertTrue(first.parentFile?.isDirectory == true)

        val reused = GitHubUpdater.cachedApkFile(root, null) {
            throw AssertionError("Existing update directory should not be recreated.")
        }

        assertEquals("kani-update.apk", reused.name)

        val failingRoot = java.nio.file.Files.createTempDirectory("kani-cache-failure").toFile()
        val failure = try {
            GitHubUpdater.cachedApkFile(failingRoot, "kani.apk") { false }
            throw AssertionError("Expected IOException")
        } catch (caught: IOException) {
            caught
        }

        assertEquals("Could not create update cache.", failure.message)
    }

    @Test
    fun cachedApkDeleteSeamHandlesMissingAndDeletedFiles() {
        val existing = File.createTempFile("kani-delete-", ".apk")
        assertTrue(existing.isFile)

        assertFalse(GitHubUpdater.deleteCachedApk(null) { file -> file.delete() })
        assertFalse(GitHubUpdater.deleteCachedApk(File(existing.parentFile, "missing.apk")) { file -> file.delete() })
        assertTrue(GitHubUpdater.deleteCachedApk(existing) { file -> file.delete() })
        assertFalse(existing.exists())
    }

    @Test
    fun cleanupStaleCachedApksDeletesOnlySelectedFiles() {
        val dir = java.nio.file.Files.createTempDirectory("kani-cache-cleanup").toFile()
        val now = 3_000_000_000L
        val deletedNames = mutableListOf<String>()
        touch(File(dir, "pending.apk"), now - TimeUnit.DAYS.toMillis(30))
        touch(File(dir, "fresh.apk"), now - TimeUnit.HOURS.toMillis(1))
        touch(File(dir, "stale-a.apk"), now - TimeUnit.DAYS.toMillis(30))
        touch(File(dir, "stale-b.APK"), now - TimeUnit.DAYS.toMillis(30))
        touch(File(dir, "notes.txt"), now - TimeUnit.DAYS.toMillis(30))

        val deleted = GitHubUpdater.cleanupStaleCachedApks(dir, "../pending.apk", now) { file ->
            deletedNames += file.name
            true
        }

        assertEquals(2, deleted)
        assertEquals(listOf("stale-a.apk", "stale-b.APK"), deletedNames)
    }

    private fun touch(file: File, lastModified: Long): File {
        assertTrue(file.createNewFile())
        assertTrue(file.setLastModified(lastModified))
        return file
    }

    @Test
    fun sha256ReadsCompleteFileAndReturnsLowercaseDigest() {
        val apk = File.createTempFile("kani-update-large-", ".apk")
        apk.deleteOnExit()
        val content = ByteArray(70_000)
        for (i in content.indices) {
            content[i] = (i * 31 + 7).toByte()
        }
        FileOutputStream(apk).use { output -> output.write(content) }

        val digest = invokeSha256(apk)

        assertEquals(expectedSha256(content), digest)
        assertEquals(digest.lowercase(Locale.ROOT), digest)
        assertTrue(digest.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun failedHttpResponseIncludesTrimmedAndTruncatedBody() {
        val body = "  first line\nsecond line " + repeatText("x", 180) + " tail that should be hidden  "
        val error = assertThrowsIOException(ErrorResponseConnection(503, body))
        val message = error.message ?: ""

        assertTrue(message.startsWith("HTTP 503 while trying to download https://example.invalid/kani.apk: first line second line "))
        assertFalse(message.contains('\n'))
        assertFalse(message.contains("tail that should be hidden"))
        assertEquals("HTTP 503 while trying to download https://example.invalid/kani.apk: ".length + 160, message.length)
    }

    @Test
    fun successfulHttpResponseReturnsWithoutReadingErrorBody() {
        val connection = SuccessResponseConnection()

        GitHubUpdater.requireSuccess(connection, "fetch https://example.invalid/releases/latest")

        assertFalse(connection.errorStreamWasRead())
    }

    @Test
    fun failedHttpResponseWithoutBodyOmitsSuffix() {
        val error = assertThrowsIOException(EmptyErrorResponseConnection(404))

        assertEquals("HTTP 404 while trying to download https://example.invalid/kani.apk", error.message)
    }

    @Test
    fun failedHttpResponseWithShortBodyKeepsBodyWithoutTruncating() {
        val error = assertThrowsIOException(ErrorResponseConnection(429, "  slow down\nretry later  "))

        assertEquals("HTTP 429 while trying to download https://example.invalid/kani.apk: slow down retry later", error.message)
    }

    @Test
    fun informationalAndRedirectHttpStatusesAreRejected() {
        val informational = assertThrowsIOException(EmptyErrorResponseConnection(199))
        val redirect = assertThrowsIOException(EmptyErrorResponseConnection(300))

        assertEquals("HTTP 199 while trying to download https://example.invalid/kani.apk", informational.message)
        assertEquals("HTTP 300 while trying to download https://example.invalid/kani.apk", redirect.message)
    }

    @Test
    fun getTextReadsLocalServerResponseAndSendsExpectedHeaders() {
        val body = "{\"tag_name\":\"v9.9.9\"}".toByteArray(StandardCharsets.UTF_8)
        OneShotHttpServer.start(body).use { server ->
            val text = GitHubUpdater.getText(server.url("/latest"))

            assertEquals("{\"tag_name\":\"v9.9.9\"}", text)
            assertTrue(server.acceptHeader().contains("application/vnd.github+json"))
            assertTrue(server.userAgentHeader().startsWith("Kani/"))
        }
    }

    @Test
    fun getTextPropagatesHttpErrorBodyFromLocalServer() {
        val body = "rate limit\ntry later".toByteArray(StandardCharsets.UTF_8)
        OneShotHttpServer.start(500, "Internal Server Error", body).use { server ->
            val error = try {
                GitHubUpdater.getText(server.url("/latest"))
                throw AssertionError("Expected IOException")
            } catch (caught: IOException) {
                caught
            }

            assertEquals("HTTP 500 while trying to fetch ${server.url("/latest")}: rate limit try later", error.message)
            assertTrue(server.acceptHeader().contains("application/vnd.github+json"))
            assertTrue(server.userAgentHeader().startsWith("Kani/"))
        }
    }

    @Test
    fun downloadStreamsCompleteApkResponseToCacheFile() {
        val body = ByteArray(80_000)
        for (i in body.indices) {
            body[i] = (i * 17 + 3).toByte()
        }
        val target = File.createTempFile("kani-downloaded-", ".apk")
        target.deleteOnExit()
        OneShotHttpServer.start(body).use { server ->
            GitHubUpdater.download(server.url("/kani.apk"), target)

            assertEquals(expectedSha256(body), invokeSha256(target))
            assertTrue(server.userAgentHeader().startsWith("Kani/"))
        }
    }

    @Test
    fun downloadAbortsAndDeletesFileWhenBodyExceedsMaxSize() {
        val body = ByteArray(80_000)
        val target = File.createTempFile("kani-oversized-", ".apk")
        target.deleteOnExit()
        OneShotHttpServer.start(body).use { server ->
            val error = try {
                GitHubUpdater.download(server.url("/kani.apk"), target, 1_000L)
                throw AssertionError("Expected IOException for oversized download")
            } catch (caught: IOException) {
                caught
            }
            assertTrue(error.message!!.contains("too large") || error.message!!.contains("exceeded"))
            // The partial/oversized file must not be left filling the cache.
            assertFalse(target.exists())
        }
    }

    @Test
    fun downloadPropagatesHttpErrorBeforeCreatingTargetFile() {
        val target = File.createTempFile("kani-failed-download-", ".apk")
        assertTrue(target.delete())
        val body = "blocked".toByteArray(StandardCharsets.UTF_8)
        OneShotHttpServer.start(403, "Forbidden", body).use { server ->
            val error = try {
                GitHubUpdater.download(server.url("/kani.apk"), target)
                throw AssertionError("Expected IOException")
            } catch (caught: IOException) {
                caught
            }

            assertEquals("HTTP 403 while trying to download ${server.url("/kani.apk")}: blocked", error.message)
            assertFalse(target.exists())
            assertTrue(server.userAgentHeader().startsWith("Kani/"))
        }
    }

    @Test
    fun rejectsReleaseWithoutApkAsset() {
        val release = GitHubReleaseMetadata(
            "v0.4.3",
            "https://example/releases/v0.4.3",
            listOf(GitHubReleaseMetadata.ReleaseAsset("kani-android-0.4.3.apk.sha256", "https://example/sha")),
        )

        val selection = UpdateReleaseAssetSelector.selectAssets(release)

        assertFalse(selection.ok())
        assertEquals("Latest release has no APK asset.", selection.message())
    }

    @Test
    fun rejectsReleaseWithoutMatchingChecksumAsset() {
        val release = GitHubReleaseMetadata(
            "v0.4.3",
            "https://example/releases/v0.4.3",
            listOf(
                GitHubReleaseMetadata.ReleaseAsset("kani-android-0.4.3.apk", "https://example/apk"),
                GitHubReleaseMetadata.ReleaseAsset("other.apk.sha256", "https://example/sha"),
            ),
        )

        val selection = UpdateReleaseAssetSelector.selectAssets(release)

        assertFalse(selection.ok())
        assertEquals("Latest release has no SHA-256 checksum asset.", selection.message())
    }

    @Test
    fun rejectsChecksumMismatch() {
        val result = UpdateArtifactValidator.validateChecksum(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        )

        assertFalse(result.ok())
        assertEquals("Checksum mismatch. Install blocked.", result.message())
    }

    @Test
    fun rejectsEmptyChecksumDigestBeforeApkDownload() {
        val result = UpdateArtifactValidator.validateExpectedChecksum("")

        assertFalse(result.ok())
        assertEquals("Checksum asset does not contain a SHA-256 digest.", result.message())
    }

    @Test
    fun installerUserActionPolicyRequiresBothRequestedAndRuntimeApiSupport() {
        assertFalse(GitHubUpdater.shouldAllowInstallerWithoutExtraUserAction(28, 31))
        assertTrue(GitHubUpdater.shouldAllowInstallerWithoutExtraUserAction(29, 31))
        assertFalse(GitHubUpdater.shouldAllowInstallerWithoutExtraUserAction(31, 30))
        assertTrue(GitHubUpdater.shouldAllowInstallerWithoutExtraUserAction(34, 36))
    }

    @Test
    fun archiveMetadataWithNullPackageManagerReturnsNull() {
        // The reflection-based archive lookup was replaced with a direct API call; a
        // null PackageManager now yields null metadata rather than a reflective error.
        assertNull(GitHubUpdater.packageArchiveInfo(null, "missing.apk"))
        assertTrue(GitHubUpdater.signingCertificates(null).isEmpty())
        assertTrue(GitHubUpdater.installedSigningCertificates(null, "dev.bee.kanjianki").isEmpty())
    }

    @Test
    fun acceptsExpectedPackageNameAndNewerVersion() {
        val result = UpdateArtifactValidator.validatePackageMetadata(
            "dev.bee.kanjianki",
            "0.4.2",
            "v0.4.3",
            "dev.bee.kanjianki",
            "0.4.3",
        )

        assertTrue(result.ok())
    }

    @Test
    fun acceptsReleaseTagWithoutVPrefixWhenArchiveVersionMatches() {
        val result = UpdateArtifactValidator.validatePackageMetadata(
            "dev.bee.kanjianki",
            "0.4.2",
            "0.4.3",
            "dev.bee.kanjianki",
            "0.4.3",
        )

        assertTrue(result.ok())
        assertEquals("APK metadata verified.", result.message())
    }

    @Test
    fun rejectsDifferentPackageName() {
        val result = UpdateArtifactValidator.validatePackageMetadata(
            "dev.bee.kanjianki",
            "0.4.2",
            "v0.4.3",
            "dev.bee.other",
            "0.4.3",
        )

        assertFalse(result.ok())
        assertEquals("APK package name is dev.bee.other, expected dev.bee.kanjianki.", result.message())
    }

    @Test
    fun rejectsArchiveVersionThatDoesNotMatchReleaseTag() {
        val result = UpdateArtifactValidator.validatePackageMetadata(
            "dev.bee.kanjianki",
            "0.4.2",
            "v0.4.4",
            "dev.bee.kanjianki",
            "0.4.3",
        )

        assertFalse(result.ok())
        assertEquals("APK version 0.4.3 does not match release v0.4.4.", result.message())
    }

    @Test
    fun mapsPendingUserActionInstallerStatus() {
        val mapped = PackageInstallStatusPolicy.mapInstallStatus(
            PackageInstallStatusPolicy.STATUS_PENDING_USER_ACTION,
            "",
        )

        assertTrue(mapped.pendingUserAction())
        assertFalse(mapped.success())
        assertEquals("Android needs permission to finish installing.", mapped.message())
    }

    @Test
    fun startsInstallConfirmationForManualAndCachedSourcesOnly() {
        assertTrue(PackageInstallStatusPolicy.shouldLaunchInstallConfirmation(GitHubUpdater.UpdateSource.MANUAL.name))
        assertTrue(PackageInstallStatusPolicy.shouldLaunchInstallConfirmation(GitHubUpdater.UpdateSource.CACHED.name))
        val launchesForAutomatic = PackageInstallStatusPolicy.shouldLaunchInstallConfirmation(GitHubUpdater.UpdateSource.AUTOMATIC.name)
        val launchesForMissingSource = PackageInstallStatusPolicy.shouldLaunchInstallConfirmation(null)
        assertFalse(launchesForAutomatic)
        assertFalse(launchesForMissingSource)
    }

    @Test
    fun installerStatusConstantsMatchAndroidPackageInstaller() {
        assertEquals(PackageInstaller.STATUS_SUCCESS, PackageInstallStatusPolicy.STATUS_SUCCESS)
        assertEquals(PackageInstaller.STATUS_PENDING_USER_ACTION, PackageInstallStatusPolicy.STATUS_PENDING_USER_ACTION)
    }

    @Test
    fun packageInstallerStatusReceiverIsMutable() {
        val flags = GitHubUpdater.installStatusPendingIntentFlags()

        assertNotEquals(0, flags and PendingIntent.FLAG_UPDATE_CURRENT)
        assertNotEquals(0, flags and PendingIntent.FLAG_MUTABLE)
        assertEquals(0, flags and PendingIntent.FLAG_IMMUTABLE)
    }

    private fun invokeSafeFileName(name: String?): String = GitHubUpdater.safeFileName(name)

    private fun invokeSha256(file: File): String = GitHubUpdater.sha256(file)

    private fun clientReturningText(text: String): GitHubUpdater.UpdateClient {
        return object : GitHubUpdater.UpdateClient {
            override fun getText(url: String): String = text

            override fun download(url: String, file: File) {
                error("download should not be called")
            }

            override fun inspectApk(apkFile: File): GitHubUpdater.ApkMetadata {
                error("inspectApk should not be called")
            }

            override fun installedSigningCertificates(packageName: String): List<ByteArray> =
                error("installedSigningCertificates should not be called")

            override fun canRequestPackageInstalls(): Boolean = error("canRequestPackageInstalls should not be called")

            override fun startPackageInstaller(
                apkFile: File,
                version: String,
                source: GitHubUpdater.UpdateSource,
                targetSdkVersion: Int,
            ) {
                error("startPackageInstaller should not be called")
            }

            override fun showPendingUpdate(version: String, message: String): Boolean =
                error("showPendingUpdate should not be called")
        }
    }

    private fun failingTextClient(ioError: IOException): GitHubUpdater.UpdateClient {
        return object : GitHubUpdater.UpdateClient {
            override fun getText(url: String): String = throw ioError

            override fun download(url: String, file: File) {
                error("download should not be called")
            }

            override fun inspectApk(apkFile: File): GitHubUpdater.ApkMetadata {
                error("inspectApk should not be called")
            }

            override fun installedSigningCertificates(packageName: String): List<ByteArray> =
                error("installedSigningCertificates should not be called")

            override fun canRequestPackageInstalls(): Boolean = error("canRequestPackageInstalls should not be called")

            override fun startPackageInstaller(
                apkFile: File,
                version: String,
                source: GitHubUpdater.UpdateSource,
                targetSdkVersion: Int,
            ) {
                error("startPackageInstaller should not be called")
            }

            override fun showPendingUpdate(version: String, message: String): Boolean =
                error("showPendingUpdate should not be called")
        }
    }

    private fun assertThrowsIOException(connection: HttpURLConnection): IOException {
        try {
            GitHubUpdater.requireSuccess(connection, "download https://example.invalid/kani.apk")
        } catch (error: IOException) {
            return error
        }
        throw AssertionError("Expected IOException")
    }

    private fun expectedSha256(content: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(content)
        val out = StringBuilder()
        for (byte in hash) {
            out.append(String.format(Locale.ROOT, "%02x", byte.toInt() and 0xff))
        }
        return out.toString()
    }

    private fun repeatText(text: String, count: Int): String = buildString(text.length * count) {
        repeat(count) { append(text) }
    }
}

private class SuccessResponseConnection : HttpURLConnection(URL("https://example.invalid/releases/latest")) {
    private var errorStreamRead = false

    override fun getResponseCode(): Int = 204

    override fun getErrorStream(): InputStream {
        errorStreamRead = true
        return ByteArrayInputStream(ByteArray(0))
    }

    fun errorStreamWasRead(): Boolean = errorStreamRead

    override fun disconnect() {
        // Test connection has no external resource to release.
    }

    override fun usingProxy(): Boolean = false

    override fun connect() {
        // Test connection is preconfigured and never opens a socket.
    }
}

private class EmptyErrorResponseConnection(
    private val responseCodeValue: Int,
) : HttpURLConnection(URL("https://example.invalid/releases/latest")) {
    override fun getResponseCode(): Int = responseCodeValue

    override fun getErrorStream(): InputStream? = null

    override fun disconnect() {
        // Test connection has no external resource to release.
    }

    override fun usingProxy(): Boolean = false

    override fun connect() {
        // Test connection is preconfigured and never opens a socket.
    }
}

private class ErrorResponseConnection(
    private val responseCodeValue: Int,
    private val errorBody: String,
) : HttpURLConnection(URL("https://example.invalid/releases/latest")) {
    override fun getResponseCode(): Int = responseCodeValue

    override fun getErrorStream(): InputStream = ByteArrayInputStream(errorBody.toByteArray(StandardCharsets.UTF_8))

    override fun disconnect() {
        // Test connection has no external resource to release.
    }

    override fun usingProxy(): Boolean = false

    override fun connect() {
        // Test connection is preconfigured and never opens a socket.
    }
}

private class OneShotHttpServer(
    private val serverSocket: ServerSocket,
    private val status: Int,
    private val reason: String,
    private val body: ByteArray,
) : AutoCloseable {
    private val acceptHeaderValue = AtomicReference("")
    private val userAgentHeaderValue = AtomicReference("")
    private val thread = Thread({ serve() }, "github-updater-test-http")

    @Volatile
    private var failure: IOException? = null

    init {
        thread.start()
    }

    fun url(path: String): String = "http://127.0.0.1:${serverSocket.localPort}$path"

    @Throws(IOException::class)
    fun acceptHeader(): String {
        rethrowFailure()
        return acceptHeaderValue.get()
    }

    @Throws(IOException::class)
    fun userAgentHeader(): String {
        rethrowFailure()
        return userAgentHeaderValue.get()
    }

    private fun serve() {
        try {
            serverSocket.accept().use { socket ->
                BufferedReader(
                    InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1),
                ).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) {
                            break
                        }
                        if (line.regionMatches(0, "Accept:", 0, "Accept:".length, ignoreCase = true)) {
                            acceptHeaderValue.set(line.substring("Accept:".length).trim())
                        } else if (line.regionMatches(0, "User-Agent:", 0, "User-Agent:".length, ignoreCase = true)) {
                            userAgentHeaderValue.set(line.substring("User-Agent:".length).trim())
                        }
                    }
                    socket.getOutputStream().use { output ->
                        val headers = buildString {
                            append("HTTP/1.1 ")
                            append(status)
                            append(' ')
                            append(reason)
                            append("\r\n")
                            append("Content-Length: ")
                            append(body.size)
                            append("\r\n")
                            append("Connection: close\r\n")
                            append("\r\n")
                        }.toByteArray(StandardCharsets.ISO_8859_1)
                        output.write(headers)
                        output.write(body)
                        output.flush()
                    }
                }
            }
        } catch (error: IOException) {
            if (!serverSocket.isClosed) {
                failure = error
            }
        }
    }

    @Throws(IOException::class)
    private fun rethrowFailure() {
        failure?.let { throw it }
    }

    @Throws(IOException::class)
    override fun close() {
        serverSocket.close()
        try {
            thread.join(1000L)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException(error)
        }
        rethrowFailure()
    }

    companion object {
        fun start(body: ByteArray): OneShotHttpServer = start(200, "OK", body)

        fun start(status: Int, reason: String, body: ByteArray): OneShotHttpServer {
            val socket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
            return OneShotHttpServer(socket, status, reason, body)
        }
    }
}
