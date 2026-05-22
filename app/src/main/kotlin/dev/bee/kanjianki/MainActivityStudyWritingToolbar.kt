package dev.bee.kanjianki

import android.view.View

internal class MainActivityStudyWritingToolbar(private val activity: MainActivityStudy) {
    fun buildStudyActionBar() {
        val actionBar = activity.studyActionBar ?: return
        activity.styleStudyActionBarShell()
        actionBar.removeAllViews()
        actionBar.visibility = View.VISIBLE

        val state = WritingActionsBarState()
        activity.writingToolActionsView = WritingToolActionsView(activity, state)
        activity.writingPrimaryActionsView = WritingPrimaryActionsView(activity, state)
        activity.writingFallbackActionsView = WritingFallbackActionsView(activity, state)
        actionBar.addView(writingActionsBarView(activity, state))
    }

    fun buildComposeActionBarState(): WritingActionsBarState {
        val state = WritingActionsBarState()
        activity.writingToolActionsView = WritingToolActionsView(activity, state)
        activity.writingPrimaryActionsView = WritingPrimaryActionsView(activity, state)
        activity.writingFallbackActionsView = WritingFallbackActionsView(activity, state)
        return state
    }
}
