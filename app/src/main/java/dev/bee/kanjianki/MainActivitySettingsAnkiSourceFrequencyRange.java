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
    private final MainActivitySettingsAnkiSource source;

    MainActivitySettingsAnkiSourceFrequencyRange(MainActivitySettings activity, MainActivitySettingsAnkiSource source) {
        this.activity = activity;
        this.source = source;
    }

    LinearLayout frequencyRangeSettingsPanel(RecordsSyncModels.Settings current) {
        LinearLayout box = activity.settingsPanelBox();
        final int[] selected = new int[]{current.suspendedRankMin, current.suspendedRankMax};
        box.addView(activity.text(SettingsTextCopy.frequencyRangeTitle(), 23, activity.INK, true));
        TextView status = activity.text(SettingsTextCopy.frequencyRangeStatusText(selected[0], selected[1]), 17, activity.TEAL, true);
        box.addView(status);
        box.addView(activity.text(SettingsTextCopy.frequencyRangeBody(), 15, activity.MUTED, false));

        LinearLayout inputs = new LinearLayout(activity);
        inputs.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout minColumn = new LinearLayout(activity);
        minColumn.setOrientation(LinearLayout.VERTICAL);
        minColumn.addView(activity.text(SettingsTextCopy.minRankLabel(), 15, activity.INK, true));
        EditText minInput = source.rankInput(selected[0]);
        minColumn.addView(minInput, new LinearLayout.LayoutParams(-1, activity.dp(58)));
        inputs.addView(minColumn, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout maxColumn = new LinearLayout(activity);
        maxColumn.setOrientation(LinearLayout.VERTICAL);
        maxColumn.setPadding(activity.dp(10), 0, 0, 0);
        maxColumn.addView(activity.text(SettingsTextCopy.maxRankLabel(), 15, activity.INK, true));
        EditText maxInput = source.rankInput(selected[1]);
        maxColumn.addView(maxInput, new LinearLayout.LayoutParams(-1, activity.dp(58)));
        inputs.addView(maxColumn, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(inputs);

        box.addView(activity.text(SettingsTextCopy.minimumRankLabel(), 14, activity.MUTED, true));
        SeekBar minSlider = new SeekBar(activity);
        box.addView(minSlider, new LinearLayout.LayoutParams(-1, activity.dp(56)));
        box.addView(activity.text(SettingsTextCopy.maximumRankLabel(), 14, activity.MUTED, true));
        SeekBar maxSlider = new SeekBar(activity);
        box.addView(maxSlider, new LinearLayout.LayoutParams(-1, activity.dp(56)));
        source.bindRankSliders(selected, status, minInput, maxInput, minSlider, maxSlider);

        Button save = activity.primaryButton(SettingsTextCopy.saveFrequencyRangeLabel(), activity.STUDY_PINK_DARK);
        save.setOnClickListener(v -> {
            int minRank;
            int maxRank;
            try {
                minRank = source.parseRankInput(minInput);
                maxRank = source.parseRankInput(maxInput);
            } catch (NumberFormatException error) {
                Toast.makeText(activity, SettingsTextCopy.numericRanksToast(), Toast.LENGTH_SHORT).show();
                return;
            }
            if (!SettingsInputRules.validRank(minRank) || !SettingsInputRules.validRank(maxRank)) {
                Toast.makeText(activity, SettingsTextCopy.rankRangeToast(), Toast.LENGTH_SHORT).show();
                return;
            }
            SettingsInputRules.RankRange rankRange = SettingsInputRules.normalizedRankRange(minRank, maxRank);
            activity.store.putIntSetting("suspended_rank_min", rankRange.minRank());
            activity.store.putIntSetting("suspended_rank_max", rankRange.maxRank());
            Toast.makeText(activity, SettingsTextCopy.frequencyRangeSavedToast(), Toast.LENGTH_LONG).show();
            activity.renderSettings();
        });
        box.addView(save);
        return box;
    }
}
