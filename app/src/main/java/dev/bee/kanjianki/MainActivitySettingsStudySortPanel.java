package dev.bee.kanjianki;

import android.view.View;
import android.widget.LinearLayout;

import dev.bee.kanjianki.core.NewCardSortSettingsPolicy;
import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.core.SettingsTextCopy;

final class MainActivitySettingsStudySortPanel {
    private final MainActivitySettings activity;
    private final MainActivitySettingsStudySortActions actions;

    MainActivitySettingsStudySortPanel(MainActivitySettings activity) {
        this.activity = activity;
        this.actions = new MainActivitySettingsStudySortActions(activity);
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
        save.setOnClickListener(new RunnableClickListener(() -> actions.saveNewCardSort(selected[0])));
        box.addView(save);
        return box;
    }

    private void addSortModeButton(LinearLayout box, String label, String mode, String[] selected, android.widget.TextView status) {
        android.widget.Button button = activity.secondaryButton(label);
        button.setOnClickListener(new SortModeClickListener(mode, selected, status));
        box.addView(button);
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

    private static final class SortModeClickListener implements View.OnClickListener {
        private final String mode;
        private final String[] selected;
        private final android.widget.TextView status;

        SortModeClickListener(String mode, String[] selected, android.widget.TextView status) {
            this.mode = mode;
            this.selected = selected;
            this.status = status;
        }

        @Override
        public void onClick(View v) {
            selected[0] = mode;
            status.setText(SettingsTextCopy.newCardSortStatusText(mode));
        }
    }
}
