package dev.bee.kanjianki.update

import android.util.Log
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.updatecore.ResumeInstallPolicy
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Installs a verified pending update when the app returns to the foreground,
 * for example right after the user grants the "install unknown apps"
 * permission from the in-app prompt and comes back from Android settings.
 */
class ResumeUpdateInstaller(
    private val installPermissionCheck: InstallPermissionCheck,
    private val statusReader: StatusReader,
    private val background: Executor,
    private val pendingUpdateInstall: PendingUpdateInstall,
) {
    private val installAttemptInFlight = AtomicBoolean(false)

    fun onResume() {
        // Cheap in-flight guard on the caller (main) thread; everything that touches
        // the database (status read) or the package manager runs on the background
        // executor so onResume never does blocking I/O on the UI thread (an ANR risk,
        // especially on a freshly launched process whose DB is still warming up).
        if (!installAttemptInFlight.compareAndSet(false, true)) {
            return
        }
        try {
            background.execute {
                try {
                    val status = statusReader.autoUpdateStatus()
                    if (ResumeInstallPolicy.shouldInstall(
                            status.enabled,
                            installPermissionCheck.canRequestPackageInstalls(),
                            status.hasPendingUpdate(),
                            false,
                        )
                    ) {
                        pendingUpdateInstall.installCachedPendingUpdate()
                    }
                } finally {
                    installAttemptInFlight.set(false)
                }
            }
        } catch (error: RejectedExecutionException) {
            installAttemptInFlight.set(false)
            logDispatchFailure(error)
        }
    }

    private fun logDispatchFailure(error: Throwable) {
        try {
            Log.e(TAG, "Could not dispatch pending update install.", error)
        } catch (_: RuntimeException) {
            // Android Log is unavailable in local JVM tests.
        }
    }

    fun interface InstallPermissionCheck {
        fun canRequestPackageInstalls(): Boolean
    }

    fun interface StatusReader {
        fun autoUpdateStatus(): LocalStoreBase.AutoUpdateStatus
    }

    fun interface PendingUpdateInstall {
        fun installCachedPendingUpdate()
    }

    private companion object {
        const val TAG = "KaniUpdate"
    }
}
