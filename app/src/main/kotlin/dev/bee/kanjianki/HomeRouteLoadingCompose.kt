package dev.bee.kanjianki

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.core.HomeTextCopy

@Composable
internal fun HomeRouteLoadingScreen(
    title: String,
    homeLabel: String,
    onHome: () -> Unit,
) {
    HomeRouteStatusScreen(
        title = title,
        body = HomeTextCopy.loadingLabel(),
        showProgress = true,
        homeLabel = homeLabel,
        onHome = onHome,
    )
}

/**
 * Fallback screen for a failed background route load. Cold-boot route loads used
 * to rethrow their exception on the main thread and crash the whole app; this
 * keeps the shell alive and gives the user a retry path instead.
 */
@Composable
internal fun HomeRouteErrorScreen(
    title: String,
    retryLabel: String,
    onRetry: () -> Unit,
    homeLabel: String,
    onHome: () -> Unit,
) {
    HomeRouteStatusScreen(
        title = title,
        body = HomeTextCopy.routeLoadErrorBody(),
        showProgress = false,
        homeLabel = homeLabel,
        onHome = onHome,
        primaryActionLabel = retryLabel,
        onPrimaryAction = onRetry,
    )
}

/** Shared scaffold for the loading and error route states. */
@Composable
private fun HomeRouteStatusScreen(
    title: String,
    body: String,
    showProgress: Boolean,
    homeLabel: String,
    onHome: () -> Unit,
    primaryActionLabel: String? = null,
    onPrimaryAction: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = KaniTheme.colors.ink,
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = KaniTheme.colors.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = KaniTheme.colors.muted,
                )
                if (showProgress) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (primaryActionLabel != null) {
            Button(onClick = { withButtonTrace(primaryActionLabel) { onPrimaryAction() } }) {
                Text(primaryActionLabel)
            }
        }
        Button(onClick = { withButtonTrace(homeLabel) { onHome() } }) {
            Text("$homeLabel >")
        }
    }
}
