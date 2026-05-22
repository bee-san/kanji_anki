package dev.bee.kanjianki

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowInsetsCompat

internal class MainActivityShellHost(private val activity: MainActivityBase) {
    fun base(selected: String) {
        prepareRoute(selected)

        val root = LinearLayout(activity)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(if (MainActivityBase.NAV_STUDY == selected) MainActivityUiSupport.STUDY_BG_SOFT else MainActivityUiSupport.BG)
        activity.setContent {
            MainActivityShell(
                legacyRoot = root,
                model = MainActivityShellModel(selectedRoute = selected)
            )
        }
        activity.styleSystemBars()

        activity.content = LinearLayout(activity)
        activity.content.orientation = LinearLayout.VERTICAL
        activity.content.setPadding(activity.dp(18), activity.dp(18), activity.dp(18), activity.dp(18))

        val scroll = ScrollView(activity)
        activity.contentScroll = scroll
        scroll.addView(activity.content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        activity.studyActionBar = LinearLayout(activity)
        activity.studyActionBar?.orientation = LinearLayout.VERTICAL
        activity.applyStudyActionBarPadding()
        activity.studyActionBar?.setBackgroundColor(MainActivityUiSupport.STUDY_BG_SOFT)
        activity.studyActionBar?.visibility = View.GONE
        root.addView(activity.studyActionBar, LinearLayout.LayoutParams(-1, -2))

        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = WindowInsetsCompat.toWindowInsetsCompat(insets, view)
                .getInsets(WindowInsetsCompat.Type.systemBars())
            activity.studyActionBarBottomInset = bars.bottom
            activity.content.setPadding(
                activity.dp(18),
                activity.dp(18) + bars.top,
                activity.dp(18),
                activity.dp(18) + bars.bottom,
            )
            activity.applyStudyActionBarPadding()
            insets
        }
        requestInsetsWhenAttached(root)
    }

    fun composeRoute(selected: String, initialScrollY: Int = 0, content: @Composable () -> Unit) {
        prepareRoute(selected)
        activity.content = LinearLayout(activity)
        activity.content.orientation = LinearLayout.VERTICAL
        // Keep the legacy content field structurally compatible while this route renders through Activity.setContent.
        activity.content.addView(ComposeView(activity))
        val scrollMirror = composeScrollMirror(initialScrollY)
        activity.contentScroll = scrollMirror
        activity.studyActionBar = LinearLayout(activity)
        activity.studyActionBar?.orientation = LinearLayout.VERTICAL
        activity.studyActionBar?.visibility = View.GONE
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

    private fun requestInsetsWhenAttached(root: View) {
        if (root.isAttachedToWindow) {
            root.requestApplyInsets()
            return
        }
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                view.removeOnAttachStateChangeListener(this)
                view.requestApplyInsets()
            }

            override fun onViewDetachedFromWindow(view: View) = Unit
        })
    }

    private companion object {
        const val COMPOSE_SCROLL_MIRROR_HEIGHT = 100_000
    }
}
