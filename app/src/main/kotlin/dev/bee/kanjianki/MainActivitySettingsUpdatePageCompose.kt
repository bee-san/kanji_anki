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
import dev.bee.kanjianki.updatecore.BackgroundAutoUpdateOptionPolicy

internal fun settingsUpdatePanelModel(
    activity: MainActivitySettings,
    title: String,
    status: SettingsAutoUpdateState = activity.loadSettingsDeviceState().autoUpdate,
    betaUpdatesEnabled: Boolean = activity.loadSettingsDeviceState().betaUpdatesEnabled,
): SettingsUpdatePanelModel {
    val canInstallUpdates = canInstallUpdates(activity)
    return SettingsUpdatePanelModel(
        title = title,
        statusLine = SettingsTextCopy.autoUpdatePanelStatus(status.enabled),
        statusColor = if (status.enabled) MainActivityUiSupport.TEAL else MainActivityUiSupport.MUTED,
        lastCheckLine = SettingsTextCopy.autoUpdateLastCheckLine(
            DateTextPolicy.autoUpdateLastCheckText(status.lastCheckAtMillis)
        ),
        lastResultLine = SettingsTextCopy.autoUpdateLastResultLine(status.lastResult),
        installPermissionLine = SettingsTextCopy.installPermissionLine(canInstallUpdates),
        installPermissionColor = if (canInstallUpdates) MainActivityUiSupport.TEAL else MainActivityUiSupport.CORAL,
        hasPendingUpdate = status.hasPendingUpdate(),
        pendingVersionLine = if (status.hasPendingUpdate()) {
            SettingsTextCopy.verifiedApkReadyLine(status.lastVersion)
        } else {
            null
        },
        pendingMessageLine = if (status.hasPendingUpdate()) {
            if (status.pendingMessage.isEmpty()) {
                SettingsTextCopy.pendingUpdateFallback(canInstallUpdates)
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
        showAutoUpdateInBackground = BackgroundAutoUpdateOptionPolicy.optionVisible(status.enabled, canInstallUpdates),
        autoUpdateInBackgroundLabel = SettingsTextCopy.autoUpdateInBackgroundLabel(),
        onAutoUpdateInBackground = { autoUpdateInBackground(activity, status.enabled) },
        betaUpdatesEnabled = betaUpdatesEnabled,
        betaUpdatesToggleLabel = SettingsTextCopy.betaUpdatesToggleLabel(betaUpdatesEnabled),
        betaUpdatesDescription = SettingsTextCopy.betaUpdatesDescription(),
        onToggleBetaUpdates = { toggleBetaUpdates(activity, betaUpdatesEnabled) },
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
    return canRequestPackageInstalls(activity)
}

private fun toggleAutomaticUpdates(activity: MainActivitySettings, enabled: Boolean) {
    val result = AutoUpdateSettingsTogglePolicy.toggle(enabled)
    activity.runSettingsWrite(
        traceSection = "kani.settings.auto-update.toggle",
        write = {
            activity.deviceSettingsStore.setAutoUpdateEnabled(result.enabled())
        },
    ) {
        if (result.enabled()) {
            AutoUpdateScheduler.schedule(activity)
        } else {
            AutoUpdateScheduler.cancel(activity)
        }
        Toast.makeText(activity, result.message(), Toast.LENGTH_SHORT).show()
        activity.renderUpdate(true)
    }
}

private fun toggleBetaUpdates(activity: MainActivitySettings, enabled: Boolean) {
    activity.runSettingsWrite(
        traceSection = "kani.settings.beta-updates.toggle",
        write = { activity.deviceSettingsStore.setBetaUpdatesEnabled(!enabled) },
    ) {
        activity.renderUpdate(true)
    }
}

/**
 * One-tap setup for background updates: turns automatic checks on when they
 * are off, then opens the Android settings page that grants the install
 * permission background updates need. When the permission is already granted,
 * the panel simply re-renders with the refreshed state.
 */
private fun autoUpdateInBackground(activity: MainActivitySettings, enabled: Boolean) {
    if (!BackgroundAutoUpdateOptionPolicy.shouldEnableAutoUpdates(enabled)) {
        activity.startActivity(GitHubUpdater.installPermissionIntent(activity))
        return
    }
    val result = AutoUpdateSettingsTogglePolicy.toggle(false)
    activity.runSettingsWrite(
        traceSection = "kani.settings.auto-update.background",
        write = {
            activity.deviceSettingsStore.setAutoUpdateEnabled(result.enabled())
        },
    ) {
        AutoUpdateScheduler.schedule(activity)
        Toast.makeText(activity, result.message(), Toast.LENGTH_SHORT).show()
        if (BackgroundAutoUpdateOptionPolicy.shouldOpenInstallSettings(canInstallUpdates(activity))) {
            activity.startActivity(GitHubUpdater.installPermissionIntent(activity))
        } else {
            activity.renderUpdate(true)
        }
    }
}
