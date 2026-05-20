package dev.bee.kanjianki;

import android.view.View;
import android.widget.Toast;

import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.core.SettingsInputRules;
import dev.bee.kanjianki.core.SettingsTextCopy;

final class MainActivitySettingsAnkiSourceFrequencyRange {
    private final MainActivitySettings activity;
    private final MainActivitySettingsAnkiSourceValidation validation;
    private final MainActivitySettingsAnkiSourceFrequencyRangeActions actions;

    MainActivitySettingsAnkiSourceFrequencyRange(
            MainActivitySettings activity,
            MainActivitySettingsAnkiSourceValidation validation
    ) {
        this.activity = activity;
        this.validation = validation;
        this.actions = new MainActivitySettingsAnkiSourceFrequencyRangeActions(activity);
    }

    View frequencyRangeSettingsPanel(RecordsSyncModels.Settings current) {
        return MainActivitySettingsAnkiSourceFrequencyRangeCompose.frequencyRangeSettingsPanelView(
                activity,
                frequencyRangeSettingsPanelModel(current)
        );
    }

    SettingsFrequencyRangePanelModel frequencyRangeSettingsPanelModel(RecordsSyncModels.Settings current) {
        final int[] selected = new int[]{current.suspendedRankMin, current.suspendedRankMax};
        return new SettingsFrequencyRangePanelModel(
                SettingsTextCopy.frequencyRangeTitle(),
                SettingsTextCopy.frequencyRangeBody(),
                selected,
                SettingsTextCopy.minRankLabel(),
                Integer.toString(selected[0]),
                SettingsTextCopy.maxRankLabel(),
                Integer.toString(selected[1]),
                SettingsTextCopy.minimumRankLabel(),
                SettingsTextCopy.maximumRankLabel(),
                SettingsTextCopy.saveFrequencyRangeLabel(),
                this::saveFrequencyRange
        );
    }

    private void saveFrequencyRange(String minRankText, String maxRankText) {
        int minRank;
        int maxRank;
        try {
            minRank = validation.parseRankText(minRankText);
            maxRank = validation.parseRankText(maxRankText);
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
