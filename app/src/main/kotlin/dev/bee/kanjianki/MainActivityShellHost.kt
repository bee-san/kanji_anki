package dev.bee.kanjianki

import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable

internal class MainActivityShellHost(private val activity: MainActivityBase) {
    fun composeRoute(selected: String, initialScrollY: Int = 0, content: @Composable () -> Unit) {
        prepareRoute(selected)
        activity.content = FrameLayout(activity)
        activity.contentScrollY = initialScrollY
        activity.studyActionBar = null
        activity.setContent {
            MainActivityComposeRoute(
                model = MainActivityShellModel(selectedRoute = selected),
                initialScrollY = initialScrollY,
                onScrollY = { activity.contentScrollY = it },
                content = content
            )
        }
        activity.styleSystemBars()
    }

    fun composeRouteWithActionBar(
        selected: String,
        initialScrollY: Int = 0,
        beforeContent: () -> Unit = {},
        content: @Composable () -> Unit,
        actionBar: @Composable () -> Unit,
    ) {
        prepareRoute(selected)
        activity.content = FrameLayout(activity)
        activity.contentScrollY = initialScrollY
        activity.studyActionBar = null
        beforeContent()
        activity.setContent {
            MainActivityComposeRouteWithActionBar(
                model = MainActivityShellModel(selectedRoute = selected),
                initialScrollY = initialScrollY,
                onScrollY = { activity.contentScrollY = it },
                content = content,
                actionBar = actionBar,
            )
        }
        activity.styleSystemBars()
    }

    private fun prepareRoute(selected: String) {
        activity.activeUpdateUiRunToken = 0
        if (MainActivityBase.NAV_STUDY != selected) {
            activity.abandonActiveStudyTask()
        }
        resetStudyInteractionState()
    }

    private fun resetStudyInteractionState() {
        activity.flashcardGestureArea = null
        activity.flashcardCard = null
        activity.writingAnswerPanelState = null
        activity.flashcardRevealState = null
        activity.flashcardActionBarState = null
        activity.flashcardAnswerRevealed = false
        activity.flashcardTouchTracking = false
    }

}
