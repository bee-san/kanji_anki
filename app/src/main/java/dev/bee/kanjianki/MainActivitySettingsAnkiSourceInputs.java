package dev.bee.kanjianki;

import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import dev.bee.kanjianki.core.SettingsInputRules;
import dev.bee.kanjianki.core.SettingsTextCopy;

import java.util.Locale;

final class MainActivitySettingsAnkiSourceInputs {
    private final MainActivitySettings activity;

    MainActivitySettingsAnkiSourceInputs(MainActivitySettings activity) {
        this.activity = activity;
    }

    EditText noteTypeInput(String value) {
        EditText input = new EditText(activity);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setText(value == null || value.trim().isEmpty() ? dev.bee.kanjianki.core.RecordsSyncModels.Settings.kikuDefaults().modelName : value.trim());
        input.setHint(dev.bee.kanjianki.core.RecordsSyncModels.Settings.kikuDefaults().modelName);
        input.setTextSize(20);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        return input;
    }

    EditText fieldInput(String value) {
        EditText input = new EditText(activity);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setText(value == null ? "" : value.trim());
        input.setTextSize(18);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        return input;
    }

    void addFieldMappingInput(LinearLayout box, String label, EditText input) {
        box.addView(activity.text(label, 14, activity.INK, true));
        box.addView(input, new LinearLayout.LayoutParams(-1, activity.dp(52)));
    }

    LinearLayout inputColumn(String label, EditText input, int leftPadding) {
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(leftPadding, 0, 0, 0);
        column.addView(activity.text(label, 15, activity.INK, true));
        column.addView(input, new LinearLayout.LayoutParams(-1, activity.dp(58)));
        return column;
    }

    EditText rankInput(int value) {
        EditText input = new EditText(activity);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.format(Locale.ROOT, "%d", value));
        input.setTextSize(22);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        return input;
    }

    EditText decimalInput(double value) {
        EditText input = new EditText(activity);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(Locale.ROOT, "%.1f", value));
        input.setTextSize(20);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        return input;
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

        minSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
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
        });
        maxSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
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
        });
    }
}
