package dev.bee.kanjianki;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.StudyMoreNewCardsPolicy;
import dev.bee.kanjianki.core.StudyTextCopy;

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

    void renderNoStudySession(RecordsSchedulerModels.AdaptiveLoadPlan seededPlan) {
        if (!home.continueAllKanjiSession && seededPlan.focusComplete()) {
            renderFocusDone(seededPlan);
            return;
        }
        home.prepareStudyContent(seededPlan, false);
        LinearLayout card = home.softStudyCard();
        card.addView(home.modePill(MainActivityBase.LABEL_PRACTICE));
        card.addView(home.text("Nothing due now", 32, home.STUDY_PLUM, true));
        card.addView(home.text("Your active kanji are resting. Sync again if Anki has created new problem candidates, or come back when the next review is due.", 17, home.STUDY_MUTED, false));
        Button back = home.pinkPrimaryButton(MainActivityBase.LABEL_BACK_HOME);
        back.setOnClickListener(new RunnableClickListener(() -> {
            home.clearStudyModeOverrides();
            home.renderHome();
        }));
        card.addView(back);
        home.content.addView(card);
    }

    void renderFocusDone(RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        home.prepareStudyContent(plan, false);
        LinearLayout card = home.softStudyCard();
        card.addView(home.modePill(MainActivityBase.LABEL_PRACTICE));
        card.addView(home.text(StudyTextCopy.studyDoneTitle(), 32, home.STUDY_PLUM, true));
        card.addView(home.text(StudyTextCopy.adaptiveFocusDoneBody(), 17, home.STUDY_MUTED, false));
        LinearLayout summary = home.softInsetPanel();
        summary.addView(home.text(StudyTextCopy.adaptiveFocusDoneSummary(plan.target), 20, home.STUDY_PLUM, true));
        summary.addView(home.text(plan.status, 15, home.STUDY_MUTED, false));
        card.addView(summary);
        addDoneStudyActions(card);
        home.content.addView(card);
    }

    void renderStudyRunDone(RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        home.prepareStudyContent(plan, false);
        LinearLayout card = home.softStudyCard();
        card.addView(home.modePill(MainActivityBase.LABEL_PRACTICE));
        card.addView(home.text(StudyTextCopy.studyDoneTitle(), 32, home.STUDY_PLUM, true));
        card.addView(home.text(StudyTextCopy.studyRunDoneBody(), 17, home.STUDY_MUTED, false));
        LinearLayout summary = home.softInsetPanel();
        summary.addView(home.text(StudyTextCopy.movedForwardSummary(home.studySessionTracker.movedForwardCount()), 20, home.STUDY_PLUM, true));
        summary.addView(home.text(StudyTextCopy.missedSummary(home.studySessionTracker.missedCount()), 16, home.STUDY_MUTED, false));
        summary.addView(home.text(StudyTextCopy.completedTaskSummary(home.studySessionTracker.completedCount()), 16, home.STUDY_MUTED, false));
        if (plan != null && !plan.status.isEmpty()) {
            summary.addView(home.text(plan.status, 15, home.STUDY_MUTED, false));
        }
        card.addView(summary);
        addDoneStudyActions(card);
        home.content.addView(card);
    }

    void renderEmptyStudyQueue() {
        home.prepareStudyContent(home.activeStudyPlan, false);
        LinearLayout card = home.softStudyCard();
        card.addView(home.modePill(MainActivityBase.LABEL_PRACTICE));
        card.addView(home.text("Study practice", 32, home.STUDY_PLUM, true));
        card.addView(home.text("Nothing to study yet", 22, home.STUDY_PLUM, true));
        card.addView(home.text("Sync from AnkiDroid first. Study opens once the app finds problem kanji to repair.", 16, home.STUDY_MUTED, false));
        home.content.addView(card);
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
