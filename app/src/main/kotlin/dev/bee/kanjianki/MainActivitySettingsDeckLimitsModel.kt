package dev.bee.kanjianki

object SettingsDeckLimitsTestTags {
    const val NEW_PER_DAY_INPUT = "settings-deck-limits-new-per-day-input"
    const val ACTIVE_QUEUE_CAP_INPUT = "settings-deck-limits-active-queue-cap-input"
}

fun interface SettingsDeckLimitsSaveAction {
    fun save(newPerDayText: String, activeQueueCapText: String)
}

data class SettingsDeckLimitsPanelModel(
    val title: String,
    val body: String,
    val newPerDayLabel: String,
    val initialNewPerDayText: String,
    val activeQueueCapLabel: String,
    val initialActiveQueueCapText: String,
    val saveLabel: String,
    val onSave: SettingsDeckLimitsSaveAction,
) : SettingsPanelModel
