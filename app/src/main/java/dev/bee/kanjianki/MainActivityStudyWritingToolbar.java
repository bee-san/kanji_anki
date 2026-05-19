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

    private LinearLayout writingToolActions() {
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

    private LinearLayout writingPrimaryActions() {
        LinearLayout primaryActions = new LinearLayout(activity);
        primaryActions.setOrientation(LinearLayout.HORIZONTAL);
        activity.checkWritingButton = activity.pinkPrimaryButton("Check");
        activity.checkWritingButton.setOnClickListener(v -> activity.checkWriting());
        primaryActions.addView(activity.checkWritingButton, new LinearLayout.LayoutParams(0, activity.dp(62), 1));

        activity.downloadModelButton = activity.studySecondaryButton("Download checker");
        activity.downloadModelButton.setOnClickListener(v -> writingStatus.downloadWritingModel());
        primaryActions.addView(activity.downloadModelButton, new LinearLayout.LayoutParams(0, activity.dp(62), 1));

        activity.nextAfterPassButton = activity.pinkPrimaryButton(activity.LABEL_PASS);
        activity.nextAfterPassButton.setOnClickListener(v -> activity.submitReview(WritingFeedbackCopy.submitRating(activity.activeAnalysis), false));
        primaryActions.addView(activity.nextAfterPassButton, new LinearLayout.LayoutParams(0, activity.dp(62), 1));
        return primaryActions;
    }

    private LinearLayout writingFallbackActions() {
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
}
