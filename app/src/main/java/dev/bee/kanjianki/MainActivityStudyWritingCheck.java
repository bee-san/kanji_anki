package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.study.StrokeGuide;
import dev.bee.kanjianki.core.study.WritingAnalysisEngine;
import dev.bee.kanjianki.core.study.WritingSample;
import dev.bee.kanjianki.study.CapturedWriting;
import dev.bee.kanjianki.study.WritingRecognizer;

final class MainActivityStudyWritingCheck {
    private final MainActivityStudy activity;

    MainActivityStudyWritingCheck(MainActivityStudy activity) {
        this.activity = activity;
    }

    void checkWriting() {
        if (activity.activeSession == null) {
            return;
        }
        if (activity.showNoInkWhenNeeded()) {
            return;
        }
        if (activity.checkingWriting) {
            return;
        }
        RecordsSchedulerModels.StudySession session = activity.activeSession;
        String token = session.token;
        String target = session.item.kanji;
        MainActivityStudy.CapturedWritingAttempt attempt;
        try {
            attempt = capturedWritingAttempt();
        } catch (IllegalArgumentException error) {
            activity.activeAnalysis = WritingAnalysisEngine.noInk(activity.currentHintState.level(), activity.hintsUsed);
            activity.showAnalysis(activity.activeAnalysis);
            return;
        }
        StrokeGuide guide = activity.strokeGuide(target);
        activity.checkingWriting = true;
        activity.updateResultActions();
        activity.setStudyStatus("Checking handwriting...", activity.MUTED);
        WritingRecognizer recognizer = activity.currentWritingRecognizer();
        if (recognizer == null) {
            activity.showModelUnavailable("The handwriting checker is unavailable on this device.");
            return;
        }
        recognizer.modelStatus().whenComplete((status, statusError) -> {
            if (statusError != null || status == null || !status.downloaded) {
                activity.main.post(() -> {
                    if (!activity.isActiveToken(token)) {
                        return;
                    }
                    activity.writingModelDownloaded = false;
                    activity.writingModelStatusKnown = true;
                    activity.showModelUnavailable("Download the handwriting checker before automatic checks.");
                });
                return;
            }
            recognizeWriting(recognizer, attempt.captured, attempt.sample, guide, target, token);
        });
    }

    MainActivityStudy.CapturedWritingAttempt capturedWritingAttempt() {
        return new MainActivityStudy.CapturedWritingAttempt(activity.drawingPad.capturedWriting(), activity.drawingPad.writingSample());
    }

    void recognizeWriting(WritingRecognizer recognizer, CapturedWriting captured, WritingSample sample, StrokeGuide guide, String target, String token) {
        recognizer.recognize(captured).whenComplete((result, error) -> activity.main.post(() -> {
            if (!activity.isActiveToken(token)) {
                return;
            }
            activity.checkingWriting = false;
            if (error != null) {
                activity.activeAnalysis = WritingAnalysisEngine.recognitionError(activity.currentHintState.level(), activity.hintsUsed);
            } else {
                activity.activeAnalysis = WritingAnalysisEngine.analyze(target, sample, guide, activity.candidates(result), activity.currentHintState.level(), activity.hintsUsed);
            }
            activity.showAnalysis(activity.activeAnalysis);
        }));
    }
}
