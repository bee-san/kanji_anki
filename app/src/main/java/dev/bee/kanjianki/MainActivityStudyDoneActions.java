package dev.bee.kanjianki;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.StudyMoreNewCardsPolicy;

import java.util.List;

final class MainActivityStudyDoneActions {
    private final MainActivityStudy home;

    MainActivityStudyDoneActions(MainActivityStudy home) {
        this.home = home;
    }

    void addDoneStudyActions(LinearLayout card) {
        int available = availableStudyMoreNewCards();
        boolean canStudyMore = available > 0;
        if (canStudyMore) {
            Button studyMore = home.pinkPrimaryButton("Study more new cards");
            studyMore.setOnClickListener(new RunnableClickListener(() -> showStudyMoreNewCardsDialog(available)));
            card.addView(studyMore);
        }
        Button keepGoing = canStudyMore ? home.studySecondaryButton(MainActivityBase.LABEL_CONTINUE_ALL_KANJI) : home.pinkPrimaryButton(MainActivityBase.LABEL_CONTINUE_ALL_KANJI);
        keepGoing.setOnClickListener(new RunnableClickListener(() -> {
            home.studyMoreNewCardKanji.clear();
            home.continueAllKanjiSession = true;
            home.renderStudy();
        }));
        card.addView(keepGoing);
        Button back = home.studySecondaryButton(MainActivityBase.LABEL_BACK_HOME);
        back.setOnClickListener(new RunnableClickListener(() -> {
            home.clearStudyModeOverrides();
            home.renderHome();
        }));
        card.addView(back);
    }

    int availableStudyMoreNewCards() {
        List<RecordsImportModels.DashboardRow> rows = home.store.activeDashboardRows();
        if (rows.isEmpty()) {
            return 0;
        }
        long now = System.currentTimeMillis();
        BridgeScheduler.ExtraNewCardsResult result = new BridgeScheduler().seedExtraNewCards(
                rows,
                home.store.studyItems(),
                home.settings(),
                now,
                home.startOfDay(now),
                Integer.MAX_VALUE,
                home.studyLadderSettings()
        );
        return result.availableCount;
    }

    void showStudyMoreNewCardsDialog(int availableAtOpen) {
        int defaultCount = StudyMoreNewCardsPolicy.defaultRequestCount(availableAtOpen);
        EditText countInput = home.thresholdInput(defaultCount);
        countInput.setHint(MainActivityBase.LABEL_NEW_CARDS);
        countInput.setContentDescription(MainActivityBase.LABEL_NEW_CARDS);

        AlertDialog dialog = new AlertDialog.Builder(home)
                .setTitle("Study more new cards")
                .setMessage("How many extra new cards do you want to study now?")
                .setView(countInput)
                .setPositiveButton(MainActivityBase.LABEL_STUDY, null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(new StudyMoreNewCardsDialogShowListener(home, dialog, countInput));
        dialog.show();
        countInput.requestFocus();
    }

    boolean applyStudyMoreNewCardsRequest(EditText countInput) {
        int requested = home.requestedStudyMoreNewCards(countInput);
        return requested > 0 && home.startStudyMoreNewCards(requested);
    }

    private static final class StudyMoreNewCardsDialogShowListener implements DialogInterface.OnShowListener {
        private final MainActivityStudy activity;
        private final AlertDialog dialog;
        private final EditText countInput;

        StudyMoreNewCardsDialogShowListener(MainActivityStudy activity, AlertDialog dialog, EditText countInput) {
            this.activity = activity;
            this.dialog = dialog;
            this.countInput = countInput;
        }

        @Override
        public void onShow(DialogInterface opened) {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                    .setOnClickListener(new RunnableClickListener(() -> {
                        if (activity.applyStudyMoreNewCardsRequest(countInput)) {
                            dialog.dismiss();
                        }
                    }));
        }
    }
}
