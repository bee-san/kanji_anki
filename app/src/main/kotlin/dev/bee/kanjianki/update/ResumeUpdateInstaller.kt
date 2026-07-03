package dev.bee.kanjianki.update

import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.updatecore.ResumeInstallPolicy
import java.util.concurrent.Executor

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
    @Volatile
    private var installAttemptInFlight = false

    fun onResume() {
        val status = statusReader.autoUpdateStatus()
        if (!ResumeInstallPolicy.shouldInstall(
                status.enabled,
                installPermissionCheck.canRequestPackageInstalls(),
                status.hasPendingUpdate(),
                installAttemptInFlight,
            )
        ) {
            return
        }
        installAttemptInFlight = true
        background.execute {
            try {
                pendingUpdateInstall.installCachedPendingUpdate()
            } finally {
                installAttemptInFlight = false
            }
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
}
