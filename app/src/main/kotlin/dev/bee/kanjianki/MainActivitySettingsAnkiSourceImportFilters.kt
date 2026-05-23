package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SettingsImportPreset
import dev.bee.kanjianki.core.SettingsTextCopy
import java.util.Locale

internal class MainActivitySettingsAnkiSourceImportFilters(
    private val activity: MainActivitySettings,
    private val validation: MainActivitySettingsAnkiSourceValidation,
) {
    fun importFilterSettingsPanelModel(
        current: RecordsSyncModels.Settings,
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
            minMatching = thresholdText(current.importMinMatchingCardsPerKanji)
        )

        return SettingsImportFiltersPanelModel(
            title = SettingsTextCopy.importFiltersTitle(),
            summary = SettingsTextCopy.settingsImportSummary(current),
            body = SettingsTextCopy.importFiltersBody(),
            presetsTitle = SettingsTextCopy.presetsTitle(),
            presets = presetButtons(),
            state = state,
            activeCardsLabel = SettingsTextCopy.activeCardsLabel(),
            suspendedCardsLabel = SettingsTextCopy.suspendedCardsLabel(),
            taggedCardsLabel = SettingsTextCopy.taggedCardsLabel(),
            weakCardsLabel = SettingsTextCopy.weakCardsLabel(),
            browserQueryCardsLabel = SettingsTextCopy.browserQueryLabel(),
            browserQueryLabel = SettingsTextCopy.ankiBrowserQueryLabel(),
            browserQueryHint = SettingsTextCopy.ankiBrowserQueryHint(),
            tagsLabel = SettingsTextCopy.ankiNoteTagsLabel(),
            tagsHint = SettingsTextCopy.ankiNoteTagsHint(),
            difficultyLabel = SettingsTextCopy.fsrsDifficultyLabel(),
            lapsesLabel = SettingsTextCopy.lapsesLabel(),
            minMatchingLabel = SettingsTextCopy.minimumMatchingCardsLabel(),
            saveLabel = SettingsTextCopy.saveImportFiltersLabel(),
            onSave = SettingsImportFilterAction { saveImportFilters(state) }
        )
    }

    private fun presetButtons(): List<SettingsImportPresetButtonModel> {
        return SettingsImportPreset.defaults().map { preset ->
            SettingsImportPresetButtonModel(
                label = preset.label(),
                onClick = SettingsImportFilterAction { applyPreset(preset) }
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
        SettingsWriteActions.saveImportFilters(
            SettingsWriteActions.ImportFilterWriteRequest(
                state.activeCards,
                state.suspendedCards,
                state.taggedCards,
                parsedTags.joinToString(" "),
                state.weakCards,
                parsedThresholds.difficulty,
                parsedThresholds.lapseThreshold,
                parsedThresholds.minCards,
                state.browserQueryCards,
                queryText
            ),
            SettingsStoreWriter(activity)
        )
        Toast.makeText(activity, SettingsTextCopy.importFiltersSavedToast(), Toast.LENGTH_LONG).show()
        activity.renderSettings()
    }

    private fun applyPreset(preset: SettingsImportPreset) {
        SettingsWriteActions.saveImportFilters(
            SettingsWriteActions.ImportFilterWriteRequest(
                preset.activeCards(),
                preset.suspendedCards(),
                preset.taggedCards(),
                preset.tags(),
                preset.weakCards(),
                preset.weakDifficulty(),
                preset.weakLapses(),
                preset.minMatchingCards(),
                preset.browserQueryCards(),
                preset.browserQuery()
            ),
            SettingsStoreWriter(activity)
        )
        Toast.makeText(activity, SettingsTextCopy.importPresetSavedToast(), Toast.LENGTH_LONG).show()
        activity.renderSettings()
    }

    private class SettingsStoreWriter(
        private val activity: MainActivitySettings,
    ) : SettingsWriteActions.SettingWriter {
        override fun putIntSetting(key: String, value: Int) {
            activity.store.putIntSetting(key, value)
        }

        override fun putStringSetting(key: String, value: String?) {
            activity.store.putStringSetting(key, value)
        }

        override fun putDoubleSetting(key: String, value: Double) {
            activity.store.putDoubleSetting(key, value)
        }
    }

    private companion object {
        fun trimmed(value: String?): String = value?.trim().orEmpty()

        fun decimalText(value: Double): String = String.format(Locale.ROOT, "%.1f", value)

        fun thresholdText(value: Int): String = value.coerceAtLeast(1).toString()
    }
}
