package dev.bee.kanjianki

sealed interface SettingsPanelModel

data class SettingsScreenModel(
    val homeLabel: String,
    val onHome: Runnable,
    val hero: SettingsAutomationHeroModel,
    val categories: List<SettingsCategorySectionModel>,
)

data class SettingsCategorySectionModel(
    val title: String,
    val summary: String,
    val iconRes: Int,
    val expanded: Boolean,
    val panelCount: String,
    val contentDescription: String,
    val onToggle: Runnable,
    val panels: List<SettingsPanelModel>,
)
