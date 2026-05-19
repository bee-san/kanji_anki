package dev.bee.kanjianki;

import android.widget.LinearLayout;
import android.widget.Toast;

import dev.bee.kanjianki.core.NewCardSortSettingsPolicy;
import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.core.SettingsTextCopy;
import dev.bee.kanjianki.sync.SyncSettings;

final class MainActivitySettingsStudySortPanel {
    private final MainActivitySettings activity;

    MainActivitySettingsStudySortPanel(MainActivitySettings activity) {
        this.activity = activity;
    }

    LinearLayout newCardSortSettingsPanel(RecordsSyncModels.Settings current) {
        final String[] selected = new String[]{current.newCardSortMode};
        LinearLayout box = activity.settingsPanelBox();
        box.addView(activity.text(SettingsTextCopy.newCardSortTitle(), 23, activity.INK, true));
        android.widget.TextView status = activity.text(SettingsTextCopy.newCardSortStatusText(selected[0]), 17, activity.TEAL, true);
        box.addView(status);
        box.addView(activity.text(SettingsTextCopy.newCardSortBody(), 15, activity.MUTED, false));

        addSortModeButton(box, SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_FREQUENCY), RecordsBase.NEW_CARD_SORT_FREQUENCY, selected, status);
        addSortModeButton(box, SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY), RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY, selected, status);
        addSortModeButton(box, SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK), RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK, selected, status);
        addSortModeButton(box, SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS), RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS, selected, status);

        android.widget.Button save = activity.primaryButton(SettingsTextCopy.saveNewCardSortLabel(), activity.STUDY_PINK_DARK);
        save.setOnClickListener(v -> {
            NewCardSortSettingsPolicy.SaveRequest request = NewCardSortSettingsPolicy.saveRequest(selected[0]);
            activity.store.putStringSetting(SyncSettings.NEW_CARD_SORT_MODE_SETTING_KEY, request.mode);
            Toast.makeText(activity, request.message, Toast.LENGTH_SHORT).show();
            activity.renderSettings();
        });
        box.addView(save);
        return box;
    }

    private void addSortModeButton(LinearLayout box, String label, String mode, String[] selected, android.widget.TextView status) {
        android.widget.Button button = activity.secondaryButton(label);
        button.setOnClickListener(v -> {
            selected[0] = mode;
            status.setText(SettingsTextCopy.newCardSortStatusText(mode));
        });
        box.addView(button);
    }
}
