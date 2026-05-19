package dev.bee.kanjianki;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import dev.bee.kanjianki.core.StudyTaskCopy;
import dev.bee.kanjianki.core.study.StrokeGuide;
import dev.bee.kanjianki.core.study.WritingActionPresentation;
import dev.bee.kanjianki.core.study.WritingFeedbackCopy;
import dev.bee.kanjianki.core.study.WritingAnalysis;
import dev.bee.kanjianki.study.WritingRecognizer;

final class MainActivityStudyWritingUi {
    private final MainActivityStudy activity;

    MainActivityStudyWritingUi(MainActivityStudy activity) {
        this.activity = activity;
    }

    void buildStudyActionBar() {
        if (activity.studyActionBar == null) {
            return;
        }
        activity.styleStudyActionBarShell();
        activity.studyActionBar.removeAllViews();
        activity.studyActionBar.setVisibility(View.VISIBLE);

        activity.studyActionBar.addView(writingToolActions());
        activity.studyActionBar.addView(writingPrimaryActions());
        activity.studyActionBar.addView(writingFallbackActions());
    }

    LinearLayout writingToolActions() {
        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button clear = activity.studySecondaryButton("Erase");
        clear.setOnClickListener(v -> activity.eraseWritingPad());
        actions.addView(clear, new LinearLayout.LayoutParams(0, activity.dp(58), 1));
        activity.undoStrokeButton = activity.studySecondaryButton("Undo");
        activity.undoStrokeButton.setOnClickListener(v -> activity.undoWritingStroke());
        actions.addView(activity.undoStrokeButton, new LinearLayout.LayoutParams(0, activity.dp(58), 1));
        activity.hintButton = activity.studySecondaryButton("Hint");
        activity.hintButton.setOnClickListener(v -> activity.showWritingHint());
        actions.addView(activity.hintButton, new LinearLayout.LayoutParams(0, activity.dp(58), 1));
        return actions;
    }

    LinearLayout writingPrimaryActions() {
        LinearLayout primaryActions = new LinearLayout(activity);
        primaryActions.setOrientation(LinearLayout.HORIZONTAL);
        activity.checkWritingButton = activity.pinkPrimaryButton("Check");
        activity.checkWritingButton.setOnClickListener(v -> activity.checkWriting());
        primaryActions.addView(activity.checkWritingButton, new LinearLayout.LayoutParams(0, activity.dp(62), 1));

        activity.downloadModelButton = activity.studySecondaryButton("Download checker");
        activity.downloadModelButton.setOnClickListener(v -> activity.downloadWritingModel());
        primaryActions.addView(activity.downloadModelButton, new LinearLayout.LayoutParams(0, activity.dp(62), 1));

        activity.nextAfterPassButton = activity.pinkPrimaryButton(activity.LABEL_PASS);
        activity.nextAfterPassButton.setOnClickListener(v -> activity.submitReview(WritingFeedbackCopy.submitRating(activity.activeAnalysis), false));
        primaryActions.addView(activity.nextAfterPassButton, new LinearLayout.LayoutParams(0, activity.dp(62), 1));
        return primaryActions;
    }

    LinearLayout writingFallbackActions() {
        LinearLayout fallbackActions = new LinearLayout(activity);
        fallbackActions.setOrientation(LinearLayout.HORIZONTAL);
        activity.replayButton = activity.studySecondaryButton("Replay");
        activity.replayButton.setOnClickListener(v -> activity.replayWritingAnalysis());
        fallbackActions.addView(activity.replayButton, new LinearLayout.LayoutParams(0, activity.dp(56), 1));

        activity.manualOverrideButton = activity.studySecondaryButton("Mark right anyway");
        activity.manualOverrideButton.setOnClickListener(v -> activity.submitReview(activity.RATING_GOOD, true));
        fallbackActions.addView(activity.manualOverrideButton, new LinearLayout.LayoutParams(0, activity.dp(56), 1));

        activity.practiceWithGuideButton = activity.studySecondaryButton("Try again with full guide");
        activity.practiceWithGuideButton.setOnClickListener(v -> activity.startGuidedWritingRetry());
        fallbackActions.addView(activity.practiceWithGuideButton, new LinearLayout.LayoutParams(0, activity.dp(56), 1));
        return fallbackActions;
    }

    void updateResultActions() {
        WritingActionPresentation presentation = writingActionPresentation();
        updateUndoStrokeButton(presentation);
        updateCheckWritingButton(presentation);
        updateDownloadModelButton(presentation);
        updateNextAfterPassButton(presentation);
        updateFallbackActionButtons(presentation);
        updateHintAndAnswerVisibility(presentation);
        if (activity.resultStatus != null && !presentation.resultStatusVisible) {
            activity.resultStatus.setVisibility(View.GONE);
        }
    }

    WritingActionPresentation writingActionPresentation() {
        WritingActionPresentation.Input input = new WritingActionPresentation.Input(activity.activeAnalysis);
        input.checkingWriting = activity.checkingWriting;
        input.canUndoStroke = activity.drawingPad != null && activity.drawingPad.canUndoStroke();
        input.writingModelStatusKnown = activity.writingModelStatusKnown;
        input.writingModelDownloaded = activity.writingModelDownloaded;
        input.hasReplaySnapshot = activity.drawingPad != null && activity.drawingPad.hasReplaySnapshot();
        input.hasInk = activity.drawingPad != null && activity.drawingPad.hasInk();
        input.guide = activity.activeSession == null ? null : activity.strokeGuide(activity.activeSession.item.kanji);
        input.canRevealMoreHelp = canRevealMoreHelp();
        input.recallTask = activity.activeSession != null && StudyTaskCopy.isRecallTask(activity.activeSession);
        input.teachingTask = activity.activeSession != null && StudyTaskCopy.isTeachingTask(activity.activeSession);
        input.currentPracticeLevel = activity.currentPracticeLevel;
        return WritingActionPresentation.from(input);
    }

    void updateCheckWritingButton(WritingActionPresentation presentation) {
        if (activity.checkWritingButton != null) {
            activity.checkWritingButton.setVisibility(presentation.checkVisible ? View.VISIBLE : View.GONE);
            activity.checkWritingButton.setEnabled(presentation.checkEnabled);
            activity.checkWritingButton.setText(presentation.checkText);
            activity.checkWritingButton.setOnClickListener(presentation.messyPass ? v -> activity.startCleanerRetry() : v -> activity.checkWriting());
        }
    }

    void updateUndoStrokeButton() {
        updateUndoStrokeButton(writingActionPresentation());
    }

    void updateUndoStrokeButton(WritingActionPresentation presentation) {
        if (activity.undoStrokeButton != null) {
            activity.undoStrokeButton.setVisibility(View.VISIBLE);
            activity.undoStrokeButton.setEnabled(presentation.undoEnabled);
        }
    }

    void updateDownloadModelButton(WritingActionPresentation presentation) {
        if (activity.downloadModelButton != null) {
            activity.downloadModelButton.setVisibility(presentation.downloadVisible ? View.VISIBLE : View.GONE);
        }
    }

    void updateNextAfterPassButton(WritingActionPresentation presentation) {
        if (activity.nextAfterPassButton != null) {
            activity.nextAfterPassButton.setVisibility(presentation.nextVisible ? View.VISIBLE : View.GONE);
            if (presentation.nextVisible) {
                activity.nextAfterPassButton.setText(presentation.nextLabel);
                activity.nextAfterPassButton.setOnClickListener(v -> activity.submitReview(presentation.nextRating, false));
            }
        }
    }

    void updateFallbackActionButtons(WritingActionPresentation presentation) {
        if (activity.manualOverrideButton != null) {
            activity.manualOverrideButton.setVisibility(presentation.manualOverrideVisible ? View.VISIBLE : View.GONE);
        }
        if (activity.practiceWithGuideButton != null) {
            activity.practiceWithGuideButton.setVisibility(presentation.practiceWithGuideVisible ? View.VISIBLE : View.GONE);
        }
        if (activity.replayButton != null) {
            activity.replayButton.setVisibility(presentation.replayVisible ? View.VISIBLE : View.GONE);
        }
    }

    void updateHintAndAnswerVisibility(WritingActionPresentation presentation) {
        if (activity.hintButton != null) {
            activity.hintButton.setVisibility(presentation.hintVisible ? View.VISIBLE : View.GONE);
            activity.hintButton.setText(presentation.hintText);
        }
        if (activity.studyAnswerPanel != null) {
            activity.studyAnswerPanel.setVisibility(presentation.answerPanelVisible ? View.VISIBLE : View.GONE);
        }
    }

    boolean canRevealMoreHelp() {
        if (activity.activeSession == null) {
            return false;
        }
        StrokeGuide guide = activity.strokeGuide(activity.activeSession.item.kanji);
        return activity.hintProgression.canRevealMoreHelp(activity.currentHintState, guide);
    }

    void setStudyStatus(String value, int color) {
        if (activity.studyStatus != null) {
            activity.studyStatus.setText(value);
            activity.studyStatus.setTextColor(color);
        }
        if (activity.resultStatus != null && activity.activeAnalysis == null) {
            activity.resultStatus.setVisibility(View.GONE);
        }
    }

    void setResultStatus(String value, int color) {
        if (activity.resultStatus != null) {
            activity.resultStatus.setText(value);
            activity.resultStatus.setTextColor(color);
            activity.resultStatus.setVisibility(View.VISIBLE);
        }
    }

    void refreshWritingModelStatus() {
        activity.writingModelStatusKnown = false;
        activity.writingModelDownloaded = false;
        updateResultActions();
        String token = activity.activeSession == null ? null : activity.activeSession.token;
        WritingRecognizer recognizer = activity.currentWritingRecognizer();
        if (recognizer == null) {
            updateWritingModelAvailability(false);
            setStudyStatus(
                    WritingFeedbackCopy.unavailableModelStatusMessage(
                            WritingFeedbackCopy.guideLabel(activity.currentHintState, activity.strokeGuide(activity.activeSession.item.kanji))
                    ),
                    activity.CORAL
            );
            updateResultActions();
            return;
        }
        recognizer.modelStatus().whenComplete((status, error) -> activity.main.post(() -> {
            if (token == null || !activity.isActiveToken(token)) {
                return;
            }
            updateWritingModelAvailability(error == null && status != null && status.downloaded);
            updateResultActions();
            if (activity.activeAnalysis != null || activity.checkingWriting) {
                return;
            }
            setWritingModelStatusMessage(status, error);
        }));
    }

    void setWritingModelStatusMessage(WritingRecognizer.ModelStatus status, Throwable error) {
        String prefix = WritingFeedbackCopy.guideLabel(activity.currentHintState, activity.strokeGuide(activity.activeSession.item.kanji));
        if (error != null || status == null) {
            setStudyStatus(WritingFeedbackCopy.modelStatusMessage(prefix, status != null, false, error != null), activity.CORAL);
            return;
        }
        if (!status.downloaded) {
            setStudyStatus(WritingFeedbackCopy.modelStatusMessage(prefix, true, false, false), activity.CORAL);
            return;
        }
        setStudyStatus(WritingFeedbackCopy.modelStatusMessage(prefix, true, true, false), activity.MUTED);
    }

    void downloadWritingModel() {
        String token = activity.activeSession == null ? null : activity.activeSession.token;
        WritingRecognizer recognizer = activity.currentWritingRecognizer();
        if (recognizer == null) {
            setStudyStatus("The handwriting checker is unavailable on this device.", activity.CORAL);
            return;
        }
        setStudyStatus("Downloading handwriting checker...", activity.MUTED);
        recognizer.downloadModel().whenComplete((status, error) -> activity.main.post(() -> {
            if (token != null && !activity.isActiveToken(token)) {
                return;
            }
            if (error != null) {
                updateWritingModelAvailability(false);
                setStudyStatus("Handwriting checker download failed: " + error.getMessage(), activity.CORAL);
            } else {
                updateWritingModelAvailability(true);
                setStudyStatus("Handwriting checker ready.", activity.TEAL);
            }
            updateResultActions();
        }));
    }

    void updateWritingModelAvailability(boolean downloaded) {
        activity.writingModelStatusKnown = true;
        activity.writingModelDownloaded = downloaded;
    }
}
