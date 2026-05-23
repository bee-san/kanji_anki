package dev.bee.kanjianki

fun interface SettingsStudyAheadSaver {
    fun save(minutesText: String)
}

data class SettingsStudyAheadPanelModel(
    val title: String,
    val body: String,
    val minutesLabel: String,
    val initialMinutesText: String,
    val saveLabel: String,
    val onSave: SettingsStudyAheadSaver,
) : SettingsPanelModel
