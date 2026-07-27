@file:JvmName("MainActivityHomeUpdatePermissionPrompt")

package dev.bee.kanjianki

import dev.bee.kanjianki.update.GitHubUpdater
import dev.bee.kanjianki.platform.DeviceSettingKeys

/**
 * Shows the automatic-update permission dialog on the home screen when the
 * prompt policy allows it. "Allow" opens the Android settings page where the
 * user grants the permission; both choices record the prompt so the user is
 * not nagged again for the same state.
 */
internal fun MainActivityHome.maybeShowUpdatePermissionPrompt(snapshot: HomeUpdatePermissionPromptSnapshot?) {
    if (snapshot == null) {
        return
    }
    pendingUpdatePermissionDialog = HomeUpdatePermissionDialogModels.create(
        pendingVersion = snapshot.pendingVersion,
        onAllow = Runnable {
            pendingUpdatePermissionDialog = null
            recordInstallPermissionPrompted(snapshot.lastVersion)
            rerenderLatestHomeRoute()
            startActivity(GitHubUpdater.installPermissionIntent(this))
        },
        onNotNow = Runnable {
            pendingUpdatePermissionDialog = null
            recordInstallPermissionPrompted(snapshot.lastVersion)
            rerenderLatestHomeRoute()
        },
    )
    rerenderLatestHomeRoute()
}

private fun MainActivityHome.recordInstallPermissionPrompted(version: String) {
    deviceSettingsStore.edit {
        put(DeviceSettingKeys.updatePermissionPromptShown, true)
        put(DeviceSettingKeys.updatePermissionPromptLastVersion, version)
    }
}
