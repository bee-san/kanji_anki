package dev.bee.kanjianki;

import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import dev.bee.kanjianki.core.FrequencyRetentionRanges;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RetentionSettingsPolicy;
import dev.bee.kanjianki.core.SettingsInputRules;
import dev.bee.kanjianki.core.SettingsTextCopy;

final class MainActivitySettingsRetentionPanel {
    private final MainActivitySettings activity;

    MainActivitySettingsRetentionPanel(MainActivitySettings activity) {
        this.activity = activity;
    }

    View retentionSettingsPanel() {
        RecordsSchedulerModels.SchedulerParameters current = activity.store.schedulerParameters();
        final int[] selected = new int[]{SettingsInputRules.retentionPercent(current.targetRetention)};
        CheckBox rankRetentionEnabled = activity.importFilterCheckBox(
                SettingsTextCopy.useJitenRankRetentionRangesLabel(),
                current.frequencyRetentionEnabled
        );
        EditText rankRanges = rankRetentionRangesInput(current.frequencyRetentionRanges);
        rankRanges.setContentDescription(SettingsTextCopy.useJitenRankRetentionRangesLabel());
        return MainActivitySettingsRetentionCompose.retentionSettingsPanelView(
                activity,
                new SettingsRetentionPanelModel(
                        SettingsTextCopy.fsrsRetentionTitle(),
                        SettingsTextCopy.fsrsRetentionBody(),
                        selected,
                        new int[]{85, 90, 95},
                        rankRetentionEnabled,
                        SettingsTextCopy.jitenRankRetentionRangesBody(),
                        rankRanges,
                        SettingsTextCopy.useExampleRangesLabel(),
                        SettingsTextCopy.saveRetentionLabel(),
                        () -> rankRanges.setText(FrequencyRetentionRanges.exampleText()),
                        () -> saveRetention(selected, rankRetentionEnabled, rankRanges)
                )
        );
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
}
