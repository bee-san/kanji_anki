package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.core.NewCardSortPlanner
import dev.bee.kanjianki.core.NewCardSortSettingsPolicy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SettingsTextCopy

internal class MainActivitySettingsStudySortPanel(private val activity: MainActivitySettings) {
    fun newCardSortSettingsPanelModel(current: RecordsSyncModels.Settings): SettingsNewCardSortPanelModel {
        val rows = activity.store.activeDashboardRows()
        val previewRowsData = SettingsNewCardSortPreviewCache.resolve(
            rows = rows,
            cached = activity.cachedNewCardSortPreviewRows,
            hasSimilarLocalPair = activity.store::hasSimilarLocalPair,
        )
        activity.cachedNewCardSortPreviewRows = previewRowsData
        val previewRowsByMode = previewRowsData.previewRowsByMode
        return SettingsNewCardSortPanelModel(
            title = SettingsTextCopy.newCardSortTitle(),
            body = SettingsTextCopy.newCardSortBody(),
            initialMode = current.newCardSortMode,
            options = newCardSortOptions(),
            saveLabel = SettingsTextCopy.saveNewCardSortLabel(),
            previewRowsByMode = previewRowsByMode,
            previewWarningsByMode = previewRowsData.previewWarningsByMode,
            onSave = SettingsNewCardSortSaver { mode -> saveNewCardSort(mode) }
        )
    }

    private fun newCardSortOptions(): List<SettingsNewCardSortOptionModel> {
        return listOf(
            newCardSortOption(RecordsBase.NEW_CARD_SORT_FREQUENCY),
            newCardSortOption(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY),
            newCardSortOption(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY),
            newCardSortOption(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK),
            newCardSortOption(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS)
        )
    }

    private fun newCardSortOption(mode: String): SettingsNewCardSortOptionModel {
        return SettingsNewCardSortOptionModel(
            label = SettingsTextCopy.newCardSortLabel(mode),
            mode = mode,
            description = SettingsTextCopy.newCardSortDescription(mode)
        )
    }

    private fun saveNewCardSort(mode: String) {
        val request = NewCardSortSettingsPolicy.saveRequest(mode)
        activity.runSettingsWrite(
            traceSection = "kani.settings.new-card-sort.save",
            write = {
                activity.store.saveNewCardSortMode(request.mode)
            },
        ) {
            Toast.makeText(activity, request.message, Toast.LENGTH_SHORT).show()
            activity.renderSettingsStudyBehavior(true)
        }
    }
}