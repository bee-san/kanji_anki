package dev.bee.kanjianki;

import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.SettingsTextCopy;
import dev.bee.kanjianki.core.WorkloadSettingsPolicy;

import java.util.List;

final class MainActivitySettingsWorkloadPanel {
    private final MainActivitySettings activity;
    private final MainActivitySettingsWorkloadSliders sliders;

    MainActivitySettingsWorkloadPanel(MainActivitySettings activity) {
        this.activity = activity;
        this.sliders = new MainActivitySettingsWorkloadSliders(activity);
    }

    LinearLayout workloadSettingsPanel() {
        int current = activity.store.adaptiveLoadWorkPercent();
        int currentMax = activity.store.adaptiveLoadMaxItems();
        boolean autoMode = AdaptiveLoadPlanner.isAutoMode(activity.store.adaptiveLoadMode());
        final int[] selected = new int[]{current};
        final int[] selectedMax = new int[]{currentMax};
        SettingsWriteActions.WorkloadSettingsWriter writer = new MainActivitySettingsWorkloadWriter(activity);
        LinearLayout box = activity.settingsPanelBox();
        box.addView(activity.text(SettingsTextCopy.dailyWorkloadTitle(), 23, activity.INK, true));

        if (autoMode) {
            long now = System.currentTimeMillis();
            List<RecordsImportModels.DashboardRow> rows = activity.store.activeDashboardRows();
            RecordsSchedulerModels.AdaptiveLoadPlan plan = rows.isEmpty()
                    ? null
                    : activity.adaptivePlan(rows, activity.store.studyItems(), now);
            box.addView(activity.text(SettingsTextCopy.autoWorkloadStatusText(plan), 17, activity.TEAL, true));
            box.addView(activity.text(SettingsTextCopy.automaticWorkloadBody(), 15, activity.MUTED, false));
            addMaxItemsControl(box, selectedMax, null, null);
            Button saveMax = activity.primaryButton(SettingsTextCopy.saveMaximumLabel(), activity.STUDY_PINK_DARK);
            saveMax.setOnClickListener(new RunnableClickListener(() -> saveMaximumWorkload(selectedMax, writer)));
            box.addView(saveMax);
            Button manual = activity.secondaryButton(SettingsTextCopy.manualWorkloadLabel());
            manual.setOnClickListener(new RunnableClickListener(() -> enableManualWorkload(writer)));
            box.addView(manual);
            return box;
        }

        TextView status = activity.text(SettingsTextCopy.workloadStatusText(selected[0], selectedMax[0]), 17, activity.TEAL, true);
        box.addView(status);
        box.addView(activity.text(SettingsTextCopy.manualWorkloadBody(), 15, activity.MUTED, false));

        SeekBar slider = new SeekBar(activity);
        slider.setMax(100);
        slider.setProgress(current);
        sliders.bindWorkloadSlider(selected, selectedMax, status, slider);
        box.addView(slider, new LinearLayout.LayoutParams(-1, activity.dp(56)));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.HORIZONTAL);
        for (String label : SettingsTextCopy.workloadScaleLabels()) {
            TextView item = activity.text(label, 11, activity.MUTED, false);
            item.setGravity(Gravity.CENTER);
            labels.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
        }
        box.addView(labels);

        addMaxItemsControl(box, selectedMax, status, selected);

        Button save = activity.primaryButton(SettingsTextCopy.saveWorkloadLabel(), activity.STUDY_PINK_DARK);
        save.setOnClickListener(new RunnableClickListener(() -> saveManualWorkload(selected, selectedMax, writer)));
        box.addView(save);
        Button automatic = activity.secondaryButton(SettingsTextCopy.automaticParetoLabel());
        automatic.setOnClickListener(new RunnableClickListener(() -> enableAutomaticWorkload(writer)));
        box.addView(automatic);
        return box;
    }

    void addMaxItemsControl(LinearLayout box, int[] selectedMax, TextView workloadStatus, int[] selectedWorkload) {
        sliders.addMaxItemsControl(box, selectedMax, workloadStatus, selectedWorkload);
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

    private static final class RunnableClickListener implements View.OnClickListener {
        private final Runnable action;

        RunnableClickListener(Runnable action) {
            this.action = action;
        }

        @Override
        public void onClick(View v) {
            action.run();
        }
    }
}
