package dev.bee.kanjianki

data class SettingsNewCardSortOptionModel(
    val label: String,
    val mode: String,
)

fun interface SettingsNewCardSortSaver {
    fun save(mode: String)
}

data class SettingsNewCardSortPanelModel(
    val title: String,
    val body: String,
    val initialMode: String,
    val options: List<SettingsNewCardSortOptionModel>,
    val saveLabel: String,
    val onSave: SettingsNewCardSortSaver,
) : SettingsPanelModel
