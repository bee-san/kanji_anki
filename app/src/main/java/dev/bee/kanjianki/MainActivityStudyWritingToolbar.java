package dev.bee.kanjianki;

import android.view.View;

final class MainActivityStudyWritingToolbar {
    private final MainActivityStudy activity;

    MainActivityStudyWritingToolbar(MainActivityStudy activity) {
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

    private View writingToolActions() {
        activity.writingToolActionsView = new WritingToolActionsView(activity);
        return activity.writingToolActionsView;
    }

    private View writingPrimaryActions() {
        activity.writingPrimaryActionsView = new WritingPrimaryActionsView(activity);
        return activity.writingPrimaryActionsView;
    }

    private View writingFallbackActions() {
        activity.writingFallbackActionsView = new WritingFallbackActionsView(activity);
        return activity.writingFallbackActionsView;
    }
}
