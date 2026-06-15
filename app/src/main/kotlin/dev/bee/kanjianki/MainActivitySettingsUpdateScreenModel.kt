package dev.bee.kanjianki

data class SettingsUpdatePageModel(
    val title: String,
    val onHome: () -> Unit,
    val onBack: () -> Unit,
    val onCheckForUpdate: () -> Unit,
    val panel: SettingsUpdatePanelModel,
)

data class SettingsUpdateRunModel(
    val title: String,
    val progressLabel: String,
    val onHome: () -> Unit,
    val onBack: () -> Unit,
)
