package dev.bee.kanjianki

object SettingsFrequencyRangeTestTags {
    const val MIN_RANK_INPUT = "settings-frequency-min-rank-input"
    const val MAX_RANK_INPUT = "settings-frequency-max-rank-input"
    const val MIN_RANK_SLIDER = "settings-frequency-min-rank-slider"
    const val MAX_RANK_SLIDER = "settings-frequency-max-rank-slider"
}

fun interface SettingsFrequencyRangeSaveAction {
    fun save(minRankText: String, maxRankText: String)
}

data class SettingsFrequencyRangePanelModel(
    val title: String,
    val body: String,
    val selectedRanks: IntArray,
    val minRankLabel: String,
    val initialMinRankText: String,
    val maxRankLabel: String,
    val initialMaxRankText: String,
    val minimumRankLabel: String,
    val maximumRankLabel: String,
    val saveLabel: String,
    val onSave: SettingsFrequencyRangeSaveAction,
) : SettingsPanelModel
