package dev.bee.kanjianki

data class SettingsNewCardSortOptionModel(
    val label: String,
    val mode: String,
    val description: String,
)

data class SettingsNewCardSortPreviewRowModel(
    val kanji: String,
    val primaryMeaning: String,
    val scoreLabel: String,
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
    val previewRowsByMode: Map<String, List<SettingsNewCardSortPreviewRowModel>> = emptyMap(),
    val onSave: SettingsNewCardSortSaver,
) : SettingsPanelModel {
    fun hasPreviewRows(): Boolean = previewRowsByMode.values.any { it.isNotEmpty() }

    fun previewRows(mode: String): List<SettingsNewCardSortPreviewRowModel> {
        return previewRowsByMode[mode].orEmpty()
    }
}
