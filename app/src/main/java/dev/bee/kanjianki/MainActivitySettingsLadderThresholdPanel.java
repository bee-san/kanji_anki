package dev.bee.kanjianki;

import android.view.View;
import android.widget.Toast;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.core.SettingsTextCopy;
import dev.bee.kanjianki.core.StudyLadderThresholdPolicy;

import java.util.Locale;

final class MainActivitySettingsLadderThresholdPanel {
    private final MainActivitySettings activity;

    MainActivitySettingsLadderThresholdPanel(MainActivitySettings activity) {
        this.activity = activity;
    }

    View ladderThresholdSettingsPanel() {
        RecordsSyncModels.Settings current = activity.settings();
        return MainActivitySettingsLadderThresholdCompose.ladderThresholdSettingsPanelView(
                activity,
                new SettingsLadderThresholdPanelModel(
                        SettingsTextCopy.ladderThresholdsTitle(),
                        SettingsTextCopy.ladderThresholdsBody(),
                        SettingsTextCopy.fsrsDaysToGoUpLabel(),
                        thresholdText(current.ladderPromotionIntervalDays),
                        SettingsTextCopy.failsToGoDownLabel(),
                        thresholdText(current.ladderDemotionFailStreak),
                        String.format(Locale.ROOT, "%d", RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS),
                        String.format(Locale.ROOT, "%d", RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK),
                        SettingsTextCopy.useDefaultLadderThresholdsLabel(),
                        SettingsTextCopy.saveLadderThresholdsLabel(),
                        this::saveLadderThresholds
                )
        );
    }

    private static String thresholdText(int value) {
        return String.format(Locale.ROOT, "%d", Math.max(1, value));
    }

    private void saveLadderThresholds(String promotionDaysText, String failStreakText) {
        StudyLadderThresholdPolicy.SaveResult request = StudyLadderThresholdPolicy.saveRequest(
                promotionDaysText,
                failStreakText
        );
        if (!request.valid) {
            Toast.makeText(activity, request.message, Toast.LENGTH_SHORT).show();
            return;
        }
        SettingsWriteActions.saveLadderThresholds(request, activity.store::putIntSetting);
        Toast.makeText(activity, SettingsTextCopy.ladderThresholdsSavedToast(), Toast.LENGTH_SHORT).show();
        activity.renderSettings();
    }
}
