package dev.bee.kanjianki;

import android.view.View;
import android.widget.Toast;

import dev.bee.kanjianki.core.LearningStepsSettingsPolicy;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.SettingsTextCopy;

final class MainActivitySettingsLearningPanel {
    private final MainActivitySettings activity;

    MainActivitySettingsLearningPanel(MainActivitySettings activity) {
        this.activity = activity;
    }

    View learningStepsSettingsPanel() {
        RecordsSchedulerModels.LearningStepSettings current = activity.store.learningStepSettings();
        RecordsSchedulerModels.LearningStepSettings defaults = RecordsSchedulerModels.LearningStepSettings.defaults();
        return MainActivitySettingsLearningCompose.learningStepsSettingsPanelView(
                activity,
                new SettingsLearningStepsPanelModel(
                        SettingsTextCopy.learningStepsTitle(),
                        SettingsTextCopy.learningStepsBody(),
                        activity.LABEL_NEW_CARDS,
                        current.newStepsText(),
                        SettingsTextCopy.reviewMissesLabel(),
                        current.reviewStepsText(),
                        defaults.newStepsText(),
                        defaults.reviewStepsText(),
                        SettingsTextCopy.ankiDefaultLabel(),
                        SettingsTextCopy.sameLearningStepsLabel(),
                        SettingsTextCopy.saveLearningStepsLabel(),
                        this::saveLearningSteps
                )
        );
    }

    private void saveLearningSteps(String newStepsText, String reviewStepsText) {
        LearningStepsSettingsPolicy.SaveResult request = LearningStepsSettingsPolicy.saveRequest(
                newStepsText,
                reviewStepsText
        );
        if (!request.valid) {
            Toast.makeText(activity, request.message, Toast.LENGTH_SHORT).show();
            return;
        }
        SettingsWriteActions.saveLearningSteps(request, activity.store::saveLearningStepSettings);
        Toast.makeText(activity, SettingsTextCopy.learningStepsSavedToast(), Toast.LENGTH_SHORT).show();
        activity.renderSettings();
    }
}
