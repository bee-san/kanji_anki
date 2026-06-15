package dev.bee.kanjianki

import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import dev.bee.kanjianki.theme.resolveSystemBars

internal class MainActivityShellHost(private val activity: MainActivityBase) {
    fun composeRoute(selected: String, initialScrollY: Int = 0, scrollPositionLabel: String? = null, content: @Composable () -> Unit) {
        withRouteTrace(selected) {
            prepareRoute(selected)
            activity.contentScrollY = initialScrollY
            val themeChoice = activity.screenshotThemeChoiceOverride ?: activity.store.appThemeChoice()
            val isSystemDarkTheme = MainActivityUiSupport.isNightMode(activity.resources.configuration)
            val systemBars = themeChoice.resolveSystemBars(isSystemDarkTheme)
            activity.setContent {
                MainActivityComposeRoute(
                    model = MainActivityShellModel(selectedRoute = selected, scrollPositionLabel = scrollPositionLabel),
                    initialScrollY = initialScrollY,
                    onScrollY = { activity.contentScrollY = it },
                    navActions = navActions(),
                    themeChoice = themeChoice,
                    isSystemDarkTheme = isSystemDarkTheme,
                    content = content,
                )
            }
            activity.styleSystemBars(systemBars)
        }
    }

    fun composeRouteWithActionBar(
        selected: String,
        initialScrollY: Int = 0,
        scrollPositionLabel: String? = null,
        beforeContent: () -> Unit = {},
        content: @Composable () -> Unit,
        actionBar: @Composable () -> Unit,
    ) {
        withRouteTrace(selected) {
            prepareRoute(selected)
            activity.contentScrollY = initialScrollY
            beforeContent()
            val themeChoice = activity.screenshotThemeChoiceOverride ?: activity.store.appThemeChoice()
            val isSystemDarkTheme = MainActivityUiSupport.isNightMode(activity.resources.configuration)
            val systemBars = themeChoice.resolveSystemBars(isSystemDarkTheme)
            activity.setContent {
                MainActivityComposeRouteWithActionBar(
                    model = MainActivityShellModel(selectedRoute = selected, scrollPositionLabel = scrollPositionLabel),
                    initialScrollY = initialScrollY,
                    onScrollY = { activity.contentScrollY = it },
                    navActions = navActions(),
                    themeChoice = themeChoice,
                    isSystemDarkTheme = isSystemDarkTheme,
                    content = content,
                    actionBar = actionBar,
                )
            }
            activity.styleSystemBars(systemBars)
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
