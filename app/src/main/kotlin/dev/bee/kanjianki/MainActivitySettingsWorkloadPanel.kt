package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.core.AdaptiveLoadPlanner
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.core.WorkloadSettingsPolicy

internal class MainActivitySettingsWorkloadPanel(private val activity: MainActivitySettings) {
    fun workloadSettingsPanelModel(): SettingsWorkloadPanelModel {
        val current = activity.store.adaptiveLoadWorkPercent()
        val currentMax = activity.store.adaptiveLoadMaxItems()
        val autoMode = AdaptiveLoadPlanner.isAutoMode(activity.store.adaptiveLoadMode())
        val selected = intArrayOf(current)
        val selectedMax = intArrayOf(currentMax)
        val writer = WorkloadSettingsStoreWriter(activity)
        return SettingsWorkloadPanelModel(
            title = SettingsTextCopy.dailyWorkloadTitle(),
            autoMode = autoMode,
            autoStatus = autoWorkloadStatus(autoMode),
            automaticBody = SettingsTextCopy.automaticWorkloadBody(),
            manualBody = SettingsTextCopy.manualWorkloadBody(),
            selectedWorkloadPercent = selected,
            selectedMaxItems = selectedMax,
            scaleLabels = SettingsTextCopy.workloadScaleLabels().toList(),
            saveMaximumLabel = SettingsTextCopy.saveMaximumLabel(),
            manualWorkloadLabel = SettingsTextCopy.manualWorkloadLabel(),
            saveWorkloadLabel = SettingsTextCopy.saveWorkloadLabel(),
            automaticParetoLabel = SettingsTextCopy.automaticParetoLabel(),
            onSaveMaximum = SettingsWorkloadAction { saveMaximumWorkload(selectedMax, writer) },
            onEnableManual = SettingsWorkloadAction { enableManualWorkload(writer) },
            onSaveWorkload = SettingsWorkloadAction { saveManualWorkload(selected, selectedMax, writer) },
            onEnableAutomatic = SettingsWorkloadAction { enableAutomaticWorkload(writer) }
        )
    }

    private fun autoWorkloadStatus(autoMode: Boolean): String {
        if (!autoMode) {
            return ""
        }
        val rows = activity.store.activeDashboardRows()
        val plan = if (rows.isEmpty()) {
            null
        } else {
            activity.adaptivePlan(rows, activity.store.studyItemsForKanji(rows.map { it.kanji }), System.currentTimeMillis())
        }
        return SettingsTextCopy.autoWorkloadStatusText(plan)
    }

    private fun saveMaximumWorkload(
        selectedMax: IntArray,
        writer: SettingsWriteActions.WorkloadSettingsWriter,
    ) {
        saveWorkloadRequest(WorkloadSettingsPolicy.saveMaximum(selectedMax[0]), writer)
    }

    private fun enableManualWorkload(writer: SettingsWriteActions.WorkloadSettingsWriter) {
        saveWorkloadRequest(WorkloadSettingsPolicy.enableManualMode(), writer)
    }

    private fun saveManualWorkload(
        selected: IntArray,
        selectedMax: IntArray,
        writer: SettingsWriteActions.WorkloadSettingsWriter,
    ) {
        saveWorkloadRequest(WorkloadSettingsPolicy.saveManualWorkload(selected[0], selectedMax[0]), writer)
    }

    private fun enableAutomaticWorkload(writer: SettingsWriteActions.WorkloadSettingsWriter) {
        saveWorkloadRequest(WorkloadSettingsPolicy.enableAutomaticMode(), writer)
    }

    private fun saveWorkloadRequest(
        request: WorkloadSettingsPolicy.SaveRequest,
        writer: SettingsWriteActions.WorkloadSettingsWriter,
    ) {
        activity.runSettingsWrite(
            traceSection = "kani.settings.workload.save",
            write = {
                SettingsWriteActions.saveWorkload(request, writer)
            },
        ) {
            Toast.makeText(activity, request.message, Toast.LENGTH_SHORT).show()
            activity.renderSettingsStudyBehavior(true)
        }
    }

    private class WorkloadSettingsStoreWriter(
        private val activity: MainActivitySettings,
    ) : SettingsWriteActions.WorkloadSettingsWriter {
        override fun saveAdaptiveLoadMode(mode: String) {
            activity.store.saveAdaptiveLoadMode(mode)
        }

        override fun saveAdaptiveLoadWorkPercent(workloadPercent: Int) {
            activity.store.saveAdaptiveLoadWorkPercent(workloadPercent)
        }

        override fun saveAdaptiveLoadMaxItems(maxItems: Int) {
            activity.store.saveAdaptiveLoadMaxItems(maxItems)
        }
    }
}
