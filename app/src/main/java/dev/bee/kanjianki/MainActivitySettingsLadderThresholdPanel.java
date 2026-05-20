package dev.bee.kanjianki;

import android.view.View;
import android.widget.EditText;
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
        EditText promotionDays = activity.thresholdInput(current.ladderPromotionIntervalDays);
        EditText failStreak = activity.thresholdInput(current.ladderDemotionFailStreak);
        promotionDays.setContentDescription(SettingsTextCopy.fsrsDaysToGoUpLabel());
        failStreak.setContentDescription(SettingsTextCopy.failsToGoDownLabel());
        return MainActivitySettingsLadderThresholdCompose.ladderThresholdSettingsPanelView(
                activity,
                new SettingsLadderThresholdPanelModel(
                        SettingsTextCopy.ladderThresholdsTitle(),
                        SettingsTextCopy.ladderThresholdsBody(),
                        SettingsTextCopy.fsrsDaysToGoUpLabel(),
                        promotionDays,
                        SettingsTextCopy.failsToGoDownLabel(),
                        failStreak,
                        SettingsTextCopy.useDefaultLadderThresholdsLabel(),
                        SettingsTextCopy.saveLadderThresholdsLabel(),
                        () -> applyDefaultThresholds(promotionDays, failStreak),
                        () -> saveLadderThresholds(promotionDays, failStreak)
                )
        );
    }

    private void applyDefaultThresholds(EditText promotionDays, EditText failStreak) {
        promotionDays.setText(String.format(Locale.ROOT, "%d", RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS));
        failStreak.setText(String.format(Locale.ROOT, "%d", RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK));
    }

    private void saveLadderThresholds(EditText promotionDays, EditText failStreak) {
        StudyLadderThresholdPolicy.SaveResult request = StudyLadderThresholdPolicy.saveRequest(
                promotionDays.getText().toString(),
                failStreak.getText().toString()
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
