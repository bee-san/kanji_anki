@file:JvmName("MainActivitySettingsAutomationAutoSyncCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AutoSyncInk = Color(0xFF2D1635)
private val AutoSyncMuted = Color(0xFF6C5674)
private val AutoSyncPinkDark = Color(0xFFDA3A7A)
private val AutoSyncPanelBorder = Color(0xFFFFC7DE)
private val AutoSyncButtonBorder = Color(0xFFEEBDDA)
private val AutoSyncWhite = Color(0xFFFFFFFF)
private val AutoSyncPanelShape = RoundedCornerShape(24.dp)
private val AutoSyncButtonShape = RoundedCornerShape(12.dp)

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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = model.title,
                color = AutoSyncInk,
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
        Button(
            onClick = { action.run() },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            shape = AutoSyncButtonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = AutoSyncPinkDark,
                contentColor = AutoSyncWhite
            )
        ) {
            AutoSyncButtonText(label)
        }
    } else {
        OutlinedButton(
            onClick = { action.run() },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp),
            shape = AutoSyncButtonShape,
            border = BorderStroke(1.dp, AutoSyncButtonBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = AutoSyncWhite,
                contentColor = AutoSyncInk
            )
        ) {
            AutoSyncButtonText(label)
        }
    }
}

@Composable
private fun AutoSyncButtonText(label: String) {
    Text(
        text = label,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
    )
}
