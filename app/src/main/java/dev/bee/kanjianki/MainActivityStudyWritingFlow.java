package dev.bee.kanjianki;

import android.view.View;

import dev.bee.kanjianki.core.study.HintState;
import dev.bee.kanjianki.core.study.StrokeDiagnosisFormatter;
import dev.bee.kanjianki.core.study.StrokeGuide;
import dev.bee.kanjianki.core.study.StrokeGuideGuard;
import dev.bee.kanjianki.core.study.WritingAnalysis;
import dev.bee.kanjianki.core.study.WritingAnalysisEngine;
import dev.bee.kanjianki.core.study.WritingFeedbackCopy;

final class MainActivityStudyWritingFlow {
    private final MainActivityStudy activity;

    MainActivityStudyWritingFlow(MainActivityStudy activity) {
        this.activity = activity;
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
        showAnalysis(activity.activeAnalysis);
        return true;
    }

    void showModelUnavailable(String message) {
        activity.activeAnalysis = WritingAnalysisEngine.modelUnavailable(
                message,
                activity.currentHintState.level(),
                activity.hintsUsed
        );
        activity.checkingWriting = false;
        showAnalysis(activity.activeAnalysis);
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

    void showAnalysis(WritingAnalysis analysis) {
        StrokeGuide guide = activity.activeSession == null ? null : activity.strokeGuide(activity.activeSession.item.kanji);
        if (WritingFeedbackCopy.shouldIncreaseSupportAfterAnalysis(analysis)) {
            activity.setHintState(activity.hintProgression.afterWriting(activity.currentHintState, analysis));
        }
        if (activity.drawingPad != null && activity.activeSession != null) {
            activity.drawingPad.setGuide(guide, activity.currentHintState, true);
            if (WritingFeedbackCopy.canReplayAnalysis(analysis, activity.drawingPad != null && activity.drawingPad.hasInk(), guide)) {
                activity.drawingPad.captureReplaySnapshot();
                activity.drawingPad.startReplay();
            } else {
                activity.drawingPad.clearReplaySnapshot();
            }
        }
        int color = analysis.writingPassed ? activity.TEAL : activity.CORAL;
        String targetKanji = activity.activeSession == null ? null : activity.activeSession.item.kanji;
        Integer activeWritingLevel = activity.activeSession == null ? null : activity.activeSession.item.writingLevel;
        String message = WritingFeedbackCopy.resultMessage(
                analysis,
                targetKanji,
                activeWritingLevel,
                WritingFeedbackCopy.shouldIncreaseSupportAfterAnalysis(analysis),
                StrokeDiagnosisFormatter.text(analysis)
        );
        activity.setStudyStatus(WritingFeedbackCopy.guideLabel(activity.currentHintState, guide), activity.MUTED);
        activity.setResultStatus(message, color);
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
        activity.activeAnalysis = null;
        if (activity.resultStatus != null) {
            activity.resultStatus.setVisibility(View.GONE);
        }
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
