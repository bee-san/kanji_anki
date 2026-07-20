package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.core.HomeTextCopy

internal fun homeUpdateCheckFailedBannerTestTag(): String = "home-update-check-failed"

/**
 * Truthful, recoverable offline state for the in-app update check. When the last
 * update check failed for a connectivity reason (no route, DNS, TLS/captive
 * portal, empty response) the store lights an update-check-failed flag and the
 * home model surfaces [line] + [onRetry]. This banner is the only place the user
 * sees that failure and gets a retry affordance; before it existed the failure
 * was silently swallowed. The retry re-runs the manual update check, which is
 * idempotent — tapping it while online clears the flag and removes the banner.
 */
@Composable
internal fun HomeUpdateCheckFailedBanner(
    line: String,
    onRetry: (() -> Unit)?,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(homeUpdateCheckFailedBannerTestTag()),
        shape = KaniUiTokens.PanelShape,
        color = KaniTheme.colors.panelSoft,
        border = BorderStroke(1.dp, KaniUiTokens.PanelBorder),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = KaniUiTokens.Ink,
            )
            if (onRetry != null) {
                KaniOutlinedButton(
                    label = HomeTextCopy.retryLabel(),
                    minHeightDp = 48,
                    textSizeSp = 14,
                    onClick = onRetry,
                )
            }
        }
    }
}
