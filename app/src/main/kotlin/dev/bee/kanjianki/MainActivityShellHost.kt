package dev.bee.kanjianki

import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.compose.setContent
import androidx.core.view.WindowInsetsCompat

internal class MainActivityShellHost(private val activity: MainActivityBase) {
    fun base(selected: String) {
        activity.activeUpdateUiRunToken = 0
        if (MainActivityBase.NAV_STUDY != selected) {
            activity.abandonActiveStudyTask()
        }
        resetStudyInteractionState()

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

    private fun resetStudyInteractionState() {
        activity.flashcardGestureArea = null
        activity.flashcardCard = null
        activity.writingAnswerPanelState = null
        activity.flashcardRevealState = null
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
}
