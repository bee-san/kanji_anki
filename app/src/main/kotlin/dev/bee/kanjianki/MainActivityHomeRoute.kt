package dev.bee.kanjianki

import androidx.compose.runtime.Composable

internal fun MainActivityHome.renderHomeRoute(content: @Composable () -> Unit) {
    rememberHomeRouteContent(content)
    composeRoute(MainActivityBase.NAV_HOME_ROUTE) {
        content()
        HomeSyncConfirmDialog(pendingHomeSyncDialog)
    }
}
