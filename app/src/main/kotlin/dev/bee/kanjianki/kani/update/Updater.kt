package dev.bee.kanjianki.kani.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.content.pm.PackageInfoCompat
import dev.bee.kanjianki.BuildConfig
import dev.bee.kanjianki.updatecore.GitHubReleaseMetadataParser
import dev.bee.kanjianki.updatecore.ReleaseVersion
import dev.bee.kanjianki.updatecore.Sha256Digest
import dev.bee.kanjianki.updatecore.UpdateArtifactValidator
import dev.bee.kanjianki.updatecore.UpdateReleaseAssetSelector
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Kani's self-updater: finds the newest GitHub release (betas included, since every
 * `main` build ships as a prerelease), downloads its APK, checks the published SHA-256
 * and the package, then hands it to [PackageInstaller]. Android rejects an APK signed
 * by another key, so a tampered or foreign build cannot replace the app.
 */
class Updater(context: Context) {
    private val app = context.applicationContext

    data class Release(val version: String, val apkName: String, val apkUrl: String, val checksumUrl: String)

    /** The newest release newer than this build, or null. Blocking: call off the main thread. */
    fun check(): Release? {
        val json = get(RELEASES_URL).decodeToString()
        return GitHubReleaseMetadataParser.objectValues(json)
            .map(GitHubReleaseMetadataParser::parseLatest)
            .filter { ReleaseVersion.isValidSemver(it.tagName()) }
            .filter { ReleaseVersion.isNewerSemver(BuildConfig.VERSION_NAME, it.tagName()) }
            // A release that failed its checks once would fail again: fall back to an older one
            // rather than downloading it on every launch.
            .filter { it.tagName().removePrefix("v") !in rejected() }
            .sortedWith { a, b ->
                when {
                    ReleaseVersion.isNewerSemver(a.tagName(), b.tagName()) -> 1
                    ReleaseVersion.isNewerSemver(b.tagName(), a.tagName()) -> -1
                    else -> 0
                }
            }
            .firstNotNullOfOrNull { release ->
                val assets = UpdateReleaseAssetSelector.selectAssets(release)
                if (!assets.ok()) return@firstNotNullOfOrNull null
                Release(
                    version = release.tagName().removePrefix("v"),
                    apkName = File(assets.apk().name()).name,
                    apkUrl = assets.apk().downloadUrl(),
                    checksumUrl = assets.checksum().downloadUrl(),
                )
            }
    }

    /** Downloads and verifies [release]; returns the APK ready to install. Blocking. */
    fun download(release: Release): File {
        val expected = Sha256Digest.findInText(get(release.checksumUrl).decodeToString())
        UpdateArtifactValidator.validateExpectedChecksum(expected).requireOk()
        val dir = File(app.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.filter { it.name != release.apkName }?.forEach { it.delete() }
        val apk = File(dir, release.apkName)
        if (!apk.isFile || sha256(apk) != expected) {
            val partial = File(dir, release.apkName + ".partial")
            download(release.apkUrl, partial)
            if (!partial.renameTo(apk)) throw IOException("Could not save the update.")
        }
        UpdateArtifactValidator.validateChecksum(expected, sha256(apk)).requireOk {
            apk.delete()
            reject(release)
        }
        val info = app.packageManager.getPackageArchiveInfo(apk.path, 0)
            ?: run { apk.delete(); reject(release); throw IOException("The downloaded update is not an app.") }
        if (info.packageName != app.packageName ||
            PackageInfoCompat.getLongVersionCode(info) <= BuildConfig.VERSION_CODE.toLong()
        ) {
            apk.delete()
            reject(release)
            throw IOException("The downloaded update is not a newer Kani.")
        }
        return apk
    }

    private val prefs get() = app.getSharedPreferences(InstallReceiver.PREFS, Context.MODE_PRIVATE)

    /** Versions whose APK failed its checks (checksum, package or version). */
    fun rejected(): Set<String> = prefs.getStringSet(KEY_REJECTED, null).orEmpty()

    private fun reject(release: Release) {
        val versions = rejected() + release.version
        prefs.edit { putStringSet(KEY_REJECTED, versions) }
    }

    /** True on a metered network (mobile data): updates then wait for Wi-Fi. */
    fun onMeteredNetwork(): Boolean =
        app.getSystemService(android.net.ConnectivityManager::class.java)?.isActiveNetworkMetered ?: true

    /** True when Android lets Kani install updates; otherwise the user has to allow it once. */
    fun canInstall(): Boolean = app.packageManager.canRequestPackageInstalls()

    /**
     * True when Android installs an update without asking: Android 12+, installs allowed, and
     * Kani installed the current version itself (so the first update from a browser install
     * still needs a tap). Kani then installs updates on its own.
     */
    fun installsSilently(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !canInstall()) return false
        val source = try {
            app.packageManager.getInstallSourceInfo(app.packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            return false
        }
        if (source.installingPackageName != app.packageName) return false
        // Android 14+: a store that claimed the update ownership has every other installer ask.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val owner = source.updateOwnerPackageName
            return owner == null || owner == app.packageName
        }
        return true
    }

    fun allowInstallsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${app.packageName}".toUri())

    /**
     * Starts the install; Android closes Kani to replace it. [auto] marks an install Kani
     * started on its own: it never asks the user, see [InstallReceiver].
     */
    fun install(apk: File, auto: Boolean = false) {
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            try {
                session.openWrite("kani.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
            } catch (error: IOException) {
                // Low storage, say: don't leave a half-written copy staged until Android expires it.
                session.abandon()
                throw error
            }
            val callback = Intent(app, InstallReceiver::class.java).setAction(ACTION_INSTALL_STATUS).putExtra(EXTRA_AUTO, auto)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(app, id, callback, flags).intentSender)
        }
    }

    private fun get(url: String): ByteArray = open(url).use { it.readBytes() }

    private fun download(url: String, target: File) {
        open(url).use { input -> target.outputStream().use { input.copyTo(it) } }
    }

    private fun open(url: String): java.io.InputStream {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/vnd.github+json, */*")
        connection.setRequestProperty("User-Agent", "Kani/${BuildConfig.VERSION_NAME}")
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            throw IOException("GitHub answered $code.")
        }
        return connection.inputStream
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private inline fun UpdateArtifactValidator.ValidationResult.requireOk(onFail: () -> Unit = {}) {
        if (!ok()) {
            onFail()
            throw IOException(message())
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "dev.bee.kanjianki.kani.INSTALL_STATUS"
        const val EXTRA_AUTO = "dev.bee.kanjianki.kani.extra.AUTO_INSTALL"
        private const val TIMEOUT_MS = 30_000
        private const val KEY_REJECTED = "rejected_versions"
        private val RELEASES_URL =
            "https://api.github.com/repos/${BuildConfig.RELEASE_OWNER}/${BuildConfig.RELEASE_REPO}/releases?per_page=20"
    }
}

/**
 * Shows Android's install confirmation when it asks for one, and logs the outcome. An automatic
 * install never asks: Kani is in the background then, so it is dropped and the Today banner
 * stays for a tap.
 */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Updater.ACTION_INSTALL_STATUS) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val auto = intent.getBooleanExtra(Updater.EXTRA_AUTO, false)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION && auto) {
            val id = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
            Log.i(TAG, "Android wants a tap for the update; leaving it to the Today banner.")
            if (id > 0) {
                try {
                    context.packageManager.packageInstaller.abandonSession(id)
                } catch (error: SecurityException) {
                    Log.w(TAG, "Could not drop the automatic install.", error)
                }
            }
            return
        }
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) ?: return
            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(confirm)
            } catch (error: RuntimeException) {
                Log.w(TAG, "Could not show the install confirmation.", error)
            }
            return
        }
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Log.i(TAG, "Install finished: status=$status $message")
        // A failed automatic install stays quiet: the banner still offers the update.
        if (status != PackageInstaller.STATUS_SUCCESS && !auto) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putString(KEY_ERROR, message ?: "Android did not install the update.")
            }
        }
    }

    companion object {
        private const val TAG = "KaniUpdate"
        const val PREFS = "kani_update"
        const val KEY_ERROR = "install_error"

        /** The last failed install's message, cleared once read. */
        fun takeError(context: Context): String? {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val error = prefs.getString(KEY_ERROR, null) ?: return null
            prefs.edit { remove(KEY_ERROR) }
            return error
        }
    }
}
