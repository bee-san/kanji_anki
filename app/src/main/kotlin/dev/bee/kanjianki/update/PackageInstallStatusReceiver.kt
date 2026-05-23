package dev.bee.kanjianki.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import androidx.core.content.IntentCompat
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.updatecore.PackageInstallStatusPolicy
import java.io.File

class PackageInstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || ACTION_INSTALL_STATUS != intent.action) {
            return
        }
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val apkName = intent.getStringExtra(EXTRA_APK_NAME)
        val version = intent.getStringExtra(EXTRA_VERSION)
        val source = sourceFrom(intent.getStringExtra(EXTRA_SOURCE))
        val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val mapped = PackageInstallStatusPolicy.mapInstallStatus(status, statusMessage)
        val now = System.currentTimeMillis()

        LocalStore(context).use { store ->
            if (mapped.pendingUserAction()) {
                val message = mapped.message()
                store.recordAutoUpdateResult(now, message, version, apkName, message)
                handlePendingUserAction(context, intent, source, version, message)
            } else {
                deleteCachedApk(context, apkName)
                store.recordAutoUpdateResult(now, mapped.message(), version, "", "")
            }
        }
    }

    interface PendingUserActionHandler {
        fun startActivity(intent: Intent)

        fun showPendingUpdate(version: String?, message: String?): Boolean
    }

    fun interface CacheFileDeletion {
        fun delete(file: File): Boolean
    }

    private class AndroidPendingUserActionHandler(context: Context?) : PendingUserActionHandler {
        private val context = context!!.applicationContext

        override fun startActivity(intent: Intent) {
            context.startActivity(intent)
        }

        override fun showPendingUpdate(version: String?, message: String?): Boolean {
            return UpdateNotifier.showPendingUpdate(context, version, message)
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS: String = "dev.bee.kanjianki.action.INSTALL_STATUS"
        private const val TAG = "KaniUpdate"
        private const val EXTRA_APK_NAME = "dev.bee.kanjianki.extra.APK_NAME"
        private const val EXTRA_VERSION = "dev.bee.kanjianki.extra.VERSION"
        private const val EXTRA_SOURCE = "dev.bee.kanjianki.extra.SOURCE"

        @JvmStatic
        fun callbackIntent(
            context: Context?,
            apkName: String?,
            version: String?,
            source: GitHubUpdater.UpdateSource?,
        ): Intent {
            return Intent(context, PackageInstallStatusReceiver::class.java)
                .setAction(ACTION_INSTALL_STATUS)
                .putExtra(EXTRA_APK_NAME, apkName ?: "")
                .putExtra(EXTRA_VERSION, version ?: "")
                .putExtra(EXTRA_SOURCE, (source ?: GitHubUpdater.UpdateSource.AUTOMATIC).name)
        }

        private fun handlePendingUserAction(
            context: Context?,
            intent: Intent?,
            source: GitHubUpdater.UpdateSource?,
            version: String?,
            message: String?,
        ) {
            handlePendingUserAction(intent, source, version, message, androidPendingUserActionHandler(context))
        }

        @JvmStatic
        fun handlePendingUserAction(
            intent: Intent?,
            source: GitHubUpdater.UpdateSource?,
            version: String?,
            message: String?,
            handler: PendingUserActionHandler,
        ) {
            val confirmation = pendingUserAction(intent)
            val sourceName = source?.name
            if (confirmation != null && PackageInstallStatusPolicy.shouldLaunchInstallConfirmation(sourceName)) {
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                handler.startActivity(confirmation)
                return
            }
            handler.showPendingUpdate(version, message)
        }

        private fun pendingUserAction(intent: Intent?): Intent? {
            if (intent == null) {
                return null
            }
            return IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
        }

        private fun deleteCachedApk(context: Context?, apkName: String?) {
            deleteCachedApk(context, apkName, CacheFileDeletion { file -> file.delete() })
        }

        @JvmStatic
        fun deleteCachedApk(context: Context?, apkName: String?, deletion: CacheFileDeletion): Boolean {
            if (apkName == null || apkName.trim().isEmpty()) {
                return false
            }
            val updatesDir = File(context!!.cacheDir, "updates")
            val cached = File(updatesDir, File(apkName).name)
            val cachedFile = cached.isFile
            if (cachedFile && !deletion.delete(cached)) {
                Log.w(TAG, "Could not delete update cache file: " + cached.name)
                return false
            }
            return cachedFile
        }

        @JvmStatic
        fun sourceFrom(raw: String?): GitHubUpdater.UpdateSource {
            return GitHubUpdater.UpdateSource.valueOf(PackageInstallStatusPolicy.sourceNameOrDefault(raw))
        }

        @JvmStatic
        fun androidPendingUserActionHandler(context: Context?): PendingUserActionHandler {
            return AndroidPendingUserActionHandler(context)
        }
    }
}
