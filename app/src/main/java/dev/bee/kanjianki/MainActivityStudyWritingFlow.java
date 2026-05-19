package dev.bee.kanjianki;

import dev.bee.kanjianki.core.study.HintState;
import dev.bee.kanjianki.core.study.StrokeGuide;
import dev.bee.kanjianki.core.study.StrokeGuideGuard;
import dev.bee.kanjianki.core.study.WritingAnalysis;
import dev.bee.kanjianki.core.study.WritingAnalysisEngine;
import dev.bee.kanjianki.core.study.WritingFeedbackCopy;

final class MainActivityStudyWritingFlow {
    private final MainActivityStudy activity;
    private final MainActivityStudyWritingResult writingResult;

    MainActivityStudyWritingFlow(MainActivityStudy activity) {
        this.activity = activity;
        this.writingResult = new MainActivityStudyWritingResult(activity);
    }

    void eraseWritingPad() {
        activity.drawingPad.clear();
        activity.activeAnalysis = null;
        activity.setStudyStatus(
                WritingFeedbackCopy.guideLabel(activity.currentHintState, activity.strokeGuide(activity.activeSession.item.kanji)),
                activity.MUTED
        );
        activity.updateResultActions();
    }

    void startGuidedWritingRetry() {
        activity.setHintState(HintState.initial());
        activity.hintsUsed++;
        activity.activeAnalysis = null;
        activity.drawingPad.clear();
        StrokeGuide guide = activity.strokeGuide(activity.activeSession.item.kanji);
        activity.drawingPad.setGuide(guide, activity.currentHintState, false);
        activity.setStudyStatus(
                WritingFeedbackCopy.freshGuidedTryStatus(WritingFeedbackCopy.guideLabel(activity.currentHintState, guide)),
                activity.MUTED
        );
        activity.updateResultActions();
    }

    boolean showNoInkWhenNeeded() {
        if (activity.drawingPad != null && activity.drawingPad.hasInk()) {
            return false;
        }
        activity.activeAnalysis = WritingAnalysisEngine.noInk(activity.currentHintState.level(), activity.hintsUsed);
        writingResult.showAnalysis(activity.activeAnalysis);
        return true;
    }

    void showModelUnavailable(String message) {
        writingResult.showModelUnavailable(message);
    }

    void showAnalysis(WritingAnalysis analysis) {
        writingResult.showAnalysis(analysis);
    }

    void showWritingHint() {
        if (activity.drawingPad == null || activity.activeSession == null) {
            return;
        }
        StrokeGuide guide = activity.strokeGuide(activity.activeSession.item.kanji);
        activity.setHintState(activity.hintProgression.revealNext(activity.currentHintState, guide));
        activity.hintsUsed++;
        activity.activeAnalysis = null;
        activity.drawingPad.setGuide(guide, activity.currentHintState, false);
        activity.setStudyStatus(
                WritingFeedbackCopy.hintUsedStatus(WritingFeedbackCopy.guideLabel(activity.currentHintState, guide)),
                activity.MUTED
        );
        activity.updateResultActions();
    }

    void startCleanerRetry() {
        if (activity.drawingPad == null || activity.activeSession == null) {
            return;
        }
        clearWritingResult();
        StrokeGuide guide = activity.strokeGuide(activity.activeSession.item.kanji);
        activity.drawingPad.clear();
        activity.drawingPad.setGuide(guide, activity.currentHintState, false);
        activity.setStudyStatus(
                WritingFeedbackCopy.cleanerRetryStatus(WritingFeedbackCopy.guideLabel(activity.currentHintState, guide)),
                activity.MUTED
        );
        activity.updateResultActions();
    }

    void undoWritingStroke() {
        if (activity.drawingPad == null || activity.activeSession == null || !activity.drawingPad.undoLastStroke()) {
            activity.updateUndoStrokeButton();
            return;
        }
        clearWritingResult();
        StrokeGuide guide = activity.strokeGuide(activity.activeSession.item.kanji);
        activity.setStudyStatus(
                WritingFeedbackCopy.undoStrokeStatus(WritingFeedbackCopy.guideLabel(activity.currentHintState, guide)),
                activity.MUTED
        );
        activity.updateResultActions();
    }

    void replayWritingAnalysis() {
        if (activity.drawingPad == null || activity.activeSession == null) {
            return;
        }
        StrokeGuide guide = activity.strokeGuide(activity.activeSession.item.kanji);
        if (WritingFeedbackCopy.canReplayAnalysis(activity.activeAnalysis, activity.drawingPad != null && activity.drawingPad.hasInk(), guide)) {
            activity.drawingPad.setGuide(guide, activity.currentHintState, true);
            activity.drawingPad.startReplay();
        }
    }

    void handleDrawingEdited() {
        activity.updateUndoStrokeButton();
        if (activity.checkingWriting || activity.activeAnalysis == null || activity.activeSession == null || activity.drawingPad == null) {
            return;
        }
        clearWritingResult();
        activity.drawingPad.clearReplaySnapshot();
        StrokeGuide guide = activity.strokeGuide(activity.activeSession.item.kanji);
        activity.setStudyStatus(
                WritingFeedbackCopy.updatedInkStatus(WritingFeedbackCopy.guideLabel(activity.currentHintState, guide)),
                activity.MUTED
        );
        activity.updateResultActions();
    }

    void clearWritingResult() {
        writingResult.clearWritingResult();
    }

    void handleDrawingBlocked(StrokeGuideGuard.Decision decision) {
        if (activity.activeSession == null || activity.drawingPad == null) {
            return;
        }
        StrokeGuide guide = activity.strokeGuide(activity.activeSession.item.kanji);
        activity.setStudyStatus(
                WritingFeedbackCopy.blockedStrokeStatus(WritingFeedbackCopy.guideLabel(activity.currentHintState, guide), decision),
                activity.MUTED
        );
        activity.updateUndoStrokeButton();
    }

}
