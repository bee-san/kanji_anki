package dev.bee.kanjianki;

import android.view.View;
import android.widget.EditText;
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

    View frequencyRangeSettingsPanel(RecordsSyncModels.Settings current) {
        final int[] selected = new int[]{current.suspendedRankMin, current.suspendedRankMax};
        EditText minInput = inputs.rankInput(selected[0]);
        EditText maxInput = inputs.rankInput(selected[1]);
        minInput.setContentDescription(SettingsTextCopy.minRankLabel());
        maxInput.setContentDescription(SettingsTextCopy.maxRankLabel());
        return MainActivitySettingsAnkiSourceFrequencyRangeCompose.frequencyRangeSettingsPanelView(
                activity,
                new SettingsFrequencyRangePanelModel(
                        SettingsTextCopy.frequencyRangeTitle(),
                        SettingsTextCopy.frequencyRangeBody(),
                        selected,
                        SettingsTextCopy.minRankLabel(),
                        minInput,
                        SettingsTextCopy.maxRankLabel(),
                        maxInput,
                        SettingsTextCopy.minimumRankLabel(),
                        SettingsTextCopy.maximumRankLabel(),
                        SettingsTextCopy.saveFrequencyRangeLabel(),
                        () -> saveFrequencyRange(minInput, maxInput)
                )
        );
    }

    private void saveFrequencyRange(EditText minInput, EditText maxInput) {
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
    }
}
