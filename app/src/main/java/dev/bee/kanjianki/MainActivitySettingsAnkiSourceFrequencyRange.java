package dev.bee.kanjianki;

import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.core.SettingsInputRules;
import dev.bee.kanjianki.core.SettingsTextCopy;

final class MainActivitySettingsAnkiSourceFrequencyRange {
    private final MainActivitySettings activity;
    private final MainActivitySettingsAnkiSourceInputs inputs;
    private final MainActivitySettingsAnkiSourceValidation validation;
    private final MainActivitySettingsAnkiSourceFrequencyRangeActions actions;

    MainActivitySettingsAnkiSourceFrequencyRange(
            MainActivitySettings activity,
            MainActivitySettingsAnkiSourceInputs inputs,
            MainActivitySettingsAnkiSourceValidation validation
    ) {
        this.activity = activity;
        this.inputs = inputs;
        this.validation = validation;
        this.actions = new MainActivitySettingsAnkiSourceFrequencyRangeActions(activity);
    }

    LinearLayout frequencyRangeSettingsPanel(RecordsSyncModels.Settings current) {
        LinearLayout box = activity.settingsPanelBox();
        final int[] selected = new int[]{current.suspendedRankMin, current.suspendedRankMax};
        box.addView(activity.text(SettingsTextCopy.frequencyRangeTitle(), 23, activity.INK, true));
        TextView status = activity.text(SettingsTextCopy.frequencyRangeStatusText(selected[0], selected[1]), 17, activity.TEAL, true);
        box.addView(status);
        box.addView(activity.text(SettingsTextCopy.frequencyRangeBody(), 15, activity.MUTED, false));

        LinearLayout inputRow = new LinearLayout(activity);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout minColumn = new LinearLayout(activity);
        minColumn.setOrientation(LinearLayout.VERTICAL);
        minColumn.addView(activity.text(SettingsTextCopy.minRankLabel(), 15, activity.INK, true));
        EditText minInput = inputs.rankInput(selected[0]);
        minColumn.addView(minInput, new LinearLayout.LayoutParams(-1, activity.dp(58)));
        inputRow.addView(minColumn, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout maxColumn = new LinearLayout(activity);
        maxColumn.setOrientation(LinearLayout.VERTICAL);
        maxColumn.setPadding(activity.dp(10), 0, 0, 0);
        maxColumn.addView(activity.text(SettingsTextCopy.maxRankLabel(), 15, activity.INK, true));
        EditText maxInput = inputs.rankInput(selected[1]);
        maxColumn.addView(maxInput, new LinearLayout.LayoutParams(-1, activity.dp(58)));
        inputRow.addView(maxColumn, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(inputRow);

        box.addView(activity.text(SettingsTextCopy.minimumRankLabel(), 14, activity.MUTED, true));
        SeekBar minSlider = new SeekBar(activity);
        box.addView(minSlider, new LinearLayout.LayoutParams(-1, activity.dp(56)));
        box.addView(activity.text(SettingsTextCopy.maximumRankLabel(), 14, activity.MUTED, true));
        SeekBar maxSlider = new SeekBar(activity);
        box.addView(maxSlider, new LinearLayout.LayoutParams(-1, activity.dp(56)));
        inputs.bindRankSliders(selected, status, minInput, maxInput, minSlider, maxSlider);

        Button save = activity.primaryButton(SettingsTextCopy.saveFrequencyRangeLabel(), activity.STUDY_PINK_DARK);
        save.setOnClickListener(v -> {
            int minRank;
            int maxRank;
            try {
                minRank = validation.parseRankInput(minInput);
                maxRank = validation.parseRankInput(maxInput);
            } catch (NumberFormatException error) {
                Toast.makeText(activity, SettingsTextCopy.numericRanksToast(), Toast.LENGTH_SHORT).show();
                return;
            }
            if (!SettingsInputRules.validRank(minRank) || !SettingsInputRules.validRank(maxRank)) {
                Toast.makeText(activity, SettingsTextCopy.rankRangeToast(), Toast.LENGTH_SHORT).show();
                return;
            }
            SettingsInputRules.RankRange rankRange = SettingsInputRules.normalizedRankRange(minRank, maxRank);
            actions.saveFrequencyRange(rankRange);
        });
        box.addView(save);
        return box;
    }
}
