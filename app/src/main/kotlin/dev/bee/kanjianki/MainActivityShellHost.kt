package dev.bee.kanjianki

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable

internal class MainActivityShellHost(private val activity: MainActivityBase) {
    fun composeRoute(selected: String, initialScrollY: Int = 0, content: @Composable () -> Unit) {
        prepareRoute(selected)
        activity.content = FrameLayout(activity)
        val scrollMirror = composeScrollMirror(initialScrollY)
        activity.contentScroll = scrollMirror
        activity.studyActionBar = null
        activity.setContent {
            MainActivityComposeRoute(
                model = MainActivityShellModel(selectedRoute = selected),
                initialScrollY = initialScrollY,
                onScrollY = { scrollMirror.scrollTo(0, it) },
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
        val scrollMirror = composeScrollMirror(initialScrollY)
        activity.contentScroll = scrollMirror
        activity.studyActionBar = null
        beforeContent()
        activity.setContent {
            MainActivityComposeRouteWithActionBar(
                model = MainActivityShellModel(selectedRoute = selected),
                initialScrollY = initialScrollY,
                onScrollY = { scrollMirror.scrollTo(0, it) },
                content = content,
                actionBar = actionBar,
            )
        }
        activity.styleSystemBars()
    }

    private fun composeScrollMirror(initialScrollY: Int): ScrollView {
        return ScrollView(activity).apply {
            val child = View(activity)
            addView(child, ViewGroup.LayoutParams(1, COMPOSE_SCROLL_MIRROR_HEIGHT))
            measure(exactMeasureSpec(1), exactMeasureSpec(1))
            layout(0, 0, 1, 1)
            child.measure(exactMeasureSpec(1), exactMeasureSpec(COMPOSE_SCROLL_MIRROR_HEIGHT))
            child.layout(0, 0, 1, COMPOSE_SCROLL_MIRROR_HEIGHT)
            scrollTo(0, initialScrollY)
        }
    }

    private fun exactMeasureSpec(size: Int): Int {
        return View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
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

    private companion object {
        const val COMPOSE_SCROLL_MIRROR_HEIGHT = 100_000
    }
}
