package dev.bee.kanjianki

data class SettingsBackupPanelModel(
    val title: String,
    val body: String,
    val lastBackupLine: String,
    val archiveCountLine: String,
    val exportLabel: String,
    val onExport: Runnable,
    val restoreLabel: String,
    val onRestore: Runnable,
) : SettingsPanelModel

data class BackupRestoreConfirmDialogModel(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val dismissLabel: String,
    val onConfirm: Runnable,
    val onDismiss: Runnable,
)
