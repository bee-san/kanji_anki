@file:JvmName("MainActivitySettingsMemoryModelCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.memory.MemoryModelCopy
import dev.bee.kanjianki.memory.RwkvRuntime

@Composable
internal fun SettingsMemoryModelPanel(model: SettingsMemoryModelPanelModel) {
    val status by model.status.collectAsState()
    val busy = status is RwkvRuntime.Status.Rebuilding || status == RwkvRuntime.Status.Checking
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
                MemoryModelCopy.statusLine(status),
                color = if (status == RwkvRuntime.Status.Ready) KaniUiTokens.Teal else KaniUiTokens.Muted,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(model.body, color = KaniUiTokens.Muted, fontSize = 15.sp)
            Text(model.modelLine, color = KaniUiTokens.Ink, fontSize = 15.sp)
            Text(model.provenanceLine, color = KaniUiTokens.Muted, fontSize = 13.sp)
            if (!busy) {
                KaniOutlinedButton(
                    label = model.rebuildLabel,
                    modifier = Modifier.semantics {
                        contentDescription = SettingsMemoryModelControlDescriptions.REBUILD
                    },
                ) { model.onRebuild.run() }
            }
        }
    }
}
