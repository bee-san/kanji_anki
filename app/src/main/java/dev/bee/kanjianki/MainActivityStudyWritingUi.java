package dev.bee.kanjianki;

import android.view.View;

import dev.bee.kanjianki.core.StudyTaskCopy;
import dev.bee.kanjianki.core.study.StrokeGuide;
import dev.bee.kanjianki.core.study.WritingActionPresentation;

final class MainActivityStudyWritingUi {
    private final MainActivityStudy activity;
    private final MainActivityStudyWritingStatus writingStatus;
    private final MainActivityStudyWritingToolbar writingToolbar;

    MainActivityStudyWritingUi(MainActivityStudy activity) {
        this.activity = activity;
        this.writingStatus = new MainActivityStudyWritingStatus(activity);
        this.writingToolbar = new MainActivityStudyWritingToolbar(activity, writingStatus);
    }

    void buildStudyActionBar() {
        writingToolbar.buildStudyActionBar();
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

    void refreshWritingModelStatus() {
        writingStatus.refreshWritingModelStatus();
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
            activity.checkWritingButton.setOnClickListener(new RunnableClickListener(
                    presentation.messyPass ? activity::startCleanerRetry : activity::checkWriting
            ));
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
                activity.nextAfterPassButton.setOnClickListener(new RunnableClickListener(() -> activity.submitReview(presentation.nextRating, false)));
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
            activity.studyStatus.setStatus(value, color);
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

    private static final class RunnableClickListener implements View.OnClickListener {
        private final Runnable action;

        RunnableClickListener(Runnable action) {
            this.action = action;
        }

        @Override
        public void onClick(View v) {
            action.run();
        }
    }
}
