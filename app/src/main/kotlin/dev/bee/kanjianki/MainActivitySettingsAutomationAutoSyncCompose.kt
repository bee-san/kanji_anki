@file:JvmName("MainActivitySettingsAutomationAutoSyncCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsAutoSyncPanel(model: SettingsAutoSyncPanelModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = KaniUiTokens.PanelShape,
        color = KaniUiTokens.White,
        border = BorderStroke(1.dp, KaniUiTokens.PanelBorder),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = model.title,
                color = KaniUiTokens.Ink,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = model.status,
                color = Color(model.statusColor),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = model.detail,
                color = KaniUiTokens.Muted,
                fontSize = 15.sp
            )
            AutoSyncActionButton(model)
        }
    }
}

@Composable
private fun AutoSyncActionButton(model: SettingsAutoSyncPanelModel) {
    val label = model.actionLabel ?: return
    val action = model.onAction ?: return
    if (model.primaryAction) {
        KaniPrimaryButton(label = label) { action.run() }
    } else {
        KaniOutlinedButton(label = label) { action.run() }
    }
}
