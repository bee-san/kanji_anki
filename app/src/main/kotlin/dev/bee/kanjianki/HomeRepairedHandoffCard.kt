package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.core.RepairedHandoffPolicy

data class HomeRepairedHandoffCardModel(
    val card: RepairedHandoffPolicy.Card,
    val onCopySearch: () -> Unit,
    val onDismiss: () -> Unit,
)

@Composable
internal fun HomeRepairedHandoffCard(model: HomeRepairedHandoffCardModel) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home-repaired-handoff"),
        shape = KaniUiTokens.PanelShape,
        color = KaniTheme.colors.panelSoft,
        border = BorderStroke(1.dp, KaniUiTokens.PanelBorder),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = model.card.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = KaniUiTokens.Ink,
            )
            Text(
                text = model.card.body,
                style = MaterialTheme.typography.bodyMedium,
                color = KaniUiTokens.Muted,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KaniPrimaryButton(
                    label = model.card.primaryLabel,
                    modifier = Modifier.weight(1f),
                    minHeightDp = 48,
                    textSizeSp = 14,
                    onClick = model.onCopySearch,
                )
                KaniOutlinedButton(
                    label = model.card.dismissLabel,
                    modifier = Modifier.weight(1f),
                    minHeightDp = 48,
                    textSizeSp = 14,
                    onClick = model.onDismiss,
                )
            }
        }
    }
}
