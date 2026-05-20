@file:JvmName("MainActivitySettingsWorkloadCompose")

package dev.bee.kanjianki

import android.view.View
import android.widget.SeekBar
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.SettingsTextCopy

private val WorkloadInk = Color(0xFF2D1635)
private val WorkloadMuted = Color(0xFF6C5674)
private val WorkloadTeal = Color(0xFF24756C)
private val WorkloadPanelBorder = Color(0xFFFFC7DE)
private val WorkloadWhite = Color(0xFFFFFFFF)
private val WorkloadPanelShape = RoundedCornerShape(24.dp)

fun interface SettingsWorkloadAction {
    fun run()
}

data class SettingsWorkloadPanelModel(
    val title: String,
    val autoMode: Boolean,
    val autoStatus: String,
    val automaticBody: String,
    val manualBody: String,
    val selectedWorkloadPercent: IntArray,
    val selectedMaxItems: IntArray,
    val workloadSlider: SeekBar,
    val maxItemsSlider: SeekBar,
    val scaleLabels: List<String>,
    val saveMaximumLabel: String,
    val manualWorkloadLabel: String,
    val saveWorkloadLabel: String,
    val automaticParetoLabel: String,
    val onSaveMaximum: SettingsWorkloadAction,
    val onEnableManual: SettingsWorkloadAction,
    val onSaveWorkload: SettingsWorkloadAction,
    val onEnableAutomatic: SettingsWorkloadAction,
)

internal fun workloadSettingsPanelView(
    activity: MainActivitySettings,
    model: SettingsWorkloadPanelModel,
): View {
    return ComposeView(activity).apply {
        layoutParams = settingsPanelLayoutParams(activity)
        setContent {
            MaterialTheme {
                SettingsWorkloadPanel(model)
            }
        }
    }
}

@Composable
fun SettingsWorkloadPanel(model: SettingsWorkloadPanelModel) {
    var workloadPercent by remember { mutableIntStateOf(model.selectedWorkloadPercent[0]) }
    var maxItems by remember { mutableIntStateOf(model.selectedMaxItems[0]) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = WorkloadPanelShape,
        color = WorkloadWhite,
        border = BorderStroke(1.dp, WorkloadPanelBorder),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = model.title,
                color = WorkloadInk,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
            if (model.autoMode) {
                AutoWorkloadContent(model, maxItems) { value ->
                    maxItems = value
                }
            } else {
                ManualWorkloadContent(model, workloadPercent, maxItems, { value ->
                    workloadPercent = value
                }, { value ->
                    maxItems = value
                })
            }
        }
    }
}

@Composable
private fun AutoWorkloadContent(
    model: SettingsWorkloadPanelModel,
    maxItems: Int,
    onMaxItemsChanged: (Int) -> Unit,
) {
    Text(
        text = model.autoStatus,
        color = WorkloadTeal,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = model.automaticBody,
        color = WorkloadMuted,
        fontSize = 15.sp
    )
    MaxItemsControl(model, maxItems, onMaxItemsChanged)
    WorkloadPrimaryButton(model.saveMaximumLabel) { model.onSaveMaximum.run() }
    WorkloadOutlinedButton(
        label = model.manualWorkloadLabel,
        modifier = Modifier.fillMaxWidth(),
        onClick = { model.onEnableManual.run() }
    )
}

@Composable
private fun ManualWorkloadContent(
    model: SettingsWorkloadPanelModel,
    workloadPercent: Int,
    maxItems: Int,
    onWorkloadChanged: (Int) -> Unit,
    onMaxItemsChanged: (Int) -> Unit,
) {
    Text(
        text = SettingsTextCopy.workloadStatusText(workloadPercent, maxItems),
        color = WorkloadTeal,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = model.manualBody,
        color = WorkloadMuted,
        fontSize = 15.sp
    )
    WorkloadSlider(model, onWorkloadChanged)
    Row(modifier = Modifier.fillMaxWidth()) {
        model.scaleLabels.forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = WorkloadMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
    MaxItemsControl(model, maxItems, onMaxItemsChanged)
    WorkloadPrimaryButton(model.saveWorkloadLabel) { model.onSaveWorkload.run() }
    WorkloadOutlinedButton(
        label = model.automaticParetoLabel,
        modifier = Modifier.fillMaxWidth(),
        onClick = { model.onEnableAutomatic.run() }
    )
}
