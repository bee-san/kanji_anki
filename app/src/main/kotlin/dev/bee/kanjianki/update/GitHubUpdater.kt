package dev.bee.kanjianki.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri
import dev.bee.kanjianki.BuildConfig
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.updatecore.GitHubReleaseMetadataParser
import dev.bee.kanjianki.updatecore.PackageInstallStatusPolicy
import dev.bee.kanjianki.updatecore.ReleaseVersion
import dev.bee.kanjianki.updatecore.Sha256Digest
import dev.bee.kanjianki.updatecore.UpdateArtifactValidator
import dev.bee.kanjianki.updatecore.UpdateCacheFilePolicy
import dev.bee.kanjianki.updatecore.UpdateReleaseAssetSelector
import dev.bee.kanjianki.updatecore.UpdateTextPolicy
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Method
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Locale

class GitHubUpdater @JvmOverloads constructor(
    context: Context,
    private val client: UpdateClient = androidClient(context),
) {
    private val context: Context = context.applicationContext

    fun checkDownloadAndInstall(source: UpdateSource): UpdateResult {
        val checkedAt = System.currentTimeMillis()
        return try {
            val api = "$API_BASE${BuildConfig.RELEASE_OWNER}/${BuildConfig.RELEASE_REPO}/releases/latest"
            val json = client.getText(api)
            val latest = GitHubReleaseMetadataParser.parseLatest(json)
            if (!ReleaseVersion.isNewerSemver(BuildConfig.VERSION_NAME, latest.tagName())) {
                return recordResult(
                    checkedAt,
                    UpdateResult(false, "Already on ${BuildConfig.VERSION_NAME}.", null, false, false),
                    latest.tagName(),
                    "",
                    "",
                )
            }

            val assets = UpdateReleaseAssetSelector.selectAssets(latest)
            if (!assets.ok()) {
                return recordResult(checkedAt, UpdateResult.failed(assets.message()), latest.tagName(), "", "")
            }

            val expected = Sha256Digest.findInText(client.getText(assets.checksum().downloadUrl()))
            val expectedDigest = UpdateArtifactValidator.validateExpectedChecksum(expected)
            if (!expectedDigest.ok()) {
                return recordResult(checkedAt, UpdateResult.failed(expectedDigest.message()), latest.tagName(), "", "")
            }

            val safeApkName = safeFileName(assets.apk().name())
            val apkFile = cachedApkFile(safeApkName)
            client.download(assets.apk().downloadUrl(), apkFile)

            val checksum = UpdateArtifactValidator.validateChecksum(expected, sha256(apkFile))
            if (!checksum.ok()) {
                deleteCachedApk(apkFile)
                return recordResult(checkedAt, UpdateResult.failed(checksum.message()), latest.tagName(), "", "")
            }

            val metadata = client.inspectApk(apkFile)
            val archive = UpdateArtifactValidator.validatePackageMetadata(
                context.packageName,
                BuildConfig.VERSION_NAME,
                latest.tagName(),
                metadata.packageName,
                metadata.versionName,
            )
            if (!archive.ok()) {
                deleteCachedApk(apkFile)
                return recordResult(checkedAt, UpdateResult.failed(archive.message()), latest.tagName(), "", "")
            }

            installVerifiedApk(checkedAt, latest.tagName(), apkFile, source, metadata.targetSdkVersion)
        } catch (error: IOException) {
            recordResult(checkedAt, UpdateResult.failed("Update check failed: " + readableMessage(error)), "", "", "")
        } catch (error: RuntimeException) {
            recordResult(checkedAt, UpdateResult.failed("Update check failed: " + readableMessage(error)), "", "", "")
        }
    }

    fun installCachedPendingUpdate(source: UpdateSource): UpdateResult {
        val checkedAt = System.currentTimeMillis()
        val status = LocalStore(context).use { store -> store.autoUpdateStatus() }
        return try {
            if (!status.hasPendingUpdate()) {
                return recordResult(
                    checkedAt,
                    UpdateResult.failed("No verified APK is waiting to install."),
                    status.lastVersion,
                    "",
                    "",
                )
            }
            val apkFile = cachedApkFile(status.pendingApkName)
            if (!apkFile.isFile) {
                return recordResult(
                    checkedAt,
                    UpdateResult.failed("Verified APK cache is missing. Check again to download it."),
                    status.lastVersion,
                    "",
                    "",
                )
            }
            val metadata = client.inspectApk(apkFile)
            val archive = UpdateArtifactValidator.validatePackageMetadata(
                context.packageName,
                BuildConfig.VERSION_NAME,
                status.lastVersion,
                metadata.packageName,
                metadata.versionName,
            )
            if (!archive.ok()) {
                deleteCachedApk(apkFile)
                return recordResult(checkedAt, UpdateResult.failed(archive.message()), status.lastVersion, "", "")
            }
            installVerifiedApk(checkedAt, status.lastVersion, apkFile, source, metadata.targetSdkVersion)
        } catch (error: IOException) {
            recordResult(
                checkedAt,
                UpdateResult.failed("Update install failed: " + readableMessage(error)),
                status.lastVersion,
                status.pendingApkName,
                status.pendingMessage,
            )
        } catch (error: RuntimeException) {
            recordResult(
                checkedAt,
                UpdateResult.failed("Update install failed: " + readableMessage(error)),
                status.lastVersion,
                status.pendingApkName,
                status.pendingMessage,
            )
        }
    }

    @Throws(IOException::class)
    private fun installVerifiedApk(
        checkedAt: Long,
        version: String,
        apkFile: File,
        source: UpdateSource,
        targetSdkVersion: Int,
    ): UpdateResult {
        if (!client.canRequestPackageInstalls()) {
            val permission = installPermissionIntent(context)
            val message = "APK verified. Grant install permission to continue."
            val result = UpdateResult(true, message, permission, true, false)
            notifyIfAutomatic(source, version, message)
            return recordResult(checkedAt, result, version, apkFile.name, message)
        }

        client.startPackageInstaller(apkFile, version, source, targetSdkVersion)
        val message = "APK verified. Android installer started."
        return recordResult(checkedAt, UpdateResult(true, message, null, false, false), version, apkFile.name, "")
    }

    private fun notifyIfAutomatic(source: UpdateSource, version: String, message: String) {
        if (source == UpdateSource.AUTOMATIC) {
            client.showPendingUpdate(version, message)
        }
    }

    private fun recordResult(
        checkedAt: Long,
        result: UpdateResult,
        version: String,
        pendingApkName: String,
        pendingMessage: String,
    ): UpdateResult {
        LocalStore(context).use { store ->
            store.recordAutoUpdateResult(checkedAt, result.message, version, pendingApkName, pendingMessage)
        }
        return result
    }

    @Throws(IOException::class)
    private fun cachedApkFile(name: String?): File {
        return cachedApkFile(context.cacheDir, name, DirectoryCreation { dir -> dir.mkdirs() })
    }

    enum class UpdateSource {
        MANUAL,
        AUTOMATIC,
        CACHED,
    }

    class UpdateResult(
        @JvmField val success: Boolean,
        @JvmField val message: String,
        @JvmField val intent: Intent?,
        @JvmField val needsInstallPermission: Boolean,
        @JvmField val retryable: Boolean,
    ) {
        companion object {
            fun failed(message: String?): UpdateResult {
                return UpdateResult(false, message ?: "", null, false, false)
            }
        }
    }

    interface UpdateClient {
        @Throws(IOException::class)
        fun getText(url: String): String

        @Throws(IOException::class)
        fun download(url: String, file: File)

        fun inspectApk(apkFile: File): ApkMetadata

        fun canRequestPackageInstalls(): Boolean

        @Throws(IOException::class)
        fun startPackageInstaller(apkFile: File, version: String, source: UpdateSource, targetSdkVersion: Int)

        fun showPendingUpdate(version: String, message: String): Boolean
    }

    fun interface CacheFileDeletion {
        fun delete(file: File): Boolean
    }

    fun interface DirectoryCreation {
        fun mkdirs(dir: File): Boolean
    }

    interface InstallerBackend {
        @Throws(IOException::class)
        fun createSession(params: PackageInstaller.SessionParams): Int

        @Throws(IOException::class)
        fun openSession(sessionId: Int): InstallerSession

        fun abandonSession(sessionId: Int)
    }

    fun interface InstallerBackendFactory {
        fun create(context: Context): InstallerBackend
    }

    fun interface ArchiveInfoMethodFinder {
        @Throws(ReflectiveOperationException::class)
        fun find(): Method
    }

    fun interface TextFetcher {
        @Throws(IOException::class)
        fun getText(url: String): String
    }

    fun interface FileDownloader {
        @Throws(IOException::class)
        fun download(url: String, file: File)
    }

    interface InstallerSession {
        @Throws(IOException::class)
        fun openWrite(name: String, offsetBytes: Long, lengthBytes: Long): OutputStream

        @Throws(IOException::class)
        fun fsync(output: OutputStream)

        fun commit(statusReceiver: IntentSender)

        fun close()
    }

    interface PackageInstallerAccess {
        @Throws(IOException::class)
        fun createSession(params: PackageInstaller.SessionParams): Int

        @Throws(IOException::class)
        fun openSession(sessionId: Int): InstallerSession

        fun abandonSession(sessionId: Int)
    }

    interface PackageInstallerSessionAccess {
        @Throws(IOException::class)
        fun openWrite(name: String, offsetBytes: Long, lengthBytes: Long): OutputStream

        @Throws(IOException::class)
        fun fsync(output: OutputStream)

        fun commit(statusReceiver: IntentSender)

        fun close()
    }

    fun interface SessionOpenWrite {
        @Throws(IOException::class)
        fun openWrite(name: String, offsetBytes: Long, lengthBytes: Long): OutputStream
    }

    fun interface SessionFsync {
        @Throws(IOException::class)
        fun fsync(output: OutputStream)
    }

    fun interface SessionCommit {
        fun commit(statusReceiver: IntentSender)
    }

    fun interface SessionClose {
        fun close()
    }

    private class AndroidUpdateClient(
        private val context: Context,
        private val installerBackendFactory: InstallerBackendFactory,
        private val textFetcher: TextFetcher,
        private val fileDownloader: FileDownloader,
    ) : UpdateClient {
        @Throws(IOException::class)
        override fun getText(url: String): String {
            return textFetcher.getText(url)
        }

        @Throws(IOException::class)
        override fun download(url: String, file: File) {
            fileDownloader.download(url, file)
        }

        override fun inspectApk(apkFile: File): ApkMetadata {
            val info = packageArchiveInfo(context.packageManager, apkFile.absolutePath)
            return metadataFromPackageInfo(info)
        }

        override fun canRequestPackageInstalls(): Boolean {
            return context.packageManager.canRequestPackageInstalls()
        }

        @Throws(IOException::class)
        override fun startPackageInstaller(
            apkFile: File,
            version: String,
            source: UpdateSource,
            targetSdkVersion: Int,
        ) {
            startPackageInstaller(
                context,
                installerBackendFactory.create(context),
                apkFile,
                version,
                source,
                targetSdkVersion,
            )
        }

        override fun showPendingUpdate(version: String, message: String): Boolean {
            return UpdateNotifier.showPendingUpdate(context, version, message)
        }
    }

    class AndroidInstallerBackend(
        private val installer: PackageInstallerAccess,
    ) : InstallerBackend {
        @Throws(IOException::class)
        override fun createSession(params: PackageInstaller.SessionParams): Int {
            return installer.createSession(params)
        }

        @Throws(IOException::class)
        override fun openSession(sessionId: Int): InstallerSession {
            return installer.openSession(sessionId)
        }

        override fun abandonSession(sessionId: Int) {
            installer.abandonSession(sessionId)
        }
    }

    class AndroidInstallerSession(
        private val session: PackageInstallerSessionAccess,
    ) : InstallerSession {
        @Throws(IOException::class)
        override fun openWrite(name: String, offsetBytes: Long, lengthBytes: Long): OutputStream {
            return session.openWrite(name, offsetBytes, lengthBytes)
        }

        @Throws(IOException::class)
        override fun fsync(output: OutputStream) {
            session.fsync(output)
        }

        override fun commit(statusReceiver: IntentSender) {
            session.commit(statusReceiver)
        }

        override fun close() {
            session.close()
        }
    }

    class ApkMetadata @JvmOverloads constructor(
        @JvmField val packageName: String,
        @JvmField val versionName: String,
        @JvmField val targetSdkVersion: Int = 0,
    )

    private class AndroidPackageInstallerAccess(
        private val installer: PackageInstaller,
    ) : PackageInstallerAccess {
        @Throws(IOException::class)
        override fun createSession(params: PackageInstaller.SessionParams): Int {
            return installer.createSession(params)
        }

        @Throws(IOException::class)
        override fun openSession(sessionId: Int): InstallerSession {
            return installerSession(installer.openSession(sessionId))
        }

        override fun abandonSession(sessionId: Int) {
            installer.abandonSession(sessionId)
        }
    }

    private class AndroidPackageInstallerSessionAccess(
        private val openWrite: SessionOpenWrite,
        private val fsync: SessionFsync,
        private val commit: SessionCommit,
        private val close: SessionClose,
    ) : PackageInstallerSessionAccess {
        @Throws(IOException::class)
        override fun openWrite(name: String, offsetBytes: Long, lengthBytes: Long): OutputStream {
            return openWrite.openWrite(name, offsetBytes, lengthBytes)
        }

        @Throws(IOException::class)
        override fun fsync(output: OutputStream) {
            fsync.fsync(output)
        }

        override fun commit(statusReceiver: IntentSender) {
            commit.commit(statusReceiver)
        }

        override fun close() {
            close.close()
        }
    }

    companion object {
        private const val API_BASE = "https://api.github.com/repos/"
        private const val TAG = "KaniUpdate"
        private const val BUFFER_SIZE = 32_768

        @JvmStatic
        fun readableMessage(error: Throwable): String {
            return UpdateTextPolicy.readableMessage(error)
        }

        @JvmStatic
        fun installStatusPendingIntentFlags(): Int {
            return PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        }

        @JvmStatic
        @Throws(IOException::class)
        fun cachedApkFile(cacheDir: File, name: String?, creation: DirectoryCreation): File {
            val updates = File(cacheDir, "updates")
            if (!updates.exists() && !creation.mkdirs(updates)) {
                throw IOException("Could not create update cache.")
            }
            return File(updates, safeFileName(name))
        }

        private fun deleteCachedApk(apkFile: File?) {
            deleteCachedApk(apkFile, CacheFileDeletion { file -> file.delete() })
        }

        @JvmStatic
        fun deleteCachedApk(apkFile: File?, deletion: CacheFileDeletion): Boolean {
            if (apkFile == null || !apkFile.exists()) {
                return false
            }
            if (!deletion.delete(apkFile)) {
                Log.w(TAG, "Could not delete update cache file: ${apkFile.name}")
                return false
            }
            return true
        }

        @JvmStatic
        fun safeFileName(name: String?): String {
            return UpdateCacheFilePolicy.safeFileName(name)
        }

        @JvmStatic
        fun installPermissionIntent(context: Context): Intent {
            return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData("package:${context.packageName}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun getText(url: String): String {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.setRequestProperty("Accept", "application/vnd.github+json,text/plain,*/*")
            connection.setRequestProperty("User-Agent", "Kani/${BuildConfig.VERSION_NAME}")
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            return try {
                requireSuccess(connection, "fetch $url")
                readText(connection.inputStream)
            } finally {
                connection.disconnect()
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun download(url: String, file: File) {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "Kani/${BuildConfig.VERSION_NAME}")
            connection.connectTimeout = 12_000
            connection.readTimeout = 60_000
            try {
                requireSuccess(connection, "download $url")
                BufferedInputStream(connection.inputStream).use { input ->
                    FileOutputStream(file).use { output ->
                        copy(input, output)
                    }
                }
            } finally {
                connection.disconnect()
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun requireSuccess(connection: HttpURLConnection, action: String) {
            val status = connection.responseCode
            if (status in 200..299) {
                return
            }
            var body = ""
            val error = connection.errorStream
            if (error != null) {
                body = readText(error).replace('\n', ' ').trim()
                if (body.length > 160) {
                    body = body.substring(0, 160)
                }
            }
            val suffix = if (body.isEmpty()) "" else ": $body"
            throw IOException("HTTP $status while trying to $action$suffix")
        }

        @Throws(IOException::class)
        private fun readText(stream: InputStream): String {
            BufferedInputStream(stream).use { input ->
                val output = ByteArrayOutputStream()
                copy(input, output)
                return output.toString("UTF-8")
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun sha256(file: File): String {
            val digest = sha256Digest()
            BufferedInputStream(FileInputStream(file)).use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) {
                        break
                    }
                    digest.update(buffer, 0, read)
                }
            }
            val out = StringBuilder()
            for (byte in digest.digest()) {
                out.append(String.format(Locale.ROOT, "%02x", byte))
            }
            return out.toString()
        }

        private fun sha256Digest(): MessageDigest {
            return try {
                MessageDigest.getInstance("SHA-256")
            } catch (error: NoSuchAlgorithmException) {
                throw IllegalStateException("SHA-256 digest is unavailable.", error)
            }
        }

        @JvmStatic
        fun androidClient(context: Context): UpdateClient {
            return androidClient(context, InstallerBackendFactory { appContext ->
                installerBackend(appContext.packageManager.packageInstaller)
            })
        }

        @JvmStatic
        fun androidClient(context: Context, installerBackendFactory: InstallerBackendFactory): UpdateClient {
            return androidClient(
                context,
                installerBackendFactory,
                TextFetcher { url -> getText(url) },
                FileDownloader { url, file -> download(url, file) },
            )
        }

        @JvmStatic
        fun androidClient(
            context: Context,
            installerBackendFactory: InstallerBackendFactory,
            textFetcher: TextFetcher,
            fileDownloader: FileDownloader,
        ): UpdateClient {
            return AndroidUpdateClient(context.applicationContext, installerBackendFactory, textFetcher, fileDownloader)
        }

        @JvmStatic
        fun metadataFromPackageInfo(info: PackageInfo?): ApkMetadata {
            if (info == null) {
                return ApkMetadata("", "", 0)
            }
            return ApkMetadata(
                info.packageName ?: "",
                info.versionName ?: "",
                info.applicationInfo?.targetSdkVersion ?: 0,
            )
        }

        @JvmStatic
        @Throws(IOException::class)
        fun startPackageInstaller(
            context: Context,
            installer: InstallerBackend,
            apkFile: File,
            version: String,
            source: UpdateSource,
            targetSdkVersion: Int,
        ) {
            val params = sessionParams(context.packageName, targetSdkVersion)
            val sessionId = installer.createSession(params)
            var session: InstallerSession? = null
            var committed = false
            try {
                session = installer.openSession(sessionId)
                BufferedInputStream(FileInputStream(apkFile)).use { input ->
                    session.openWrite("kani-update.apk", 0, apkFile.length()).use { output ->
                        copy(input, output)
                        session.fsync(output)
                    }
                }
                val callback = PackageInstallStatusReceiver.callbackIntent(context, apkFile.name, version, source)
                val pending = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    callback,
                    installStatusPendingIntentFlags(),
                )
                session.commit(pending.intentSender)
                committed = true
            } finally {
                session?.close()
                if (!committed) {
                    installer.abandonSession(sessionId)
                }
            }
        }

        @JvmStatic
        fun sessionParams(packageName: String, targetSdkVersion: Int): PackageInstaller.SessionParams {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                shouldAllowInstallerWithoutExtraUserAction(targetSdkVersion, Build.VERSION.SDK_INT)
            ) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            return params
        }

        @JvmStatic
        fun shouldAllowInstallerWithoutExtraUserAction(requestedSdk: Int, runtimeSdk: Int): Boolean {
            return PackageInstallStatusPolicy.shouldAllowInstallerWithoutExtraUserAction(requestedSdk, runtimeSdk)
        }

        @JvmStatic
        fun packageArchiveInfo(packageManager: PackageManager?, apkPath: String): PackageInfo? {
            return packageArchiveInfo(
                packageManager,
                apkPath,
                ArchiveInfoMethodFinder {
                    PackageManager::class.java.getMethod("getPackageArchiveInfo", String::class.java, Int::class.javaPrimitiveType)
                },
            )
        }

        @JvmStatic
        fun packageArchiveInfo(
            packageManager: PackageManager?,
            apkPath: String,
            methodFinder: ArchiveInfoMethodFinder,
        ): PackageInfo? {
            return try {
                methodFinder.find().invoke(packageManager, apkPath, 0) as PackageInfo?
            } catch (error: ReflectiveOperationException) {
                throw IllegalStateException("Could not inspect APK metadata.", error)
            }
        }

        @JvmStatic
        fun installerBackend(installer: PackageInstaller): InstallerBackend {
            return AndroidInstallerBackend(AndroidPackageInstallerAccess(installer))
        }

        @JvmStatic
        fun installerSession(session: PackageInstaller.Session): InstallerSession {
            return installerSession(
                SessionOpenWrite { name, offsetBytes, lengthBytes -> session.openWrite(name, offsetBytes, lengthBytes) },
                SessionFsync { output -> session.fsync(output) },
                SessionCommit { statusReceiver -> session.commit(statusReceiver) },
                SessionClose { session.close() },
            )
        }

        @JvmStatic
        fun installerSession(
            openWrite: SessionOpenWrite,
            fsync: SessionFsync,
            commit: SessionCommit,
            close: SessionClose,
        ): InstallerSession {
            return AndroidInstallerSession(AndroidPackageInstallerSessionAccess(openWrite, fsync, commit, close))
        }

        @Throws(IOException::class)
        private fun copy(input: InputStream, output: OutputStream) {
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                output.write(buffer, 0, read)
            }
        }
    }
}
