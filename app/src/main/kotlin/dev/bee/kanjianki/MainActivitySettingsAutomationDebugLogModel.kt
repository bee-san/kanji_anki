package dev.bee.kanjianki

data class SettingsDebugLogPanelModel(
    val title: String,
    val status: String,
    val statusColor: Int,
    val detail: String,
    val toggleLabel: String,
    val togglePrimary: Boolean,
    val onToggle: Runnable,
    val shareLabel: String,
    val onShare: Runnable,
) : SettingsPanelModel
