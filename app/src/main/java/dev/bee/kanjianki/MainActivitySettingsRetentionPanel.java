package dev.bee.kanjianki;

import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import dev.bee.kanjianki.core.FrequencyRetentionRanges;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RetentionSettingsPolicy;
import dev.bee.kanjianki.core.SettingsInputRules;
import dev.bee.kanjianki.core.SettingsTextCopy;

final class MainActivitySettingsRetentionPanel {
    private final MainActivitySettings activity;
    private final MainActivitySettingsRetentionSlider retentionSlider;

    MainActivitySettingsRetentionPanel(MainActivitySettings activity) {
        this.activity = activity;
        this.retentionSlider = new MainActivitySettingsRetentionSlider(activity);
    }

    LinearLayout retentionSettingsPanel() {
        RecordsSchedulerModels.SchedulerParameters current = activity.store.schedulerParameters();
        final int[] selected = new int[]{SettingsInputRules.retentionPercent(current.targetRetention)};
        LinearLayout box = activity.settingsPanelBox();
        box.addView(activity.text(SettingsTextCopy.fsrsRetentionTitle(), 23, activity.INK, true));
        TextView status = activity.text(SettingsTextCopy.retentionStatusText(selected[0]), 17, activity.TEAL, true);
        box.addView(status);
        box.addView(activity.text(SettingsTextCopy.fsrsRetentionBody(), 15, activity.MUTED, false));

        SeekBar slider = new SeekBar(activity);
        retentionSlider.bindRetentionSlider(selected, status, slider);
        box.addView(slider, new LinearLayout.LayoutParams(-1, activity.dp(56)));

        LinearLayout quick = new LinearLayout(activity);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        for (int value : new int[]{85, 90, 95}) {
            Button preset = activity.secondaryButton(SettingsTextCopy.retentionPresetLabel(value));
            preset.setOnClickListener(new RetentionPresetClickListener(value, selected, slider, status));
            quick.addView(preset, new LinearLayout.LayoutParams(0, activity.dp(54), 1));
        }
        box.addView(quick);

        CheckBox rankRetentionEnabled = activity.importFilterCheckBox(SettingsTextCopy.useJitenRankRetentionRangesLabel(), current.frequencyRetentionEnabled);
        box.addView(rankRetentionEnabled);
        box.addView(activity.text(SettingsTextCopy.jitenRankRetentionRangesBody(), 15, activity.MUTED, false));
        EditText rankRanges = rankRetentionRangesInput(current.frequencyRetentionRanges);
        box.addView(rankRanges, new LinearLayout.LayoutParams(-1, activity.dp(132)));

        Button exampleRanges = activity.secondaryButton(SettingsTextCopy.useExampleRangesLabel());
        exampleRanges.setOnClickListener(new RunnableClickListener(() -> rankRanges.setText(FrequencyRetentionRanges.exampleText())));
        box.addView(exampleRanges);

        Button save = activity.primaryButton(SettingsTextCopy.saveRetentionLabel(), activity.STUDY_PINK_DARK);
        save.setOnClickListener(new RunnableClickListener(() -> saveRetention(selected, rankRetentionEnabled, rankRanges)));
        box.addView(save);
        return box;
    }

    EditText rankRetentionRangesInput(String value) {
        EditText input = new EditText(activity);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setText(value == null || value.trim().isEmpty() ? FrequencyRetentionRanges.exampleText() : value.trim());
        input.setTextSize(16);
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setSelectAllOnFocus(false);
        return input;
    }

    private void saveRetention(int[] selected, CheckBox rankRetentionEnabled, EditText rankRanges) {
        RetentionSettingsPolicy.SaveResult request = RetentionSettingsPolicy.saveRequest(
                selected[0],
                rankRetentionEnabled.isChecked(),
                rankRanges.getText().toString(),
                activity.store.schedulerParameters()
        );
        if (!request.valid) {
            Toast.makeText(activity, request.message, Toast.LENGTH_LONG).show();
            return;
        }
        activity.store.saveSchedulerParameters(request.parameters);
        Toast.makeText(activity, request.message, Toast.LENGTH_SHORT).show();
        activity.renderSettings();
    }

    private static final class RetentionPresetClickListener implements View.OnClickListener {
        private final int value;
        private final int[] selected;
        private final SeekBar slider;
        private final TextView status;

        RetentionPresetClickListener(int value, int[] selected, SeekBar slider, TextView status) {
            this.value = value;
            this.selected = selected;
            this.slider = slider;
            this.status = status;
        }

        @Override
        public void onClick(View v) {
            selected[0] = value;
            slider.setProgress(value - 80);
            status.setText(SettingsTextCopy.retentionStatusText(selected[0]));
        }
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
