package dev.bee.kanjianki

import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable

internal class MainActivityShellHost(private val activity: MainActivityBase) {
    fun composeRoute(selected: String, initialScrollY: Int = 0, content: @Composable () -> Unit) {
        withRouteTrace(selected) {
            prepareRoute(selected)
            activity.contentScrollY = initialScrollY
            activity.setContent {
                MainActivityComposeRoute(
                    model = MainActivityShellModel(selectedRoute = selected),
                    initialScrollY = initialScrollY,
                    onScrollY = { activity.contentScrollY = it },
                    navActions = navActions(),
                    content = content
                )
            }
            activity.styleSystemBars()
        }
    }

    fun composeRouteWithActionBar(
        selected: String,
        initialScrollY: Int = 0,
        beforeContent: () -> Unit = {},
        content: @Composable () -> Unit,
        actionBar: @Composable () -> Unit,
    ) {
        withRouteTrace(selected) {
            prepareRoute(selected)
            activity.contentScrollY = initialScrollY
            beforeContent()
            activity.setContent {
                MainActivityComposeRouteWithActionBar(
                    model = MainActivityShellModel(selectedRoute = selected),
                    initialScrollY = initialScrollY,
                    onScrollY = { activity.contentScrollY = it },
                    navActions = navActions(),
                    content = content,
                    actionBar = actionBar,
                )
            }
            activity.styleSystemBars()
        }
    }

    private fun navActions(): KaniNavActions {
        return KaniNavActions(
            onHome = { activity.renderHome() },
            onStudy = { activity.renderStudy() },
            onStats = { activity.renderStats() },
            onSettings = { activity.renderSettings() },
        )
    }

    private fun prepareRoute(selected: String) {
        activity.activeUpdateUiRunToken = 0
        if (MainActivityBase.NAV_STUDY != selected) {
            activity.abandonActiveStudyTask()
            activity.studyUndoState.clear()
        }
        MainActivityStudyInteractionReset.resetRoute(activity)
        activity.backAction = if (MainActivityBase.NAV_HOME_ROUTE == selected) {
            null
        } else {
            Runnable { activity.renderHome() }
        }
    }

}
