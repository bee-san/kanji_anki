package dev.bee.kanjianki

import androidx.compose.runtime.Composable

internal fun MainActivityBase.renderSettingsRoute(
    initialScrollY: Int = 0,
    content: @Composable () -> Unit,
) {
    composeRoute(MainActivityBase.NAV_SETTINGS_ROUTE, initialScrollY) {
        content()
    }
}
