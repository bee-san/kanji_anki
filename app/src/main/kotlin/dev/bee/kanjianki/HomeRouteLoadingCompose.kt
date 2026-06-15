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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.core.HomeTextCopy

@Composable
internal fun HomeRouteLoadingScreen(
    title: String,
    homeLabel: String,
    onHome: () -> Unit,
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
                    text = HomeTextCopy.loadingLabel(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = KaniTheme.colors.muted,
                )
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        Button(onClick = { withButtonTrace(homeLabel) { onHome() } }) {
            Text("$homeLabel >")
        }
    }
}
