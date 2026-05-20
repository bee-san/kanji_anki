package dev.bee.kanjianki;

import android.view.View;

import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.StudyTaskCopy;
import dev.bee.kanjianki.core.StudyTextCopy;
import dev.bee.kanjianki.core.study.WritingFeedbackCopy;
import dev.bee.kanjianki.core.study.StrokeGuide;

import java.util.ArrayList;
import java.util.List;

final class MainActivityStudyWritingSession {
    private final MainActivityStudy home;

    MainActivityStudyWritingSession(MainActivityStudy home) {
        this.home = home;
    }

    void renderWritingSession(RecordsSchedulerModels.StudySession session) {
        resetWritingSession(session);

        home.writingAnswerPanelState = new WritingAnswerPanelState(false);
        home.studyAnswerPanel = null;
        StudyAnswerPanelModel answerPanel = home.learningPanelModel(session);

        StrokeGuide guide = home.strokeGuide(session.item.kanji);
        home.studyStatus = new WritingStatusState();
        home.studyStatus.setStatus(WritingFeedbackCopy.guideLabel(home.currentHintState, guide), home.STUDY_MUTED);
        home.drawingPad = new DrawingPadView(home);
        home.drawingPad.setTarget(session.item.kanji);
        home.drawingPad.setInkEditListener(home::handleDrawingEdited);
        home.drawingPad.setStrokeBlockedListener(home::handleDrawingBlocked);
        home.drawingPad.setGuide(guide, home.currentHintState, false);
        home.writingResultStatus = new WritingResultStatusHandle();
        home.writingResultStatus.hide();
        home.content.addView(MainActivityStudyWritingSessionCompose.writingSessionCardView(
                home,
                new WritingSessionCardModel(
                        writingPromptHeaderModel(session),
                        answerPanel,
                        home.writingAnswerPanelState,
                        "Writing",
                        home.STUDY_PLUM,
                        home.studyStatus,
                        MainActivityStudyWritingPadCompose.writingPadPanelView(home, home.drawingPad, home.studyPadHeight()),
                        home.writingResultStatus
                )
        ));

        home.buildStudyActionBar();
        home.updateResultActions();
        home.refreshWritingModelStatus();
    }

    private WritingPromptHeaderModel writingPromptHeaderModel(RecordsSchedulerModels.StudySession session) {
        return new WritingPromptHeaderModel(
                MainActivityBase.LABEL_PRACTICE,
                "Draw this kanji",
                StudyTaskCopy.labelForTask(session.taskType),
                home.studyReasonLine(session),
                writingPromptLines(session)
        );
    }

    private List<WritingPromptLineModel> writingPromptLines(RecordsSchedulerModels.StudySession session) {
        List<WritingPromptLineModel> lines = new ArrayList<>();
        if (session.row == null) {
            lines.add(new WritingPromptLineModel(safeText(session.prompt), 17, home.STUDY_MUTED, false));
            return lines;
        }
        if (!StudyTaskCopy.isRecallTask(session)) {
            lines.add(new WritingPromptLineModel("Learn it from the reference, trace it, then check.", 15, home.STUDY_MUTED, false));
            return lines;
        }
        lines.add(new WritingPromptLineModel(
                "Prompt: " + StudyTextCopy.sessionClue(home.currentDictionaryLookup(), session),
                17,
                home.STUDY_PLUM,
                true
        ));
        if (!session.row.reading.isEmpty()) {
            lines.add(new WritingPromptLineModel("Reading: " + session.row.reading, 15, home.STUDY_MUTED, false));
        }
        lines.add(new WritingPromptLineModel(
                "Write the kanji from this prompt. The answer stays hidden until you check.",
                15,
                home.STUDY_MUTED,
                false
        ));
        return lines;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
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
