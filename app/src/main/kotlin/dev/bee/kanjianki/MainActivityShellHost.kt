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
                    content = content,
                    actionBar = actionBar,
                )
            }
            activity.styleSystemBars()
        }
    }

    private fun prepareRoute(selected: String) {
        activity.activeUpdateUiRunToken = 0
        if (MainActivityBase.NAV_STUDY != selected) {
            activity.abandonActiveStudyTask()
        }
        MainActivityStudyInteractionReset.resetRoute(activity)
    }

}
