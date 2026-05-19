package dev.bee.kanjianki;

import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import dev.bee.kanjianki.core.LearningStepsSettingsPolicy;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.SettingsTextCopy;

final class MainActivitySettingsLearningPanel {
    private final MainActivitySettings activity;

    MainActivitySettingsLearningPanel(MainActivitySettings activity) {
        this.activity = activity;
    }

    LinearLayout learningStepsSettingsPanel() {
        RecordsSchedulerModels.LearningStepSettings current = activity.store.learningStepSettings();
        LinearLayout box = activity.settingsPanelBox();
        box.addView(activity.text(SettingsTextCopy.learningStepsTitle(), 23, activity.INK, true));
        box.addView(activity.text(SettingsTextCopy.learningStepsBody(), 15, activity.MUTED, false));

        EditText newSteps = stepInput(current.newStepsText());
        EditText reviewSteps = stepInput(current.reviewStepsText());
        box.addView(activity.text(activity.LABEL_NEW_CARDS, 15, activity.INK, true));
        box.addView(newSteps, new LinearLayout.LayoutParams(-1, activity.dp(58)));
        box.addView(activity.text(SettingsTextCopy.reviewMissesLabel(), 15, activity.INK, true));
        box.addView(reviewSteps, new LinearLayout.LayoutParams(-1, activity.dp(58)));

        LinearLayout presets = new LinearLayout(activity);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        Button ankiDefault = activity.secondaryButton(SettingsTextCopy.ankiDefaultLabel());
        ankiDefault.setOnClickListener(new RunnableClickListener(() -> applyLearningStepDefaults(newSteps, reviewSteps, false)));
        presets.addView(ankiDefault, new LinearLayout.LayoutParams(0, activity.dp(54), 1));
        Button sameSteps = activity.secondaryButton(SettingsTextCopy.sameLearningStepsLabel());
        sameSteps.setOnClickListener(new RunnableClickListener(() -> applyLearningStepDefaults(newSteps, reviewSteps, true)));
        presets.addView(sameSteps, new LinearLayout.LayoutParams(0, activity.dp(54), 1));
        box.addView(presets);

        Button save = activity.primaryButton(SettingsTextCopy.saveLearningStepsLabel(), activity.STUDY_PINK_DARK);
        save.setOnClickListener(new RunnableClickListener(() -> saveLearningSteps(newSteps, reviewSteps)));
        box.addView(save);
        return box;
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

    private static final class RunnableClickListener implements android.view.View.OnClickListener {
        private final Runnable action;

        RunnableClickListener(Runnable action) {
            this.action = action;
        }

        @Override
        public void onClick(android.view.View v) {
            action.run();
        }
    }
}
