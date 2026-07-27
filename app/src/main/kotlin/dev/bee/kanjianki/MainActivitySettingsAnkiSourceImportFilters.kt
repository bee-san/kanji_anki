package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SettingsImportPreset
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.data.SettingsSaveCommand
import java.util.Locale

internal class MainActivitySettingsAnkiSourceImportFilters(
    private val activity: MainActivitySettings,
    private val validation: MainActivitySettingsAnkiSourceValidation,
) {
    fun importFilterSettingsPanelModel(
        current: RecordsSyncModels.Settings,
        tagRepairedCards: Boolean,
    ): SettingsImportFiltersPanelModel {
        val state = SettingsImportFiltersState(
            activeCards = current.importActiveCards,
            suspendedCards = current.importSuspendedCards,
            taggedCards = current.importTaggedCardsEnabled(),
            weakCards = current.importWeakCards,
            browserQueryCards = current.importBrowserQueryCards,
            browserQuery = trimmed(current.importBrowserQuery),
            tags = trimmed(current.importTagsText()),
            difficulty = decimalText(current.importWeakFsrsDifficultyThreshold),
            lapses = thresholdText(current.importWeakLapsesThreshold),
            minMatching = thresholdText(current.importMinMatchingCardsPerKanji),
            tagRepairedCards = tagRepairedCards,
        )

        return SettingsImportFiltersPanelModel(
            title = SettingsTextCopy.importFiltersTitle(),
            summary = SettingsTextCopy.settingsImportSummary(current),
            body = SettingsTextCopy.importFiltersBody(),
            presetsTitle = SettingsTextCopy.presetsTitle(),
            presets = presetButtons(tagRepairedCards),
            state = state,
            activeCardsLabel = SettingsTextCopy.activeCardsLabel(),
            suspendedCardsLabel = SettingsTextCopy.suspendedCardsLabel(),
            taggedCardsLabel = SettingsTextCopy.taggedCardsLabel(),
            weakCardsLabel = SettingsTextCopy.weakCardsLabel(),
            browserQueryCardsLabel = SettingsTextCopy.browserQueryLabel(),
            browserQueryLabel = SettingsTextCopy.ankiBrowserQueryLabel(),
            browserQueryHint = SettingsTextCopy.ankiBrowserQueryHint(),
            browserQueryHelperText = SettingsTextCopy.ankiBrowserQueryHelperText(),
            tagsLabel = SettingsTextCopy.ankiNoteTagsLabel(),
            tagsHint = SettingsTextCopy.ankiNoteTagsHint(),
            difficultyLabel = SettingsTextCopy.fsrsDifficultyLabel(),
            lapsesLabel = SettingsTextCopy.lapsesLabel(),
            minMatchingLabel = SettingsTextCopy.minimumMatchingCardsLabel(),
            saveLabel = SettingsTextCopy.saveImportFiltersLabel(),
            onSave = SettingsImportFilterSaveAction(::saveImportFilters),
            tagRepairedCardsLabel = SettingsTextCopy.tagRepairedCardsLabel(),
        )
    }

    private fun presetButtons(tagRepairedCards: Boolean): List<SettingsImportPresetButtonModel> {
        return SettingsImportPreset.defaults().map { preset ->
            SettingsImportPresetButtonModel(
                label = preset.label(),
                onClick = SettingsImportFilterAction {
                    applyPreset(preset, tagRepairedCards)
                }
            )
        }
    }

    private fun saveImportFilters(state: SettingsImportFiltersState) {
        val parsedTags = RecordsBase.parseImportTags(state.tags)
        val queryText = state.browserQuery.trim()
        if (state.browserQueryCards && queryText.isEmpty()) {
            Toast.makeText(activity, SettingsTextCopy.browserQueryRequiredToast(), Toast.LENGTH_SHORT).show()
            return
        }
        if (!validation.hasSelectedImportSource(
                state.activeCards,
                state.suspendedCards,
                state.taggedCards,
                state.weakCards,
                state.browserQueryCards,
                parsedTags,
                queryText
            )
        ) {
            Toast.makeText(activity, SettingsTextCopy.importSourceRequiredToast(), Toast.LENGTH_SHORT).show()
            return
        }
        val parsedThresholds = validation.readImportThresholds(
            state.difficulty,
            state.lapses,
            state.minMatching
        ) ?: return
        activity.runSettingsWrite(
            traceSection = "kani.settings.import-filters.save",
            write = {
                activity.saveSettings(
                    SettingsSaveCommand.ImportFilters(
                        activeCards = state.activeCards,
                        suspendedCards = state.suspendedCards,
                        taggedCards = state.taggedCards,
                        tags = parsedTags.joinToString(" "),
                        weakCards = state.weakCards,
                        weakDifficulty = parsedThresholds.difficulty,
                        weakLapses = parsedThresholds.lapseThreshold,
                        minMatchingCards = parsedThresholds.minCards,
                        browserQueryCards = state.browserQueryCards,
                        browserQuery = queryText,
                        tagRepairedCards = state.tagRepairedCards,
                    ),
                )
            },
        ) {
            Toast.makeText(activity, SettingsTextCopy.importFiltersSavedToast(), Toast.LENGTH_LONG).show()
            activity.renderSettingsImportSync(true)
        }
    }

    private fun applyPreset(
        preset: SettingsImportPreset,
        tagRepairedCards: Boolean,
    ) {
        activity.runSettingsWrite(
            traceSection = "kani.settings.import-filters.preset",
            write = {
                activity.saveSettings(
                    SettingsSaveCommand.ImportFilters(
                        activeCards = preset.activeCards(),
                        suspendedCards = preset.suspendedCards(),
                        taggedCards = preset.taggedCards(),
                        tags = preset.tags(),
                        weakCards = preset.weakCards(),
                        weakDifficulty = preset.weakDifficulty(),
                        weakLapses = preset.weakLapses(),
                        minMatchingCards = preset.minMatchingCards(),
                        browserQueryCards = preset.browserQueryCards(),
                        browserQuery = preset.browserQuery(),
                        tagRepairedCards = tagRepairedCards,
                    ),
                )
            },
        ) {
            Toast.makeText(activity, SettingsTextCopy.importPresetSavedToast(), Toast.LENGTH_LONG).show()
            activity.renderSettingsImportSync(true)
        }
    }

    private companion object {
        fun trimmed(value: String?): String = value?.trim().orEmpty()

        fun decimalText(value: Double): String = String.format(Locale.ROOT, "%.1f", value)

        fun thresholdText(value: Int): String = value.coerceAtLeast(1).toString()
    }
}
