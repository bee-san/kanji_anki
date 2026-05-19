package dev.bee.kanjianki;

import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.SettingsTextCopy;

final class MainActivitySettingsWorkloadSliders {
    private final MainActivitySettings activity;

    MainActivitySettingsWorkloadSliders(MainActivitySettings activity) {
        this.activity = activity;
    }

    void bindWorkloadSlider(int[] selected, int[] selectedMax, TextView status, SeekBar slider) {
        slider.setOnSeekBarChangeListener(new WorkloadSliderChangeListener(selected, selectedMax, status));
    }

    void addMaxItemsControl(LinearLayout box, int[] selectedMax, TextView workloadStatus, int[] selectedWorkload) {
        TextView maxStatus = activity.text(SettingsTextCopy.maxItemsStatusText(selectedMax[0]), 17, activity.TEAL, true);
        maxStatus.setPadding(0, activity.dp(8), 0, 0);
        box.addView(maxStatus);

        SeekBar maxSlider = new SeekBar(activity);
        maxSlider.setMax(AdaptiveLoadPlanner.MAX_MAX_ITEMS - AdaptiveLoadPlanner.MIN_MAX_ITEMS);
        maxSlider.setProgress(selectedMax[0] - AdaptiveLoadPlanner.MIN_MAX_ITEMS);
        maxSlider.setOnSeekBarChangeListener(new MaxItemsSliderChangeListener(selectedMax, maxStatus, workloadStatus, selectedWorkload));
        box.addView(maxSlider, new LinearLayout.LayoutParams(-1, activity.dp(56)));
    }

    private static final class WorkloadSliderChangeListener implements SeekBar.OnSeekBarChangeListener {
        private final int[] selected;
        private final int[] selectedMax;
        private final TextView status;

        WorkloadSliderChangeListener(int[] selected, int[] selectedMax, TextView status) {
            this.selected = selected;
            this.selectedMax = selectedMax;
            this.status = status;
        }

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
    }

    private static final class MaxItemsSliderChangeListener implements SeekBar.OnSeekBarChangeListener {
        private final int[] selectedMax;
        private final TextView maxStatus;
        private final TextView workloadStatus;
        private final int[] selectedWorkload;

        MaxItemsSliderChangeListener(int[] selectedMax, TextView maxStatus, TextView workloadStatus, int[] selectedWorkload) {
            this.selectedMax = selectedMax;
            this.maxStatus = maxStatus;
            this.workloadStatus = workloadStatus;
            this.selectedWorkload = selectedWorkload;
        }

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
    }
}
