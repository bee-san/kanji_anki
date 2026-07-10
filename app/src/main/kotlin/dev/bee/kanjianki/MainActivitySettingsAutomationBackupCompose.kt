@file:JvmName("MainActivitySettingsAutomationBackupCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsBackupPanel(model: SettingsBackupPanelModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = KaniUiTokens.PanelShape,
        color = KaniUiTokens.White,
        border = BorderStroke(1.dp, KaniUiTokens.PanelBorder),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = model.title,
                color = KaniUiTokens.Ink,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(text = model.body, color = KaniUiTokens.Muted, fontSize = 15.sp)
            Text(
                text = model.lastBackupLine,
                color = KaniUiTokens.Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = model.archiveCountLine, color = KaniUiTokens.Muted, fontSize = 15.sp)
            KaniPrimaryButton(label = model.exportLabel) { model.onExport.run() }
            KaniOutlinedButton(label = model.restoreLabel) { model.onRestore.run() }
        }
    }
}

@Composable
fun BackupRestoreConfirmDialog(model: BackupRestoreConfirmDialogModel?) {
    if (model == null) return
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { model.onDismiss.run() },
        title = { Text(model.title) },
        text = { Text(model.message) },
        confirmButton = {
            TextButton(onClick = { model.onConfirm.run() }) {
                Text(model.confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = { model.onDismiss.run() }) {
                Text(model.dismissLabel)
            }
        },
    )
}
