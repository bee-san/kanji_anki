package dev.bee.kanjianki

data class SettingsUpdatePanelModel(
    val title: String,
    val statusLine: String,
    val statusColor: Int,
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
)

data class SettingsUpdateOverviewPanelModel(
    val panel: SettingsUpdatePanelModel,
    val openUpdaterLabel: String,
    val onOpenUpdater: () -> Unit,
) : SettingsPanelModel
