package dev.bee.kanjianki

fun interface SettingsWorkloadAction {
    fun run()
}

data class SettingsWorkloadPanelModel(
    val title: String,
    val autoMode: Boolean,
    val autoStatus: String,
    val automaticBody: String,
    val manualBody: String,
    val selectedWorkloadPercent: IntArray,
    val selectedMaxItems: IntArray,
    val scaleLabels: List<String>,
    val saveMaximumLabel: String,
    val manualWorkloadLabel: String,
    val saveWorkloadLabel: String,
    val automaticParetoLabel: String,
    val onSaveMaximum: SettingsWorkloadAction,
    val onEnableManual: SettingsWorkloadAction,
    val onSaveWorkload: SettingsWorkloadAction,
    val onEnableAutomatic: SettingsWorkloadAction,
) : SettingsPanelModel
