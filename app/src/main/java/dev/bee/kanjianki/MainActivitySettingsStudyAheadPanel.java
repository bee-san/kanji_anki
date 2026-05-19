package dev.bee.kanjianki;

import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import dev.bee.kanjianki.core.SettingsTextCopy;
import dev.bee.kanjianki.core.StudyAheadSettingsPolicy;

import java.util.Locale;

final class MainActivitySettingsStudyAheadPanel {
    private final MainActivitySettings activity;

    MainActivitySettingsStudyAheadPanel(MainActivitySettings activity) {
        this.activity = activity;
    }

    LinearLayout studyAheadSettingsPanel() {
        int currentMinutes = activity.store.studyAheadMinutes();
        LinearLayout box = activity.settingsPanelBox();
        box.addView(activity.text(SettingsTextCopy.studyAheadTitle(), 23, activity.INK, true));
        box.addView(activity.text(SettingsTextCopy.studyAheadBody(), 15, activity.MUTED, false));

        EditText minutesInput = new EditText(activity);
        minutesInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        minutesInput.setText(String.format(Locale.ROOT, "%d", currentMinutes));
        minutesInput.setTextSize(20);
        minutesInput.setSingleLine(true);
        minutesInput.setSelectAllOnFocus(true);
        box.addView(activity.text(SettingsTextCopy.studyAheadMinutesLabel(), 15, activity.INK, true));
        box.addView(minutesInput, new LinearLayout.LayoutParams(-1, activity.dp(58)));

        Button save = activity.primaryButton(SettingsTextCopy.saveStudyAheadLabel(), activity.STUDY_PINK_DARK);
        save.setOnClickListener(new RunnableClickListener(() -> saveStudyAhead(minutesInput)));
        box.addView(save);
        return box;
    }

    private void saveStudyAhead(EditText minutesInput) {
        StudyAheadSettingsPolicy.SaveResult request = StudyAheadSettingsPolicy.saveRequest(minutesInput.getText().toString());
        if (!request.valid) {
            Toast.makeText(activity, request.message, Toast.LENGTH_SHORT).show();
            return;
        }
        activity.store.saveStudyAheadMinutes(request.minutes);
        Toast.makeText(activity, SettingsTextCopy.studyAheadSavedToast(), Toast.LENGTH_SHORT).show();
        activity.renderSettings();
    }

    private static final class RunnableClickListener implements View.OnClickListener {
        private final Runnable action;

        RunnableClickListener(Runnable action) {
            this.action = action;
        }

        @Override
        public void onClick(View v) {
            action.run();
        }
    }
}
