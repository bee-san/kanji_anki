package dev.bee.kanjianki

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object SettingsRetentionControlDescriptions {
    const val RETENTION_SLIDER = "FSRS retention slider"
    const val RANK_RETENTION_CHECKBOX = "Jiten-rank retention ranges checkbox"
    const val RANK_RANGES_INPUT = "Jiten-rank retention ranges input"
}

class SettingsRetentionState(
    frequencyRetentionEnabled: Boolean,
    frequencyRetentionRanges: String?,
) {
    var frequencyRetentionEnabled by mutableStateOf(frequencyRetentionEnabled)
    var frequencyRetentionRanges by mutableStateOf(frequencyRetentionRanges.orEmpty())
}

fun interface SettingsRetentionSaveAction {
    fun save(retentionPercent: Int, frequencyRetentionEnabled: Boolean, frequencyRetentionRanges: String)
}

data class SettingsRetentionPanelModel(
    val title: String,
    val body: String,
    val selectedRetentionPercent: IntArray,
    val presetValues: IntArray,
    val state: SettingsRetentionState,
    val rankRetentionLabel: String,
    val rankRangesBody: String,
    val exampleRangesText: String,
    val exampleRangesLabel: String,
    val saveLabel: String,
    val onSave: SettingsRetentionSaveAction,
) : SettingsPanelModel
