@file:JvmName("MainActivityHomeUpdatePermissionPrompt")

package dev.bee.kanjianki

import dev.bee.kanjianki.update.GitHubUpdater
import dev.bee.kanjianki.updatecore.InstallPermissionPromptPolicy

/**
 * Reports whether the app currently holds the "install unknown apps"
 * permission, honoring the test override seam.
 */
internal fun canRequestPackageInstalls(activity: MainActivityBase): Boolean {
    MainActivityRuntimeOverrides.installPermission?.let { return it }
    return activity.packageManager.canRequestPackageInstalls()
}

/**
 * Shows the automatic-update permission dialog on the home screen when the
 * prompt policy allows it. "Allow" opens the Android settings page where the
 * user grants the permission; both choices record the prompt so the user is
 * not nagged again for the same state.
 */
internal fun MainActivityHome.maybeShowUpdatePermissionPrompt() {
    val status = store.autoUpdateStatus()
    val shouldPrompt = InstallPermissionPromptPolicy.shouldPrompt(
        status.enabled,
        canRequestPackageInstalls(this),
        status.lastCheckAtMillis > 0L,
        store.installPermissionPromptShown(),
        status.hasPendingUpdate(),
        status.lastVersion,
        store.installPermissionPromptLastVersion(),
    )
    if (!shouldPrompt) {
        return
    }
    val pendingVersion = if (status.hasPendingUpdate()) status.lastVersion else null
    pendingUpdatePermissionDialog = HomeUpdatePermissionDialogModels.create(
        pendingVersion = pendingVersion,
        onAllow = Runnable {
            pendingUpdatePermissionDialog = null
            store.recordInstallPermissionPrompted(status.lastVersion)
            rerenderLatestHomeRoute()
            startActivity(GitHubUpdater.installPermissionIntent(this))
        },
        onNotNow = Runnable {
            pendingUpdatePermissionDialog = null
            store.recordInstallPermissionPrompted(status.lastVersion)
            rerenderLatestHomeRoute()
        },
    )
    rerenderLatestHomeRoute()
}
