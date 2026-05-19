package dev.bee.kanjianki;

import android.widget.SeekBar;
import android.widget.TextView;

import dev.bee.kanjianki.core.SettingsTextCopy;

final class MainActivitySettingsRetentionSlider {
    private final MainActivitySettings activity;

    MainActivitySettingsRetentionSlider(MainActivitySettings activity) {
        this.activity = activity;
    }

    void bindRetentionSlider(int[] selected, TextView status, SeekBar slider) {
        slider.setMax(17);
        slider.setProgress(selected[0] - 80);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selected[0] = 80 + progress;
                status.setText(SettingsTextCopy.retentionStatusText(selected[0]));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Drag-start has no side effects; live updates happen as progress changes.
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Drag-stop has no side effects; selected retention is already updated.
            }
        });
    }
}
