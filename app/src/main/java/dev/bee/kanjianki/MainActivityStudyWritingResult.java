package dev.bee.kanjianki;

import android.view.View;

import dev.bee.kanjianki.core.study.StrokeDiagnosisFormatter;
import dev.bee.kanjianki.core.study.StrokeGuide;
import dev.bee.kanjianki.core.study.WritingAnalysis;
import dev.bee.kanjianki.core.study.WritingAnalysisEngine;
import dev.bee.kanjianki.core.study.WritingFeedbackCopy;

final class MainActivityStudyWritingResult {
    private final MainActivityStudy activity;

    MainActivityStudyWritingResult(MainActivityStudy activity) {
        this.activity = activity;
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

    void clearWritingResult() {
        activity.activeAnalysis = null;
        if (activity.resultStatus != null) {
            activity.resultStatus.setVisibility(View.GONE);
        }
    }
}
