@file:JvmName("MainActivitySettingsSwitchPanelCompose")

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

/**
 * Shared card for a Settings panel whose primary control is a single Material [Switch]:
 * a title, an enabled/disabled status line, a description, and a toggle row. Extra controls
 * (for example action buttons) may be appended via [trailing]. Panels reuse this so they do
 * not each duplicate the same card scaffold.
 */
@Composable
fun SettingsSwitchPanel(
    title: String,
    status: String,
    body: String,
    enabled: Boolean,
    toggleLabel: String,
    toggleContentDescription: String,
    onToggle: (Boolean) -> Unit,
    trailing: @Composable () -> Unit = {},
) {
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
            Text(title, color = KaniUiTokens.Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(
                status,
                color = if (enabled) KaniUiTokens.Teal else KaniUiTokens.Muted,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(body, color = KaniUiTokens.Muted, fontSize = 15.sp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .toggleable(
                        value = enabled,
                        role = Role.Switch,
                        onValueChange = onToggle,
                    )
                    .semantics { contentDescription = toggleContentDescription },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(toggleLabel, color = KaniUiTokens.Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Switch(
                    checked = enabled,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(checkedTrackColor = KaniUiTokens.Primary),
                )
            }
            trailing()
        }
    }
}
