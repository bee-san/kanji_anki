package dev.bee.kanjianki;

import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import dev.bee.kanjianki.core.SettingsInputRules;
import dev.bee.kanjianki.core.SettingsTextCopy;

import java.util.Locale;

final class MainActivitySettingsAnkiSourceRankSliders {
    private final MainActivitySettings activity;

    MainActivitySettingsAnkiSourceRankSliders(MainActivitySettings activity) {
        this.activity = activity;
    }

    void bindRankSliders(
            int[] selected,
            TextView status,
            EditText minInput,
            EditText maxInput,
            SeekBar minSlider,
            SeekBar maxSlider
    ) {
        minSlider.setMax(19999);
        maxSlider.setMax(19999);
        minSlider.setProgress(SettingsInputRules.rankSliderProgress(selected[0]));
        maxSlider.setProgress(SettingsInputRules.rankSliderProgress(selected[1]));

        minSlider.setOnSeekBarChangeListener(new MinRankSliderChangeListener(selected, status, minInput));
        maxSlider.setOnSeekBarChangeListener(new MaxRankSliderChangeListener(selected, status, maxInput));
    }

    private static final class MinRankSliderChangeListener implements SeekBar.OnSeekBarChangeListener {
        private final int[] selected;
        private final TextView status;
        private final EditText minInput;

        MinRankSliderChangeListener(int[] selected, TextView status, EditText minInput) {
            this.selected = selected;
            this.status = status;
            this.minInput = minInput;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            selected[0] = Math.min(SettingsInputRules.rankFromSliderProgress(progress), selected[1]);
            minInput.setText(String.format(Locale.ROOT, "%d", selected[0]));
            status.setText(SettingsTextCopy.frequencyRangeStatusText(selected[0], selected[1]));
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // Drag-start has no side effects; live updates happen as progress changes.
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            seekBar.setProgress(SettingsInputRules.rankSliderProgress(selected[0]));
        }
    }

    private static final class MaxRankSliderChangeListener implements SeekBar.OnSeekBarChangeListener {
        private final int[] selected;
        private final TextView status;
        private final EditText maxInput;

        MaxRankSliderChangeListener(int[] selected, TextView status, EditText maxInput) {
            this.selected = selected;
            this.status = status;
            this.maxInput = maxInput;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            selected[1] = Math.max(SettingsInputRules.rankFromSliderProgress(progress), selected[0]);
            maxInput.setText(String.format(Locale.ROOT, "%d", selected[1]));
            status.setText(SettingsTextCopy.frequencyRangeStatusText(selected[0], selected[1]));
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // Drag-start has no side effects; live updates happen as progress changes.
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            seekBar.setProgress(SettingsInputRules.rankSliderProgress(selected[1]));
        }
    }
}
