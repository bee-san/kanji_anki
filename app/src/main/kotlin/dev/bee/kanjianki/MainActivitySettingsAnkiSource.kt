package dev.bee.kanjianki

import android.view.View
import dev.bee.kanjianki.core.RecordsSyncModels

internal class MainActivitySettingsAnkiSource(activity: MainActivitySettings) {
    private val validation = MainActivitySettingsAnkiSourceValidation(activity)
    private val frequencyRange = MainActivitySettingsAnkiSourceFrequencyRange(activity, validation)
    private val noteType = MainActivitySettingsAnkiSourceNoteType(activity)
    private val importFilters = MainActivitySettingsAnkiSourceImportFilters(activity, validation)

    fun noteTypeSettingsPanel(current: RecordsSyncModels.Settings): View {
        return noteType.noteTypeSettingsPanel(current)
    }

    fun noteTypeSettingsPanelModel(current: RecordsSyncModels.Settings): SettingsNoteTypePanelModel {
        return noteType.noteTypeSettingsPanelModel(current)
    }

    fun importFilterSettingsPanel(current: RecordsSyncModels.Settings): View {
        return importFilters.importFilterSettingsPanel(current)
    }

    fun importFilterSettingsPanelModel(current: RecordsSyncModels.Settings): SettingsImportFiltersPanelModel {
        return importFilters.importFilterSettingsPanelModel(current)
    }

    fun frequencyRangeSettingsPanel(current: RecordsSyncModels.Settings): View {
        return frequencyRange.frequencyRangeSettingsPanel(current)
    }

    fun frequencyRangeSettingsPanelModel(current: RecordsSyncModels.Settings): SettingsFrequencyRangePanelModel {
        return frequencyRange.frequencyRangeSettingsPanelModel(current)
    }
}
