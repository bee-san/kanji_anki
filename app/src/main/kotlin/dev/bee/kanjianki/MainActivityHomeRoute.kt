package dev.bee.kanjianki

import androidx.compose.runtime.Composable

internal fun MainActivityBase.renderHomeRoute(content: @Composable () -> Unit) {
    composeRoute(MainActivityBase.NAV_HOME_ROUTE) {
        content()
    }
}
