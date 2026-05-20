package dev.bee.kanjianki;

import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import dev.bee.kanjianki.core.SettingsTextCopy;
import dev.bee.kanjianki.core.StudyAheadSettingsPolicy;

import java.util.Locale;

final class MainActivitySettingsStudyAheadPanel {
    private final MainActivitySettings activity;

    MainActivitySettingsStudyAheadPanel(MainActivitySettings activity) {
        this.activity = activity;
    }

    View studyAheadSettingsPanel() {
        int currentMinutes = activity.store.studyAheadMinutes();
        EditText minutesInput = minutesInput(currentMinutes);
        return MainActivitySettingsStudyAheadCompose.studyAheadSettingsPanelView(
                activity,
                new SettingsStudyAheadPanelModel(
                        SettingsTextCopy.studyAheadTitle(),
                        SettingsTextCopy.studyAheadBody(),
                        SettingsTextCopy.studyAheadMinutesLabel(),
                        minutesInput,
                        SettingsTextCopy.saveStudyAheadLabel(),
                        () -> saveStudyAhead(minutesInput)
                )
        );
    }

    private EditText minutesInput(int currentMinutes) {
        EditText input = new EditText(activity);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.format(Locale.ROOT, "%d", currentMinutes));
        input.setTextSize(20);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setContentDescription(SettingsTextCopy.studyAheadMinutesLabel());
        return input;
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
}
