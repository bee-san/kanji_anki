package dev.bee.kanjianki

sealed interface SettingsPanelModel

data class SettingsScreenModel(
    val homeLabel: String,
    val onHome: Runnable,
    val hero: SettingsAutomationHeroModel,
    val cards: List<SettingsHubCardModel>,
)

data class SettingsHubCardModel(
    val routeKey: String,
    val title: String,
    val summary: String,
    val iconRes: Int,
    val panelCount: String,
    val contentDescription: String,
    val onOpen: Runnable,
)

data class SettingsSubmenuScreenModel(
    val homeLabel: String,
    val onHome: Runnable,
    val backLabel: String,
    val onBack: Runnable,
    val title: String,
    val body: String,
    val panels: List<SettingsPanelModel>,
)

data class SettingsCategorySectionModel(
    val sectionKey: String,
    val title: String,
    val summary: String,
    val iconRes: Int,
    val expanded: Boolean,
    val panelCount: String,
    val contentDescription: String,
    val onToggle: Runnable,
    val panels: List<SettingsPanelModel>,
)
