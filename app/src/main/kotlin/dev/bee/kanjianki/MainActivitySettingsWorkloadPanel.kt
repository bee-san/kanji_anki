package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.core.AdaptiveLoadPlanner
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.core.WorkloadSettingsPolicy
import dev.bee.kanjianki.data.AdaptiveWorkloadSnapshot
import dev.bee.kanjianki.data.SettingsSaveCommand
import dev.bee.kanjianki.data.SettingsSnapshot
import kotlinx.coroutines.runBlocking

internal class MainActivitySettingsWorkloadPanel(private val activity: MainActivitySettings) {
    fun workloadSettingsPanelModel(
        snapshot: SettingsSnapshot = activity.loadSettingsSnapshot(),
    ): SettingsWorkloadPanelModel {
        val currentWorkload = snapshot.adaptiveWorkload
        val current = currentWorkload.workPercent
        val currentMax = currentWorkload.maxItems
        val autoMode = AdaptiveLoadPlanner.isAutoMode(currentWorkload.mode)
        val selected = intArrayOf(current)
        val selectedMax = intArrayOf(currentMax)
        return SettingsWorkloadPanelModel(
            title = SettingsTextCopy.dailyWorkloadTitle(),
            autoMode = autoMode,
            autoStatus = autoWorkloadStatus(autoMode, snapshot),
            automaticBody = SettingsTextCopy.automaticWorkloadBody(),
            manualBody = SettingsTextCopy.manualWorkloadBody(),
            selectedWorkloadPercent = selected,
            selectedMaxItems = selectedMax,
            scaleLabels = SettingsTextCopy.workloadScaleLabels().toList(),
            saveMaximumLabel = SettingsTextCopy.saveMaximumLabel(),
            manualWorkloadLabel = SettingsTextCopy.manualWorkloadLabel(),
            saveWorkloadLabel = SettingsTextCopy.saveWorkloadLabel(),
            automaticParetoLabel = SettingsTextCopy.automaticParetoLabel(),
            onSaveMaximum = SettingsWorkloadAction {
                saveMaximumWorkload(selectedMax, currentWorkload)
            },
            onEnableManual = SettingsWorkloadAction { enableManualWorkload(currentWorkload) },
            onSaveWorkload = SettingsWorkloadAction {
                saveManualWorkload(selected, selectedMax, currentWorkload)
            },
            onEnableAutomatic = SettingsWorkloadAction {
                enableAutomaticWorkload(currentWorkload)
            },
        )
    }

    private fun autoWorkloadStatus(
        autoMode: Boolean,
        settings: SettingsSnapshot,
    ): String {
        if (!autoMode) {
            return ""
        }
        val now = System.currentTimeMillis()
        val home = runBlocking {
            activity.homeUseCases.loadHome(now)
        }
        val study = runBlocking {
            activity.homeUseCases.loadStudyQueue(now)
        }
        val rows = home.activeRows
        val plan = if (rows.isEmpty()) {
            null
        } else {
            MainActivityStudyPlanProvider(activity).adaptivePlan(
                rows = rows,
                items = home.studyItems,
                now = now,
                streakDays = home.studyStreak.currentDays,
                settings = settings.sync,
                reviewStats = study.recentReviewStats,
                studiedKanji = study.studiedKanjiToday,
                workload = settings.adaptiveWorkload,
            )
        }
        return SettingsTextCopy.autoWorkloadStatusText(plan)
    }

    private fun saveMaximumWorkload(
        selectedMax: IntArray,
        current: AdaptiveWorkloadSnapshot,
    ) {
        saveWorkloadRequest(WorkloadSettingsPolicy.saveMaximum(selectedMax[0]), current)
    }

    private fun enableManualWorkload(current: AdaptiveWorkloadSnapshot) {
        saveWorkloadRequest(WorkloadSettingsPolicy.enableManualMode(), current)
    }

    private fun saveManualWorkload(
        selected: IntArray,
        selectedMax: IntArray,
        current: AdaptiveWorkloadSnapshot,
    ) {
        saveWorkloadRequest(
            WorkloadSettingsPolicy.saveManualWorkload(selected[0], selectedMax[0]),
            current,
        )
    }

    private fun enableAutomaticWorkload(current: AdaptiveWorkloadSnapshot) {
        saveWorkloadRequest(WorkloadSettingsPolicy.enableAutomaticMode(), current)
    }

    private fun saveWorkloadRequest(
        request: WorkloadSettingsPolicy.SaveRequest,
        current: AdaptiveWorkloadSnapshot,
    ) {
        activity.runSettingsWrite(
            traceSection = "kani.settings.workload.save",
            write = {
                activity.saveSettings(
                    SettingsSaveCommand.AdaptiveWorkload(
                        AdaptiveWorkloadSnapshot(
                            request.workloadPercent ?: current.workPercent,
                            request.maxItems ?: current.maxItems,
                            request.mode ?: current.mode,
                        ),
                    ),
                )
            },
        ) {
            Toast.makeText(activity, request.message, Toast.LENGTH_SHORT).show()
            activity.renderSettingsStudyBehavior(true)
        }
    }

}
