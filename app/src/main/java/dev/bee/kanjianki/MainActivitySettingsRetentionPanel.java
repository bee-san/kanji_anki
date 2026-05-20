package dev.bee.kanjianki;

import android.view.View;
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
        SettingsRetentionState state = new SettingsRetentionState(
                current.frequencyRetentionEnabled,
                rankRetentionRangesText(current.frequencyRetentionRanges)
        );
        return MainActivitySettingsRetentionCompose.retentionSettingsPanelView(
                activity,
                new SettingsRetentionPanelModel(
                        SettingsTextCopy.fsrsRetentionTitle(),
                        SettingsTextCopy.fsrsRetentionBody(),
                        selected,
                        new int[]{85, 90, 95},
                        state,
                        SettingsTextCopy.useJitenRankRetentionRangesLabel(),
                        SettingsTextCopy.jitenRankRetentionRangesBody(),
                        FrequencyRetentionRanges.exampleText(),
                        SettingsTextCopy.useExampleRangesLabel(),
                        SettingsTextCopy.saveRetentionLabel(),
                        this::saveRetention
                )
        );
    }

    private static String rankRetentionRangesText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return FrequencyRetentionRanges.exampleText();
        }
        return value.trim();
    }

    private void saveRetention(int retentionPercent, boolean rankRetentionEnabled, String rankRanges) {
        RetentionSettingsPolicy.SaveResult request = RetentionSettingsPolicy.saveRequest(
                retentionPercent,
                rankRetentionEnabled,
                rankRanges,
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
