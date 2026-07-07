@file:JvmName("MainActivitySettingsAutomationDebugLogCompose")

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

private val DebugLogInk: Color @Composable get() = KaniUiTokens.Ink
private val DebugLogMuted: Color @Composable get() = KaniUiTokens.Muted
private val DebugLogPanelBorder: Color @Composable get() = KaniUiTokens.PanelBorder
private val DebugLogWhite: Color @Composable get() = KaniUiTokens.White
private val DebugLogPanelShape = KaniUiTokens.PanelShape

@Composable
fun SettingsDebugLogPanel(model: SettingsDebugLogPanelModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = DebugLogPanelShape,
        color = DebugLogWhite,
        border = BorderStroke(1.dp, DebugLogPanelBorder),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = model.title,
                color = DebugLogInk,
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
                color = DebugLogMuted,
                fontSize = 15.sp
            )
            if (model.togglePrimary) {
                KaniPrimaryButton(label = model.toggleLabel) { model.onToggle.run() }
            } else {
                KaniOutlinedButton(label = model.toggleLabel) { model.onToggle.run() }
            }
            KaniOutlinedButton(label = model.shareLabel) { model.onShare.run() }
        }
    }
}
