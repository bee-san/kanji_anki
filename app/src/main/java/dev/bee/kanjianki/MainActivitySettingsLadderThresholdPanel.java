package dev.bee.kanjianki;

import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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

    LinearLayout ladderThresholdSettingsPanel() {
        RecordsSyncModels.Settings current = activity.settings();
        LinearLayout box = activity.settingsPanelBox();
        box.addView(activity.text(SettingsTextCopy.ladderThresholdsTitle(), 23, activity.INK, true));
        box.addView(activity.text(SettingsTextCopy.ladderThresholdsBody(), 15, activity.MUTED, false));

        EditText promotionDays = activity.thresholdInput(current.ladderPromotionIntervalDays);
        EditText failStreak = activity.thresholdInput(current.ladderDemotionFailStreak);
        box.addView(activity.text(SettingsTextCopy.fsrsDaysToGoUpLabel(), 15, activity.INK, true));
        box.addView(promotionDays, new LinearLayout.LayoutParams(-1, activity.dp(58)));
        box.addView(activity.text(SettingsTextCopy.failsToGoDownLabel(), 15, activity.INK, true));
        box.addView(failStreak, new LinearLayout.LayoutParams(-1, activity.dp(58)));

        Button defaults = activity.secondaryButton(SettingsTextCopy.useDefaultLadderThresholdsLabel());
        defaults.setOnClickListener(v -> {
            promotionDays.setText(String.format(Locale.ROOT, "%d", RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS));
            failStreak.setText(String.format(Locale.ROOT, "%d", RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK));
        });
        box.addView(defaults);

        Button save = activity.primaryButton(SettingsTextCopy.saveLadderThresholdsLabel(), activity.STUDY_PINK_DARK);
        save.setOnClickListener(v -> {
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
        });
        box.addView(save);
        return box;
    }
}
