package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.core.NewCardSortSettingsPolicy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.data.SettingsSaveCommand
import kotlinx.coroutines.runBlocking

internal class MainActivitySettingsStudySortPanel(private val activity: MainActivitySettings) {
    fun newCardSortSettingsPanelModel(current: RecordsSyncModels.Settings): SettingsNewCardSortPanelModel {
        val previewRowsData = activity.cachedNewCardSortPreviewRows
        val previewVersion = runBlocking {
            activity.homeUseCases.loadNewCardSortPreviewVersion()
        }
        if (previewRowsData == null || previewRowsData.sourceVersion != previewVersion) {
            schedulePreviewRefresh()
        }
        return SettingsNewCardSortPanelModel(
            title = SettingsTextCopy.newCardSortTitle(),
            body = SettingsTextCopy.newCardSortBody(),
            initialMode = current.newCardSortMode,
            options = newCardSortOptions(),
            saveLabel = SettingsTextCopy.saveNewCardSortLabel(),
            previewRowsByMode = previewRowsData?.previewRowsByMode.orEmpty(),
            previewWarningsByMode = previewRowsData?.previewWarningsByMode.orEmpty(),
            onSave = SettingsNewCardSortSaver { mode -> saveNewCardSort(mode) }
        )
    }

    private fun schedulePreviewRefresh() {
        if (activity.newCardSortPreviewRefreshPending) {
            return
        }
        activity.newCardSortPreviewRefreshPending = true
        val cached = activity.cachedNewCardSortPreviewRows
        activity.io.execute {
            var previewRowsData: SettingsNewCardSortPreviewRowsSnapshot? = null
            try {
                val data = runBlocking {
                    activity.homeUseCases.loadNewCardSortPreviewData()
                }
                val pairKeys = data.similarPairs.mapTo(HashSet()) {
                    similarPairKey(it.kanjiA, it.kanjiB)
                }
                previewRowsData = SettingsNewCardSortPreviewCache.resolve(
                    rows = data.activeRows,
                    cached = cached,
                    sourceVersion = data.sourceVersion,
                    hasSimilarLocalPair = { first, second ->
                        pairKeys.contains(similarPairKey(first, second))
                    },
                )
            } finally {
                activity.postToMainIfActive {
                    activity.newCardSortPreviewRefreshPending = false
                    previewRowsData?.let { snapshot ->
                        activity.cachedNewCardSortPreviewRows = snapshot
                        if (activity.currentRoute == MainActivityBase.NAV_SETTINGS_STUDY_BEHAVIOR_ROUTE) {
                            if (activity.activityPaused) {
                                activity.newCardSortPreviewRerenderOnResumePending = true
                            } else {
                                activity.newCardSortPreviewRerenderOnResumePending = false
                                activity.renderSettingsStudyBehavior(true)
                            }
                        }
                    }
                }
            }
        }
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
                activity.saveSettings(SettingsSaveCommand.NewCardSort(request.mode))
            },
        ) {
            Toast.makeText(activity, request.message, Toast.LENGTH_SHORT).show()
            activity.renderSettingsStudyBehavior(true)
        }
    }

    private fun similarPairKey(first: String?, second: String?): String {
        val left = first.orEmpty()
        val right = second.orEmpty()
        return if (left <= right) "$left\u0000$right" else "$right\u0000$left"
    }
}
