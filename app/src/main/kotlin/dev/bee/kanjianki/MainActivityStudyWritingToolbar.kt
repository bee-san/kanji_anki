package dev.bee.kanjianki

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.ComposeView

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
        actionBar.addView(FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            addView(
                ComposeView(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                    setContent {
                        MaterialTheme {
                            Surface {
                                WritingActionsBar(state)
                            }
                        }
                    }
                },
            )
        })
    }

    fun buildComposeActionBarState(): WritingActionsBarState {
        val state = WritingActionsBarState()
        activity.writingToolActionsView = WritingToolActionsView(activity, state)
        activity.writingPrimaryActionsView = WritingPrimaryActionsView(activity, state)
        activity.writingFallbackActionsView = WritingFallbackActionsView(activity, state)
        return state
    }
}
