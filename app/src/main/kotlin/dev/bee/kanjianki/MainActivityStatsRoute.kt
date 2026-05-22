package dev.bee.kanjianki

import androidx.compose.runtime.Composable

internal fun MainActivityBase.renderStatsRoute(content: @Composable () -> Unit) {
    composeRoute(MainActivityBase.NAV_STATS_ROUTE) {
        content()
    }
}
