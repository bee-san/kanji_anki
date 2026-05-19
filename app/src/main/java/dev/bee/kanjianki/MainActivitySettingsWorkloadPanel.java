package dev.bee.kanjianki;

import android.view.Gravity;
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

    MainActivitySettingsWorkloadPanel(MainActivitySettings activity) {
        this.activity = activity;
    }

    LinearLayout workloadSettingsPanel() {
        int current = activity.store.adaptiveLoadWorkPercent();
        int currentMax = activity.store.adaptiveLoadMaxItems();
        boolean autoMode = AdaptiveLoadPlanner.isAutoMode(activity.store.adaptiveLoadMode());
        final int[] selected = new int[]{current};
        final int[] selectedMax = new int[]{currentMax};
        SettingsWriteActions.WorkloadSettingsWriter writer = new SettingsWriteActions.WorkloadSettingsWriter() {
            @Override
            public void saveAdaptiveLoadMode(String mode) {
                activity.store.saveAdaptiveLoadMode(mode);
            }

            @Override
            public void saveAdaptiveLoadWorkPercent(int workloadPercent) {
                activity.store.saveAdaptiveLoadWorkPercent(workloadPercent);
            }

            @Override
            public void saveAdaptiveLoadMaxItems(int maxItems) {
                activity.store.saveAdaptiveLoadMaxItems(maxItems);
            }
        };
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
            saveMax.setOnClickListener(v -> {
                WorkloadSettingsPolicy.SaveRequest request = WorkloadSettingsPolicy.saveMaximum(selectedMax[0]);
                SettingsWriteActions.saveWorkload(request, writer);
                Toast.makeText(activity, request.message, Toast.LENGTH_SHORT).show();
                activity.renderSettings();
            });
            box.addView(saveMax);
            Button manual = activity.secondaryButton(SettingsTextCopy.manualWorkloadLabel());
            manual.setOnClickListener(v -> {
                WorkloadSettingsPolicy.SaveRequest request = WorkloadSettingsPolicy.enableManualMode();
                SettingsWriteActions.saveWorkload(request, writer);
                Toast.makeText(activity, request.message, Toast.LENGTH_SHORT).show();
                activity.renderSettings();
            });
            box.addView(manual);
            return box;
        }

        TextView status = activity.text(SettingsTextCopy.workloadStatusText(selected[0], selectedMax[0]), 17, activity.TEAL, true);
        box.addView(status);
        box.addView(activity.text(SettingsTextCopy.manualWorkloadBody(), 15, activity.MUTED, false));

        SeekBar slider = new SeekBar(activity);
        slider.setMax(100);
        slider.setProgress(current);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selected[0] = AdaptiveLoadPlanner.snapWorkloadPercent(progress);
                status.setText(SettingsTextCopy.workloadStatusText(selected[0], selectedMax[0]));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Drag-start has no side effects; live updates happen as progress changes.
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekBar.setProgress(selected[0]);
            }
        });
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
        save.setOnClickListener(v -> {
            WorkloadSettingsPolicy.SaveRequest request = WorkloadSettingsPolicy.saveManualWorkload(selected[0], selectedMax[0]);
            SettingsWriteActions.saveWorkload(request, writer);
            Toast.makeText(activity, request.message, Toast.LENGTH_SHORT).show();
            activity.renderSettings();
        });
        box.addView(save);
        Button automatic = activity.secondaryButton(SettingsTextCopy.automaticParetoLabel());
        automatic.setOnClickListener(v -> {
            WorkloadSettingsPolicy.SaveRequest request = WorkloadSettingsPolicy.enableAutomaticMode();
            SettingsWriteActions.saveWorkload(request, writer);
            Toast.makeText(activity, request.message, Toast.LENGTH_SHORT).show();
            activity.renderSettings();
        });
        box.addView(automatic);
        return box;
    }

    void addMaxItemsControl(LinearLayout box, int[] selectedMax, TextView workloadStatus, int[] selectedWorkload) {
        TextView maxStatus = activity.text(SettingsTextCopy.maxItemsStatusText(selectedMax[0]), 17, activity.TEAL, true);
        maxStatus.setPadding(0, activity.dp(8), 0, 0);
        box.addView(maxStatus);

        SeekBar maxSlider = new SeekBar(activity);
        maxSlider.setMax(AdaptiveLoadPlanner.MAX_MAX_ITEMS - AdaptiveLoadPlanner.MIN_MAX_ITEMS);
        maxSlider.setProgress(selectedMax[0] - AdaptiveLoadPlanner.MIN_MAX_ITEMS);
        maxSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selectedMax[0] = AdaptiveLoadPlanner.normalizeMaxItems(progress + AdaptiveLoadPlanner.MIN_MAX_ITEMS);
                maxStatus.setText(SettingsTextCopy.maxItemsStatusText(selectedMax[0]));
                if (workloadStatus != null && selectedWorkload != null) {
                    workloadStatus.setText(SettingsTextCopy.workloadStatusText(selectedWorkload[0], selectedMax[0]));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Drag-start has no side effects; live updates happen as progress changes.
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekBar.setProgress(selectedMax[0] - AdaptiveLoadPlanner.MIN_MAX_ITEMS);
            }
        });
        box.addView(maxSlider, new LinearLayout.LayoutParams(-1, activity.dp(56)));
    }
}
