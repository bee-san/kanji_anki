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
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun RecoveryTimelinePanels(model: MainActivityHomeBrowseDetail.BrowseTimelinePanelsModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = model.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = BrowseInk,
            fontSize = 22.sp
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = BrowsePanelShape,
            color = BrowseWhite,
            border = BorderStroke(1.dp, ComposeColor(model.statusColor))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = model.statusText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BrowseInk
                )
                Text(
                    text = model.supportText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrowseMuted
                )
            }
        }
        if (model.emptyText != null) {
            Text(
                text = model.emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = BrowseMuted,
                fontSize = 15.sp
            )
        }
        model.events.forEach { event ->
            RecoveryTimelineEvent(event)
        }
    }
}

@Composable
private fun RecoveryTimelineEvent(model: MainActivityHomeBrowseDetail.BrowseTimelineEventModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = BrowsePanelShape,
        color = BrowseWhite,
        border = BorderStroke(1.dp, ComposeColor(model.color))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = model.dateText,
                style = MaterialTheme.typography.labelMedium,
                color = BrowseMuted
            )
            Text(
                text = model.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = BrowseInk
            )
            if (model.detail.isNotEmpty()) {
                Text(
                    text = model.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrowseMuted
                )
            }
            if (model.sourceLine.isNotEmpty()) {
                Text(
                    text = model.sourceLine,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = BrowseInk
                )
            }
        }
    }
}
