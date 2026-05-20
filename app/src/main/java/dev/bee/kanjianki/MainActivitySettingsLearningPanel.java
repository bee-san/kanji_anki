package dev.bee.kanjianki;

import android.view.View;
import android.widget.EditText;
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
        EditText newSteps = stepInput(current.newStepsText());
        EditText reviewSteps = stepInput(current.reviewStepsText());
        newSteps.setContentDescription(activity.LABEL_NEW_CARDS);
        reviewSteps.setContentDescription(SettingsTextCopy.reviewMissesLabel());
        return MainActivitySettingsLearningCompose.learningStepsSettingsPanelView(
                activity,
                new SettingsLearningStepsPanelModel(
                        SettingsTextCopy.learningStepsTitle(),
                        SettingsTextCopy.learningStepsBody(),
                        activity.LABEL_NEW_CARDS,
                        newSteps,
                        SettingsTextCopy.reviewMissesLabel(),
                        reviewSteps,
                        SettingsTextCopy.ankiDefaultLabel(),
                        SettingsTextCopy.sameLearningStepsLabel(),
                        SettingsTextCopy.saveLearningStepsLabel(),
                        () -> applyLearningStepDefaults(newSteps, reviewSteps, false),
                        () -> applyLearningStepDefaults(newSteps, reviewSteps, true),
                        () -> saveLearningSteps(newSteps, reviewSteps)
                )
        );
    }

    EditText stepInput(String value) {
        EditText input = new EditText(activity);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setText(value);
        input.setTextSize(20);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        return input;
    }

    private void applyLearningStepDefaults(EditText newSteps, EditText reviewSteps, boolean useSameSteps) {
        RecordsSchedulerModels.LearningStepSettings defaults = RecordsSchedulerModels.LearningStepSettings.defaults();
        newSteps.setText(defaults.newStepsText());
        reviewSteps.setText(useSameSteps ? defaults.newStepsText() : defaults.reviewStepsText());
    }

    private void saveLearningSteps(EditText newSteps, EditText reviewSteps) {
        LearningStepsSettingsPolicy.SaveResult request = LearningStepsSettingsPolicy.saveRequest(
                newSteps.getText().toString(),
                reviewSteps.getText().toString()
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
