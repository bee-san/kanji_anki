package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.NewCardSortSettingsPolicy
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.sync.SyncSettings

internal class MainActivitySettingsStudySortPanel(private val activity: MainActivitySettings) {
    fun newCardSortSettingsPanelModel(current: RecordsSyncModels.Settings): SettingsNewCardSortPanelModel {
        return SettingsNewCardSortPanelModel(
            title = SettingsTextCopy.newCardSortTitle(),
            body = SettingsTextCopy.newCardSortBody(),
            initialMode = current.newCardSortMode,
            options = newCardSortOptions(),
            saveLabel = SettingsTextCopy.saveNewCardSortLabel(),
            onSave = SettingsNewCardSortSaver { mode -> saveNewCardSort(mode) }
        )
    }

    private fun newCardSortOptions(): List<SettingsNewCardSortOptionModel> {
        return listOf(
            newCardSortOption(RecordsBase.NEW_CARD_SORT_FREQUENCY),
            newCardSortOption(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY),
            newCardSortOption(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK),
            newCardSortOption(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS),
            newCardSortOption(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY)
        )
    }

    private fun newCardSortOption(mode: String): SettingsNewCardSortOptionModel {
        return SettingsNewCardSortOptionModel(
            label = SettingsTextCopy.newCardSortLabel(mode),
            mode = mode
        )
    }

    private fun saveNewCardSort(mode: String) {
        val request = NewCardSortSettingsPolicy.saveRequest(mode)
        activity.store.putStringSetting(SyncSettings.NEW_CARD_SORT_MODE_SETTING_KEY, request.mode)
        Toast.makeText(activity, request.message, Toast.LENGTH_SHORT).show()
        activity.renderSettings()
    }
}
