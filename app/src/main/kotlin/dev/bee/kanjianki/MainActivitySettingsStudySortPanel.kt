package dev.bee.kanjianki

import android.view.View
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SettingsTextCopy

internal class MainActivitySettingsStudySortPanel(private val activity: MainActivitySettings) {
    private val actions = MainActivitySettingsStudySortActions(activity)

    fun newCardSortSettingsPanel(current: RecordsSyncModels.Settings): View {
        return newCardSortSettingsPanelView(activity, newCardSortSettingsPanelModel(current))
    }

    fun newCardSortSettingsPanelModel(current: RecordsSyncModels.Settings): SettingsNewCardSortPanelModel {
        return SettingsNewCardSortPanelModel(
            title = SettingsTextCopy.newCardSortTitle(),
            body = SettingsTextCopy.newCardSortBody(),
            initialMode = current.newCardSortMode,
            options = newCardSortOptions(),
            saveLabel = SettingsTextCopy.saveNewCardSortLabel(),
            onSave = SettingsNewCardSortSaver { mode -> actions.saveNewCardSort(mode) }
        )
    }

    private fun newCardSortOptions(): List<SettingsNewCardSortOptionModel> {
        return listOf(
            newCardSortOption(RecordsBase.NEW_CARD_SORT_FREQUENCY),
            newCardSortOption(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY),
            newCardSortOption(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK),
            newCardSortOption(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS)
        )
    }

    private fun newCardSortOption(mode: String): SettingsNewCardSortOptionModel {
        return SettingsNewCardSortOptionModel(
            label = SettingsTextCopy.newCardSortLabel(mode),
            mode = mode
        )
    }
}
