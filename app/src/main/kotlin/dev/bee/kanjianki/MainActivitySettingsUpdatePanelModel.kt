package dev.bee.kanjianki

data class SettingsUpdatePanelModel(
    val title: String,
    val statusLine: String,
    val statusColor: Int,
    val installedVersionLine: String,
    val latestVersionLine: String,
    val lastCheckLine: String,
    val lastResultLine: String,
    val installPermissionLine: String,
    val installPermissionColor: Int,
    val hasPendingUpdate: Boolean,
    val pendingVersionLine: String?,
    val pendingMessageLine: String?,
    val canInstallUpdates: Boolean,
    val onInstallVerifiedUpdate: () -> Unit,
    val onOpenInstallSettings: () -> Unit,
    val onToggleAutomaticUpdates: () -> Unit,
    val automaticUpdatesToggleLabel: String,
    val showAutoUpdateInBackground: Boolean,
    val autoUpdateInBackgroundLabel: String,
    val onAutoUpdateInBackground: () -> Unit,
    val betaUpdatesEnabled: Boolean,
    val betaUpdatesToggleLabel: String,
    val betaUpdatesDescription: String,
    val onToggleBetaUpdates: () -> Unit,
)

data class SettingsUpdateOverviewPanelModel(
    val panel: SettingsUpdatePanelModel,
    val openUpdaterLabel: String,
    val onOpenUpdater: () -> Unit,
) : SettingsPanelModel
