package dev.bee.kanjianki

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object SettingsImportFiltersTestTags {
    const val BROWSER_QUERY_INPUT = "settings-import-browser-query-input"
    const val TAGS_INPUT = "settings-import-tags-input"
    const val DIFFICULTY_INPUT = "settings-import-difficulty-input"
    const val LAPSES_INPUT = "settings-import-lapses-input"
    const val MIN_MATCHING_INPUT = "settings-import-min-matching-input"
}

class SettingsImportFiltersState(
    activeCards: Boolean,
    suspendedCards: Boolean,
    taggedCards: Boolean,
    weakCards: Boolean,
    browserQueryCards: Boolean,
    browserQuery: String?,
    tags: String?,
    difficulty: String?,
    lapses: String?,
    minMatching: String?,
) {
    var activeCards by mutableStateOf(activeCards)
    var suspendedCards by mutableStateOf(suspendedCards)
    var taggedCards by mutableStateOf(taggedCards)
    var weakCards by mutableStateOf(weakCards)
    var browserQueryCards by mutableStateOf(browserQueryCards)
    var browserQuery by mutableStateOf(browserQuery.orEmpty())
    var tags by mutableStateOf(tags.orEmpty())
    var difficulty by mutableStateOf(difficulty.orEmpty())
    var lapses by mutableStateOf(lapses.orEmpty())
    var minMatching by mutableStateOf(minMatching.orEmpty())
}

fun interface SettingsImportFilterAction {
    fun run()
}

data class SettingsImportPresetButtonModel(
    val label: String,
    val onClick: SettingsImportFilterAction,
)

data class SettingsImportFiltersPanelModel(
    val title: String,
    val summary: String,
    val body: String,
    val presetsTitle: String,
    val presets: List<SettingsImportPresetButtonModel>,
    val state: SettingsImportFiltersState,
    val activeCardsLabel: String,
    val suspendedCardsLabel: String,
    val taggedCardsLabel: String,
    val weakCardsLabel: String,
    val browserQueryCardsLabel: String,
    val browserQueryLabel: String,
    val browserQueryHint: String,
    val browserQueryHelperText: String,
    val tagsLabel: String,
    val tagsHint: String,
    val difficultyLabel: String,
    val lapsesLabel: String,
    val minMatchingLabel: String,
    val saveLabel: String,
    val onSave: SettingsImportFilterAction,
) : SettingsPanelModel
