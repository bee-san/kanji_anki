package dev.bee.kanjianki;

import android.widget.Toast;

import dev.bee.kanjianki.core.SettingsInputRules;
import dev.bee.kanjianki.core.SettingsTextCopy;

final class MainActivitySettingsAnkiSourceFrequencyRangeActions {
    private final MainActivitySettings activity;

    MainActivitySettingsAnkiSourceFrequencyRangeActions(MainActivitySettings activity) {
        this.activity = activity;
    }

    void saveFrequencyRange(SettingsInputRules.RankRange rankRange) {
        activity.store.putIntSetting("suspended_rank_min", rankRange.minRank());
        activity.store.putIntSetting("suspended_rank_max", rankRange.maxRank());
        Toast.makeText(activity, SettingsTextCopy.frequencyRangeSavedToast(), Toast.LENGTH_LONG).show();
        activity.renderSettings();
    }
}
