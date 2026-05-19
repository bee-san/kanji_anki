package dev.bee.kanjianki;

import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.SettingsTextCopy;

final class MainActivitySettingsWorkload {
    private final MainActivitySettings activity;
    private final MainActivitySettingsWorkloadPanel workloadPanel;

    MainActivitySettingsWorkload(MainActivitySettings activity) {
        this.activity = activity;
        this.workloadPanel = new MainActivitySettingsWorkloadPanel(activity, this);
    }

    LinearLayout workloadSettingsPanel() {
        return workloadPanel.workloadSettingsPanel();
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
