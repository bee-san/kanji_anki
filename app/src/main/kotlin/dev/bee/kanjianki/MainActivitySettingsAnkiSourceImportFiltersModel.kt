package dev.bee.kanjianki

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
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
    tagRepairedCards: Boolean = false,
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
    var tagRepairedCards by mutableStateOf(tagRepairedCards)

    companion object {
        val Saver = listSaver<SettingsImportFiltersState, Any>(
            save = { state ->
                listOf(
                    state.activeCards,
                    state.suspendedCards,
                    state.taggedCards,
                    state.weakCards,
                    state.browserQueryCards,
                    state.browserQuery,
                    state.tags,
                    state.difficulty,
                    state.lapses,
                    state.minMatching,
                    state.tagRepairedCards,
                )
            },
            restore = { values ->
                SettingsImportFiltersState(
                    activeCards = values[0] as Boolean,
                    suspendedCards = values[1] as Boolean,
                    taggedCards = values[2] as Boolean,
                    weakCards = values[3] as Boolean,
                    browserQueryCards = values[4] as Boolean,
                    browserQuery = values[5] as String,
                    tags = values[6] as String,
                    difficulty = values[7] as String,
                    lapses = values[8] as String,
                    minMatching = values[9] as String,
                    tagRepairedCards = values[10] as Boolean,
                )
            },
        )
    }
}

fun interface SettingsImportFilterAction {
    fun run()
}

fun interface SettingsImportFilterSaveAction {
    fun save(state: SettingsImportFiltersState)
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
    val onSave: SettingsImportFilterSaveAction,
    val tagRepairedCardsLabel: String = "",
) : SettingsPanelModel
