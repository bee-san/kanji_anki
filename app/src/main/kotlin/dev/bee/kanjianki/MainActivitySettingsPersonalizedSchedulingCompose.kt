@file:JvmName("MainActivitySettingsPersonalizedSchedulingCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsPersonalizedSchedulingPanel(model: SettingsPersonalizedSchedulingPanelModel) {
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
            Text(model.title, color = KaniUiTokens.Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(
                model.status,
                color = if (model.state.enabled) KaniUiTokens.Teal else KaniUiTokens.Muted,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(model.body, color = KaniUiTokens.Muted, fontSize = 15.sp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .toggleable(
                        value = model.state.enabled,
                        role = Role.Switch,
                        onValueChange = { enabled ->
                            model.state.enabled = enabled
                            model.onToggle.setEnabled(enabled)
                        },
                    )
                    .semantics {
                        contentDescription = SettingsPersonalizedSchedulingControlDescriptions.TOGGLE
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(model.toggleLabel, color = KaniUiTokens.Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Switch(
                    checked = model.state.enabled,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(checkedTrackColor = KaniUiTokens.Primary),
                )
            }
            KaniPrimaryButton(
                label = model.fitNowLabel,
                modifier = Modifier.semantics {
                    contentDescription = SettingsPersonalizedSchedulingControlDescriptions.FIT_NOW
                },
            ) { model.onFitNow.run() }
            KaniOutlinedButton(
                label = model.resetLabel,
                modifier = Modifier.semantics {
                    contentDescription = SettingsPersonalizedSchedulingControlDescriptions.RESET
                },
            ) { model.onReset.run() }
        }
    }
}
