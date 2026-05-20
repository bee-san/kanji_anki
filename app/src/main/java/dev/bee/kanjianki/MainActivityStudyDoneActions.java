package dev.bee.kanjianki;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.LinearLayout;
import android.widget.EditText;

import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.StudyMoreNewCardsPolicy;
import dev.bee.kanjianki.core.StudyTextCopy;

import java.util.List;

import static dev.bee.kanjianki.MainActivityStudyDoneActionsCompose.studyDoneActionsView;
import static dev.bee.kanjianki.MainActivityStudyDoneActionsCompose.studyDoneScreenView;

final class MainActivityStudyDoneActions {
    private final MainActivityStudy home;

    MainActivityStudyDoneActions(MainActivityStudy home) {
        this.home = home;
    }

    void addDoneStudyActions(LinearLayout card) {
        int available = availableStudyMoreNewCards();
        card.addView(studyDoneActionsView(
                home,
                available,
                () -> showStudyMoreNewCardsDialog(available),
                this::continueAllKanji,
                this::backHome
        ));
    }

    void renderNoStudySession(RecordsSchedulerModels.AdaptiveLoadPlan seededPlan) {
        if (!home.continueAllKanjiSession && seededPlan.focusComplete()) {
            renderFocusDone(seededPlan);
            return;
        }
        home.prepareStudyContent(seededPlan, false);
        home.content.addView(studyDoneScreenView(home, studyDoneScreenModel(
                "Nothing due now",
                null,
                "Your active kanji are resting. Sync again if Anki has created new problem candidates, or come back when the next review is due.",
                List.of(),
                false,
                true,
                true
        )));
    }

    void renderFocusDone(RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        home.prepareStudyContent(plan, false);
        List<String> summaryLines = new java.util.ArrayList<>();
        summaryLines.add(StudyTextCopy.adaptiveFocusDoneSummary(plan.target));
        if (!plan.status.isEmpty()) {
            summaryLines.add(plan.status);
        }
        home.content.addView(studyDoneScreenView(home, studyDoneScreenModel(
                StudyTextCopy.studyDoneTitle(),
                null,
                StudyTextCopy.adaptiveFocusDoneBody(),
                summaryLines,
                true,
                false,
                false
        )));
    }

    void renderStudyRunDone(RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        home.prepareStudyContent(plan, false);
        List<String> summaryLines = new java.util.ArrayList<>();
        summaryLines.add(StudyTextCopy.movedForwardSummary(home.studySessionTracker.movedForwardCount()));
        summaryLines.add(StudyTextCopy.missedSummary(home.studySessionTracker.missedCount()));
        summaryLines.add(StudyTextCopy.completedTaskSummary(home.studySessionTracker.completedCount()));
        if (plan != null && !plan.status.isEmpty()) {
            summaryLines.add(plan.status);
        }
        home.content.addView(studyDoneScreenView(home, studyDoneScreenModel(
                StudyTextCopy.studyDoneTitle(),
                null,
                StudyTextCopy.studyRunDoneBody(),
                summaryLines,
                true,
                false,
                false
        )));
    }

    void renderEmptyStudyQueue() {
        home.prepareStudyContent(home.activeStudyPlan, false);
        home.content.addView(studyDoneScreenView(home, studyDoneScreenModel(
                "Study practice",
                "Nothing to study yet",
                "Sync from AnkiDroid first. Study opens once the app finds problem kanji to repair.",
                List.of(),
                false,
                false,
                false
        )));
    }

    void renderStudyForKanjiNotAvailable() {
        home.prepareStudyContent(home.activeStudyPlan, false);
        home.content.addView(studyDoneScreenView(home, studyDoneScreenModel(
                "Study practice",
                "Kanji not available",
                "This row may have changed after sync.",
                List.of(),
                false,
                false,
                false
        )));
    }

    private StudyDoneScreenModel studyDoneScreenModel(
            String title,
            String headline,
            String body,
            List<String> summaryLines,
            boolean showDoneActions,
            boolean showBackHome,
            boolean backHomePrimary
    ) {
        int available = showDoneActions ? availableStudyMoreNewCards() : 0;
        return new StudyDoneScreenModel(
                MainActivityBase.LABEL_PRACTICE,
                title,
                headline,
                body,
                summaryLines,
                showDoneActions,
                available,
                showBackHome,
                backHomePrimary,
                () -> showStudyMoreNewCardsDialog(available),
                this::continueAllKanji,
                this::backHome
        );
    }

    private void continueAllKanji() {
        home.studyMoreNewCardKanji.clear();
        home.continueAllKanjiSession = true;
        home.renderStudy();
    }

    private void backHome() {
        home.clearStudyModeOverrides();
        home.renderHome();
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
