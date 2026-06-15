package dev.bee.kanjianki

import androidx.compose.runtime.Composable

/**
 * Renders content under the home navigation route.
 *
 * [backAction] is the in-app destination for the system back gesture. Null
 * (the default) means the base home screen is showing and back exits the app.
 * Sub-screens hosted under the home route (browse, games, recent mistakes,
 * kanji detail) pass their own back destination.
 */
internal fun MainActivityHome.renderHomeRoute(
    backAction: Runnable? = null,
    initialScrollY: Int = 0,
    scrollPositionLabel: String? = null,
    content: @Composable () -> Unit,
) {
    rememberHomeRouteContent(backAction, content)
    composeRoute(MainActivityBase.NAV_HOME_ROUTE, initialScrollY, scrollPositionLabel) {
        content()
        HomeSyncConfirmDialog(pendingHomeSyncDialog)
    }
    this.backAction = backAction
}
