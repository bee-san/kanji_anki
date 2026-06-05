@file:JvmName("MainActivitySettingsUpdatePageCompose")

package dev.bee.kanjianki

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.DateTextPolicy
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.update.AutoUpdateScheduler
import dev.bee.kanjianki.update.GitHubUpdater
import dev.bee.kanjianki.updatecore.AutoUpdateSettingsTogglePolicy

internal fun settingsUpdatePanelModel(
    activity: MainActivitySettings,
    title: String,
): SettingsUpdatePanelModel {
    val status = activity.store.autoUpdateStatus()
    val canInstallUpdates = canInstallUpdates(activity)
    return SettingsUpdatePanelModel(
        title = title,
        statusLine = SettingsTextCopy.autoUpdatePanelStatus(status.enabled),
        statusColor = if (status.enabled) SettingsUpdateTeal else SettingsUpdateMuted,
        lastCheckLine = SettingsTextCopy.autoUpdateLastCheckLine(
            DateTextPolicy.autoUpdateLastCheckText(status.lastCheckAtMillis)
        ),
        lastResultLine = SettingsTextCopy.autoUpdateLastResultLine(status.lastResult),
        installPermissionLine = SettingsTextCopy.installPermissionLine(canInstallUpdates),
        installPermissionColor = if (canInstallUpdates) SettingsUpdateTeal else SettingsUpdateCoral,
        hasPendingUpdate = status.hasPendingUpdate(),
        pendingVersionLine = if (status.hasPendingUpdate()) {
            SettingsTextCopy.verifiedApkReadyLine(status.lastVersion)
        } else {
            null
        },
        pendingMessageLine = if (status.hasPendingUpdate()) {
            if (status.pendingMessage.isEmpty()) {
                SettingsTextCopy.pendingUpdateFallback()
            } else {
                status.pendingMessage
            }
        } else {
            null
        },
        canInstallUpdates = canInstallUpdates,
        onInstallVerifiedUpdate = { activity.runUpdate(true) },
        onOpenInstallSettings = { activity.startActivity(GitHubUpdater.installPermissionIntent(activity)) },
        onToggleAutomaticUpdates = { toggleAutomaticUpdates(activity, status.enabled) },
        automaticUpdatesToggleLabel = SettingsTextCopy.automaticUpdatesToggleLabel(status.enabled),
    )
}

@Composable
fun SettingsUpdatePage(model: SettingsUpdatePageModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SettingsUpdateHomeButton(onClick = model.onHome)
        SettingsUpdateBackButton(onClick = model.onBack)
        Text(
            text = model.title,
            modifier = Modifier.fillMaxWidth(),
            color = SettingsUpdateInk,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold
        )
        SettingsUpdatePanel(model = model.panel)
        SettingsUpdateFilledButton(
            label = SettingsTextCopy.checkForUpdateLabel(),
            containerColor = SettingsUpdatePinkDark,
            contentColor = SettingsUpdateWhite,
            minHeight = 62.dp,
            shape = SettingsUpdatePrimaryButtonShape,
            onClick = model.onCheckForUpdate
        )
    }
}

private fun canInstallUpdates(activity: MainActivitySettings): Boolean {
    MainActivityRuntimeOverrides.installPermission?.let { return it }
    return activity.packageManager.canRequestPackageInstalls()
}

private fun toggleAutomaticUpdates(activity: MainActivitySettings, enabled: Boolean) {
    val result = AutoUpdateSettingsTogglePolicy.toggle(enabled)
    activity.store.saveAutoUpdateEnabled(result.enabled())
    if (result.enabled()) {
        AutoUpdateScheduler.schedule(activity)
    } else {
        AutoUpdateScheduler.cancel(activity)
    }
    Toast.makeText(activity, result.message(), Toast.LENGTH_SHORT).show()
    activity.renderUpdate()
}
