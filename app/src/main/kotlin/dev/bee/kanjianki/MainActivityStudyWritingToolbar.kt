package dev.bee.kanjianki

import android.view.View

internal class MainActivityStudyWritingToolbar(private val activity: MainActivityStudy) {
    fun buildStudyActionBar() {
        val actionBar = activity.studyActionBar ?: return
        activity.styleStudyActionBarShell()
        actionBar.removeAllViews()
        actionBar.visibility = View.VISIBLE

        actionBar.addView(writingToolActions())
        actionBar.addView(writingPrimaryActions())
        actionBar.addView(writingFallbackActions())
    }

    private fun writingToolActions(): View {
        return WritingToolActionsView(activity).also {
            activity.writingToolActionsView = it
        }
    }

    private fun writingPrimaryActions(): View {
        return WritingPrimaryActionsView(activity).also {
            activity.writingPrimaryActionsView = it
        }
    }

    private fun writingFallbackActions(): View {
        return WritingFallbackActionsView(activity).also {
            activity.writingFallbackActionsView = it
        }
    }
}
