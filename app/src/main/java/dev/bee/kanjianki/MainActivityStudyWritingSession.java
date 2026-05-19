package dev.bee.kanjianki;

import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.StudyTaskCopy;
import dev.bee.kanjianki.core.StudyTextCopy;
import dev.bee.kanjianki.core.study.WritingFeedbackCopy;
import dev.bee.kanjianki.core.study.StrokeGuide;

final class MainActivityStudyWritingSession {
    private final MainActivityStudy home;

    MainActivityStudyWritingSession(MainActivityStudy home) {
        this.home = home;
    }

    void renderWritingSession(RecordsSchedulerModels.StudySession session) {
        resetWritingSession(session);

        LinearLayout card = home.softStudyCard();
        card.addView(home.modePill(MainActivityBase.LABEL_PRACTICE));
        card.addView(home.text("Draw this kanji", 30, home.STUDY_PLUM, true));
        card.addView(home.text(StudyTaskCopy.labelForTask(session.taskType), 16, home.STUDY_PINK_DARK, true));
        home.addStudyReasonLine(card, session);
        if (session.row != null) {
            if (StudyTaskCopy.isRecallTask(session)) {
                card.addView(home.text("Prompt: " + StudyTextCopy.sessionClue(home.currentDictionaryLookup(), session), 17, home.STUDY_PLUM, true));
                if (!session.row.reading.isEmpty()) {
                    card.addView(home.text("Reading: " + session.row.reading, 15, home.STUDY_MUTED, false));
                }
                card.addView(home.text("Write the kanji from this prompt. The answer stays hidden until you check.", 15, home.STUDY_MUTED, false));
            } else {
                card.addView(home.text("Learn it from the reference, trace it, then check.", 15, home.STUDY_MUTED, false));
            }
        } else {
            card.addView(home.text(session.prompt, 17, home.STUDY_MUTED, false));
        }
        home.studyAnswerPanel = home.learningPanel(session);
        card.addView(home.studyAnswerPanel);

        TextView writingTitle = home.sectionTitle("Writing");
        writingTitle.setTextColor(home.STUDY_PLUM);
        card.addView(writingTitle);
        StrokeGuide guide = home.strokeGuide(session.item.kanji);
        home.studyStatus = home.text(WritingFeedbackCopy.guideLabel(home.currentHintState, guide), 16, home.STUDY_MUTED, false);
        card.addView(home.studyStatus);
        home.drawingPad = new DrawingPadView(home);
        home.drawingPad.setTarget(session.item.kanji);
        home.drawingPad.setInkEditListener(home::handleDrawingEdited);
        home.drawingPad.setStrokeBlockedListener(home::handleDrawingBlocked);
        home.drawingPad.setGuide(guide, home.currentHintState, false);
        LinearLayout padShell = home.softInsetPanel();
        padShell.setPadding(home.dp(8), home.dp(8), home.dp(8), home.dp(8));
        MainActivityBase.SquarePadFrame squarePad = new MainActivityBase.SquarePadFrame(home, home.studyPadHeight());
        squarePad.addView(home.drawingPad);
        padShell.addView(squarePad, new LinearLayout.LayoutParams(-1, -2));
        card.addView(padShell);
        home.resultStatus = home.text("", 16, home.STUDY_MUTED, false);
        home.resultStatus.setVisibility(View.GONE);
        card.addView(home.resultStatus);
        home.content.addView(card);

        home.buildStudyActionBar();
        home.updateResultActions();
        home.refreshWritingModelStatus();
    }

    void resetWritingSession(RecordsSchedulerModels.StudySession session) {
        home.prepareStudyContent(home.activeStudyPlan, false);
        home.activeAnalysis = null;
        home.checkingWriting = false;
        home.flashcardGestureArea = null;
        home.flashcardAnswerRevealed = false;
        home.flashcardTouchTracking = false;
        home.typingAnswerInput = null;
        home.hintsUsed = 0;
        home.setHintState(home.initialHintState(session));
    }

    void hideStudyActionBar() {
        if (home.studyActionBar != null) {
            home.studyActionBar.removeAllViews();
            home.studyActionBar.setVisibility(View.GONE);
        }
    }

    void renderSimilarWritingRepair(RecordsImportModels.SimilarKanjiWritingRepair repair, RecordsSchedulerModels.AdaptiveLoadPlan plan, long now) {
        StudyRepairActions.ActiveRepair active = StudyRepairActions.activateSimilarWritingRepair(repair, now, home.store::saveSimilarWritingRepair);
        RecordsImportModels.SimilarKanjiWritingRepair activeRepair = active.repair();
        home.activeSimilarWritingRepair = activeRepair;
        RecordsSchedulerModels.StudyItem item = new BridgeScheduler().newTargetedStudyItem(activeRepair.repairKanji, now, home.studyLadderSettings());
        home.activeSession = new RecordsSchedulerModels.StudySession(
                item.withToken(active.token()),
                null,
                active.token(),
                MainActivityBase.TASK_REPAIR_WRITING,
                true,
                StudyTextCopy.similarRepairPrompt(activeRepair)
        );
        home.activeStudyPlan = plan;
        home.registerStudyTaskShown(active.progressKey());
        home.startActiveStudyTask(active.studyTaskKey(), activeRepair.repairKanji, MainActivityBase.TASK_REPAIR_WRITING, now);
        renderWritingSession(home.activeSession);
    }
}
