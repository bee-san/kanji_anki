@file:JvmName("MainActivitySettingsUpdateRunCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.SettingsTextCopy

@Composable
fun SettingsUpdateRunScreen(model: SettingsUpdateRunModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SettingsUpdateRunHomeButton(onClick = model.onHome)
        SettingsUpdateRunBackButton(onClick = model.onBack)
        Text(
            text = model.title,
            color = UpdateRunInk,
            fontSize = 32.sp,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = model.body,
            color = UpdateRunMuted,
            style = MaterialTheme.typography.bodyMedium
        )
        SettingsUpdateProgressPanel(model.progressLabel)
    }
}

@Composable
private fun SettingsUpdateProgressPanel(label: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = UpdateRunPanelShape,
        color = UpdateRunWhite,
        border = BorderStroke(1.dp, UpdateRunPanelBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(36.dp)
                    .semantics { contentDescription = label },
                color = UpdateRunPink
            )
            Text(
                text = label,
                color = UpdateRunPlum,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SettingsUpdateRunHomeButton(onClick: () -> Unit) {
    HomeFullWidthHomeButton(label = HomeTextCopy.homeLabel(), onClick = onClick)
}

@Composable
private fun SettingsUpdateRunBackButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, UpdateRunButtonBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = UpdateRunWhite,
            contentColor = UpdateRunInk
        )
    ) {
        Text(
            text = SettingsTextCopy.backToSettingsLabel(),
            color = UpdateRunInk,
            fontWeight = FontWeight.Bold
        )
    }
}

private val UpdateRunInk = ComposeColor(0xFF2D1635)
private val UpdateRunMuted = ComposeColor(0xFF6C5674)
private val UpdateRunPlum = ComposeColor(0xFF4B2552)
private val UpdateRunPink = ComposeColor(0xFFDA3A7A)
private val UpdateRunWhite = ComposeColor(0xFFFFFFFF)
private val UpdateRunPanelBorder = ComposeColor(0xFFF6CAE1)
private val UpdateRunButtonBorder = ComposeColor(0xFFEEBDDA)
private val UpdateRunPanelShape = RoundedCornerShape(18.dp)
