@file:JvmName("MainActivitySettingsUpdateRunCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
    KaniOutlinedButton(
        label = SettingsTextCopy.backToSettingsLabel(),
        minHeightDp = 54,
        onClick = onClick
    )
}

private val UpdateRunInk = KaniUiTokens.Ink
private val UpdateRunMuted = KaniUiTokens.Muted
private val UpdateRunPlum = ComposeColor(0xFF4B2552)
private val UpdateRunPink = KaniUiTokens.Primary
private val UpdateRunWhite = KaniUiTokens.White
private val UpdateRunPanelBorder = ComposeColor(0xFFF6CAE1)
private val UpdateRunPanelShape = RoundedCornerShape(18.dp)
