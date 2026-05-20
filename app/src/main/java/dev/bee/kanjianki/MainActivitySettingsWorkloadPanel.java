package dev.bee.kanjianki;

import android.view.View;
import android.widget.SeekBar;
import android.widget.Toast;

import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.SettingsTextCopy;
import dev.bee.kanjianki.core.WorkloadSettingsPolicy;

import java.util.Arrays;
import java.util.List;

final class MainActivitySettingsWorkloadPanel {
    private final MainActivitySettings activity;

    MainActivitySettingsWorkloadPanel(MainActivitySettings activity) {
        this.activity = activity;
    }

    View workloadSettingsPanel() {
        int current = activity.store.adaptiveLoadWorkPercent();
        int currentMax = activity.store.adaptiveLoadMaxItems();
        boolean autoMode = AdaptiveLoadPlanner.isAutoMode(activity.store.adaptiveLoadMode());
        final int[] selected = new int[]{current};
        final int[] selectedMax = new int[]{currentMax};
        SettingsWriteActions.WorkloadSettingsWriter writer = new MainActivitySettingsWorkloadWriter(activity);
        String autoStatus;
        if (autoMode) {
            long now = System.currentTimeMillis();
            List<RecordsImportModels.DashboardRow> rows = activity.store.activeDashboardRows();
            RecordsSchedulerModels.AdaptiveLoadPlan plan = rows.isEmpty()
                    ? null
                    : activity.adaptivePlan(rows, activity.store.studyItems(), now);
            autoStatus = SettingsTextCopy.autoWorkloadStatusText(plan);
        } else {
            autoStatus = "";
        }
        return MainActivitySettingsWorkloadCompose.workloadSettingsPanelView(
                activity,
                new SettingsWorkloadPanelModel(
                        SettingsTextCopy.dailyWorkloadTitle(),
                        autoMode,
                        autoStatus,
                        SettingsTextCopy.automaticWorkloadBody(),
                        SettingsTextCopy.manualWorkloadBody(),
                        selected,
                        selectedMax,
                        new SeekBar(activity),
                        new SeekBar(activity),
                        Arrays.asList(SettingsTextCopy.workloadScaleLabels()),
                        SettingsTextCopy.saveMaximumLabel(),
                        SettingsTextCopy.manualWorkloadLabel(),
                        SettingsTextCopy.saveWorkloadLabel(),
                        SettingsTextCopy.automaticParetoLabel(),
                        () -> saveMaximumWorkload(selectedMax, writer),
                        () -> enableManualWorkload(writer),
                        () -> saveManualWorkload(selected, selectedMax, writer),
                        () -> enableAutomaticWorkload(writer)
                )
        );
    }

    private void saveMaximumWorkload(int[] selectedMax, SettingsWriteActions.WorkloadSettingsWriter writer) {
        WorkloadSettingsPolicy.SaveRequest request = WorkloadSettingsPolicy.saveMaximum(selectedMax[0]);
        SettingsWriteActions.saveWorkload(request, writer);
        Toast.makeText(activity, request.message, Toast.LENGTH_SHORT).show();
        activity.renderSettings();
    }

    private void enableManualWorkload(SettingsWriteActions.WorkloadSettingsWriter writer) {
        WorkloadSettingsPolicy.SaveRequest request = WorkloadSettingsPolicy.enableManualMode();
        SettingsWriteActions.saveWorkload(request, writer);
        Toast.makeText(activity, request.message, Toast.LENGTH_SHORT).show();
        activity.renderSettings();
    }

    private void saveManualWorkload(int[] selected, int[] selectedMax, SettingsWriteActions.WorkloadSettingsWriter writer) {
        WorkloadSettingsPolicy.SaveRequest request = WorkloadSettingsPolicy.saveManualWorkload(selected[0], selectedMax[0]);
        SettingsWriteActions.saveWorkload(request, writer);
        Toast.makeText(activity, request.message, Toast.LENGTH_SHORT).show();
        activity.renderSettings();
    }

    private void enableAutomaticWorkload(SettingsWriteActions.WorkloadSettingsWriter writer) {
        WorkloadSettingsPolicy.SaveRequest request = WorkloadSettingsPolicy.enableAutomaticMode();
        SettingsWriteActions.saveWorkload(request, writer);
        Toast.makeText(activity, request.message, Toast.LENGTH_SHORT).show();
        activity.renderSettings();
    }

}
