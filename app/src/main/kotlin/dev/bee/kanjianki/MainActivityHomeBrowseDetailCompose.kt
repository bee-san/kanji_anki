@file:JvmName("MainActivityHomeBrowseDetailCompose")

package dev.bee.kanjianki

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = ComposeColor(0xFF2D1635)
private val Muted = ComposeColor(0xFF6C5674)
private val White = ComposeColor(0xFFFFFFFF)
private val PanelShape = RoundedCornerShape(18.dp)

internal fun recoveryTimelinePanelsView(activity: MainActivityHomeBrowseDetail, model: MainActivityHomeBrowseDetail.BrowseTimelinePanelsModel): View {
    return ComposeView(activity.home()).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContent {
            MaterialTheme {
                RecoveryTimelinePanels(model)
            }
        }
    }
}

@Composable
fun RecoveryTimelinePanels(model: MainActivityHomeBrowseDetail.BrowseTimelinePanelsModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = model.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Ink,
            fontSize = 22.sp
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = PanelShape,
            color = White,
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
                    color = Ink
                )
                Text(
                    text = model.supportText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted
                )
            }
        }
        if (model.emptyText != null) {
            Text(
                text = model.emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
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
        shape = PanelShape,
        color = White,
        border = BorderStroke(1.dp, ComposeColor(model.color))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = model.dateText,
                style = MaterialTheme.typography.labelMedium,
                color = Muted
            )
            Text(
                text = model.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            if (model.detail.isNotEmpty()) {
                Text(
                    text = model.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted
                )
            }
            if (model.sourceLine.isNotEmpty()) {
                Text(
                    text = model.sourceLine,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink
                )
            }
        }
    }
}
