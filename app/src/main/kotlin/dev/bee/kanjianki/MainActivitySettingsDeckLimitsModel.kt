package dev.bee.kanjianki

object SettingsDeckLimitsTestTags {
    const val NEW_PER_DAY_INPUT = "settings-deck-limits-new-per-day-input"
}

fun interface SettingsDeckLimitsSaveAction {
    fun save(newPerDayText: String)
}

data class SettingsDeckLimitsPanelModel(
    val title: String,
    val body: String,
    val newPerDayLabel: String,
    val initialNewPerDayText: String,
    val saveLabel: String,
    val onSave: SettingsDeckLimitsSaveAction,
) : SettingsPanelModel
