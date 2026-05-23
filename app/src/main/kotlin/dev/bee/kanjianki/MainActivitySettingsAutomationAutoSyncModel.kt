package dev.bee.kanjianki

fun interface SettingsAutoSyncAction {
    fun run()
}

data class SettingsAutoSyncPanelModel(
    val title: String,
    val status: String,
    val statusColor: Int,
    val detail: String,
    val actionLabel: String?,
    val primaryAction: Boolean,
    val onAction: SettingsAutoSyncAction?,
) : SettingsPanelModel
