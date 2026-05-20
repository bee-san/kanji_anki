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
        this.writingToolbar = new MainActivityStudyWritingToolbar(activity);
    }

    void buildStudyActionBar() {
        writingToolbar.buildStudyActionBar();
    }

    void updateResultActions() {
        WritingActionPresentation presentation = writingActionPresentation();
        updateToolActionRow(presentation);
        updatePrimaryActionRow(presentation);
        updateFallbackActionButtons(presentation);
        updateHintAndAnswerVisibility(presentation);
        if (!presentation.resultStatusVisible) {
            hideResultStatus();
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

    void updateUndoStrokeButton() {
        updateToolActionRow(writingActionPresentation());
    }

    void updateToolActionRow(WritingActionPresentation presentation) {
        if (activity.writingToolActionsView != null) {
            activity.writingToolActionsView.render(new WritingToolActionsModel(
                    presentation.undoEnabled,
                    presentation.hintText,
                    presentation.hintVisible,
                    activity::eraseWritingPad,
                    activity::undoWritingStroke,
                    activity::showWritingHint
            ));
        }
    }

    void updatePrimaryActionRow(WritingActionPresentation presentation) {
        if (activity.writingPrimaryActionsView != null) {
            activity.writingPrimaryActionsView.render(new WritingPrimaryActionsModel(
                    presentation.checkText,
                    presentation.checkVisible,
                    presentation.checkEnabled,
                    "Download checker",
                    presentation.downloadVisible,
                    presentation.nextLabel,
                    presentation.nextVisible,
                    presentation.messyPass ? activity::startCleanerRetry : activity::checkWriting,
                    writingStatus::downloadWritingModel,
                    () -> activity.submitReview(presentation.nextRating, false)
            ));
        }
    }

    void updateFallbackActionButtons(WritingActionPresentation presentation) {
        if (activity.writingFallbackActionsView != null) {
            activity.writingFallbackActionsView.render(new WritingFallbackActionsModel(
                    presentation.replayVisible,
                    presentation.manualOverrideVisible,
                    presentation.practiceWithGuideVisible,
                    activity::replayWritingAnalysis,
                    () -> activity.submitReview(activity.RATING_GOOD, true),
                    activity::startGuidedWritingRetry
            ));
        }
    }

    void updateHintAndAnswerVisibility(WritingActionPresentation presentation) {
        if (activity.writingAnswerPanelState != null) {
            activity.writingAnswerPanelState.updateVisible(presentation.answerPanelVisible);
        } else if (activity.studyAnswerPanel != null) {
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
        if (activity.activeAnalysis == null) {
            hideResultStatus();
        }
    }

    void setResultStatus(String value, int color) {
        if (activity.writingResultStatus != null) {
            activity.writingResultStatus.show(value, color);
        }
    }

    void hideResultStatus() {
        if (activity.writingResultStatus != null) {
            activity.writingResultStatus.hide();
        }
    }
}
