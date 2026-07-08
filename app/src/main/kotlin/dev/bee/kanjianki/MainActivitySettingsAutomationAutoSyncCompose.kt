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

private val AutoSyncInk: Color @Composable get() = KaniUiTokens.Ink
private val AutoSyncMuted: Color @Composable get() = KaniUiTokens.Muted
private val AutoSyncPanelBorder: Color @Composable get() = KaniUiTokens.PanelBorder
private val AutoSyncWhite: Color @Composable get() = KaniUiTokens.White
private val AutoSyncPanelShape = KaniUiTokens.PanelShape

@Composable
fun SettingsAutoSyncPanel(model: SettingsAutoSyncPanelModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AutoSyncPanelShape,
        color = AutoSyncWhite,
        border = BorderStroke(1.dp, AutoSyncPanelBorder),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = model.title,
                color = AutoSyncInk,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = model.status,
                color = kaniColor(model.statusColor),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = model.detail,
                color = AutoSyncMuted,
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
