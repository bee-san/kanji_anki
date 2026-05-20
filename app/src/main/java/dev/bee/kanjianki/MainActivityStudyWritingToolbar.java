package dev.bee.kanjianki;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import dev.bee.kanjianki.core.study.WritingFeedbackCopy;

final class MainActivityStudyWritingToolbar {
    private final MainActivityStudy activity;
    private final MainActivityStudyWritingStatus writingStatus;

    MainActivityStudyWritingToolbar(MainActivityStudy activity, MainActivityStudyWritingStatus writingStatus) {
        this.activity = activity;
        this.writingStatus = writingStatus;
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

    private View writingToolActions() {
        activity.writingToolActionsView = new WritingToolActionsView(activity);

        activity.undoStrokeButton = new Button(activity);
        activity.undoStrokeButton.setText(undoLabel());
        activity.undoStrokeButton.setOnClickListener(new RunnableClickListener(activity::undoWritingStroke));

        activity.hintButton = new Button(activity);
        activity.hintButton.setText(hintLabel());
        activity.hintButton.setOnClickListener(new RunnableClickListener(activity::showWritingHint));
        return activity.writingToolActionsView;
    }

    private String undoLabel() {
        return "Undo";
    }

    private String hintLabel() {
        return "Hint";
    }

    private View writingPrimaryActions() {
        activity.writingPrimaryActionsView = new WritingPrimaryActionsView(activity);

        activity.checkWritingButton = new Button(activity);
        activity.checkWritingButton.setText(WritingFeedbackCopy.checkWritingButtonText(false, false));
        activity.checkWritingButton.setOnClickListener(new RunnableClickListener(activity::checkWriting));

        activity.downloadModelButton = new Button(activity);
        activity.downloadModelButton.setText(downloadCheckerLabel());
        activity.downloadModelButton.setOnClickListener(new RunnableClickListener(writingStatus::downloadWritingModel));

        activity.nextAfterPassButton = new Button(activity);
        activity.nextAfterPassButton.setText(activity.LABEL_PASS);
        activity.nextAfterPassButton.setVisibility(View.GONE);
        return activity.writingPrimaryActionsView;
    }

    private String downloadCheckerLabel() {
        return "Download checker";
    }

    private View writingFallbackActions() {
        activity.writingFallbackActionsView = new WritingFallbackActionsView(activity);

        activity.replayButton = new Button(activity);
        activity.replayButton.setText(replayLabel());
        activity.replayButton.setOnClickListener(new RunnableClickListener(activity::replayWritingAnalysis));

        activity.manualOverrideButton = new Button(activity);
        activity.manualOverrideButton.setText(manualOverrideLabel());
        activity.manualOverrideButton.setOnClickListener(new RunnableClickListener(() -> activity.submitReview(activity.RATING_GOOD, true)));

        activity.practiceWithGuideButton = new Button(activity);
        activity.practiceWithGuideButton.setText(practiceWithGuideLabel());
        activity.practiceWithGuideButton.setOnClickListener(new RunnableClickListener(activity::startGuidedWritingRetry));
        return activity.writingFallbackActionsView;
    }

    private String replayLabel() {
        return "Replay";
    }

    private String manualOverrideLabel() {
        return "Mark right anyway";
    }

    private String practiceWithGuideLabel() {
        return "Try again with full guide";
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
