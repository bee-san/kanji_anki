package dev.bee.kanjianki;

import dev.bee.kanjianki.core.study.WritingFeedbackCopy;
import dev.bee.kanjianki.study.WritingRecognizer;

final class MainActivityStudyWritingStatus {
    private final MainActivityStudy activity;

    MainActivityStudyWritingStatus(MainActivityStudy activity) {
        this.activity = activity;
    }

    void refreshWritingModelStatus() {
        activity.writingModelStatusKnown = false;
        activity.writingModelDownloaded = false;
        activity.updateResultActions();
        String token = activity.activeSession == null ? null : activity.activeSession.token;
        WritingRecognizer recognizer = activity.currentWritingRecognizer();
        if (recognizer == null) {
            updateWritingModelAvailability(false);
            activity.setStudyStatus(
                    WritingFeedbackCopy.unavailableModelStatusMessage(
                            WritingFeedbackCopy.guideLabel(activity.currentHintState, activity.strokeGuide(activity.activeSession.item.kanji))
                    ),
                    activity.CORAL
            );
            activity.updateResultActions();
            return;
        }
        recognizer.modelStatus().whenComplete((status, error) -> activity.main.post(() -> {
            if (token == null || !activity.isActiveToken(token)) {
                return;
            }
            updateWritingModelAvailability(error == null && status != null && status.downloaded);
            activity.updateResultActions();
            if (activity.activeAnalysis != null || activity.checkingWriting) {
                return;
            }
            setWritingModelStatusMessage(status, error);
        }));
    }

    void setWritingModelStatusMessage(WritingRecognizer.ModelStatus status, Throwable error) {
        String prefix = WritingFeedbackCopy.guideLabel(activity.currentHintState, activity.strokeGuide(activity.activeSession.item.kanji));
        if (error != null || status == null) {
            activity.setStudyStatus(WritingFeedbackCopy.modelStatusMessage(prefix, status != null, false, error != null), activity.CORAL);
            return;
        }
        if (!status.downloaded) {
            activity.setStudyStatus(WritingFeedbackCopy.modelStatusMessage(prefix, true, false, false), activity.CORAL);
            return;
        }
        activity.setStudyStatus(WritingFeedbackCopy.modelStatusMessage(prefix, true, true, false), activity.MUTED);
    }

    void downloadWritingModel() {
        String token = activity.activeSession == null ? null : activity.activeSession.token;
        WritingRecognizer recognizer = activity.currentWritingRecognizer();
        if (recognizer == null) {
            activity.setStudyStatus("The handwriting checker is unavailable on this device.", activity.CORAL);
            return;
        }
        activity.setStudyStatus("Downloading handwriting checker...", activity.MUTED);
        recognizer.downloadModel().whenComplete((status, error) -> activity.main.post(() -> {
            if (token != null && !activity.isActiveToken(token)) {
                return;
            }
            if (error != null) {
                updateWritingModelAvailability(false);
                activity.setStudyStatus("Handwriting checker download failed: " + error.getMessage(), activity.CORAL);
            } else {
                updateWritingModelAvailability(true);
                activity.setStudyStatus("Handwriting checker ready.", activity.TEAL);
            }
            activity.updateResultActions();
        }));
    }

    void updateWritingModelAvailability(boolean downloaded) {
        activity.writingModelStatusKnown = true;
        activity.writingModelDownloaded = downloaded;
    }
}
