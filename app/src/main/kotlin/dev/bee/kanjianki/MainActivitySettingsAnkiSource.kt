package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsSyncModels

internal class MainActivitySettingsAnkiSource(activity: MainActivitySettings) {
    private val validation = MainActivitySettingsAnkiSourceValidation(activity)
    private val frequencyRange = MainActivitySettingsAnkiSourceFrequencyRange(activity, validation)
    private val noteType = MainActivitySettingsAnkiSourceNoteType(activity)
    private val importFilters = MainActivitySettingsAnkiSourceImportFilters(activity, validation)

    fun noteTypeSettingsPanelModel(current: RecordsSyncModels.Settings): SettingsNoteTypePanelModel {
        return noteType.noteTypeSettingsPanelModel(current)
    }

    fun importFilterSettingsPanelModel(
        current: RecordsSyncModels.Settings,
        tagRepairedCards: Boolean,
    ): SettingsImportFiltersPanelModel {
        return importFilters.importFilterSettingsPanelModel(current, tagRepairedCards)
    }

    fun frequencyRangeSettingsPanelModel(current: RecordsSyncModels.Settings): SettingsFrequencyRangePanelModel {
        return frequencyRange.frequencyRangeSettingsPanelModel(current)
    }
}
