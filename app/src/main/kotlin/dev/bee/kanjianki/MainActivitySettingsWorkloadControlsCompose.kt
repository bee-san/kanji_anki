package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.AdaptiveLoadPlanner
import dev.bee.kanjianki.core.SettingsTextCopy
import kotlin.math.roundToInt

private val WorkloadControlInk = Color(0xFF2D1635)
private val WorkloadControlTeal = Color(0xFF24756C)
private val WorkloadControlPinkDark = Color(0xFFDA3A7A)
private val WorkloadControlButtonBorder = Color(0xFFEEBDDA)
private val WorkloadControlWhite = Color(0xFFFFFFFF)
private val WorkloadControlButtonShape = RoundedCornerShape(12.dp)

object SettingsWorkloadControlDescriptions {
    const val WORKLOAD_PERCENT_SLIDER = "Daily workload percentage"
    const val MAX_ITEMS_SLIDER = "Maximum Pareto items"
}

object SettingsWorkloadTestTags {
    const val WORKLOAD_PERCENT_SLIDER = "settings-workload-percent-slider"
    const val MAX_ITEMS_SLIDER = "settings-workload-max-items-slider"
}

@Composable
internal fun WorkloadSlider(
    model: SettingsWorkloadPanelModel,
    workloadPercent: Int,
    onWorkloadChanged: (Int) -> Unit,
) {
    Slider(
        value = workloadPercent.toFloat(),
        onValueChange = { progress ->
            val snapped = AdaptiveLoadPlanner.snapWorkloadPercent(progress.roundToInt())
            model.selectedWorkloadPercent[0] = snapped
            onWorkloadChanged(snapped)
        },
        valueRange = 0f..100f,
        steps = 19,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag(SettingsWorkloadTestTags.WORKLOAD_PERCENT_SLIDER)
            .semantics { contentDescription = SettingsWorkloadControlDescriptions.WORKLOAD_PERCENT_SLIDER }
    )
}

@Composable
internal fun MaxItemsControl(
    model: SettingsWorkloadPanelModel,
    maxItems: Int,
    onMaxItemsChanged: (Int) -> Unit,
) {
    Text(
        text = SettingsTextCopy.maxItemsStatusText(maxItems),
        color = WorkloadControlTeal,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp)
    )
    Slider(
        value = maxItems.toFloat(),
        onValueChange = { progress ->
            val value = AdaptiveLoadPlanner.normalizeMaxItems(progress.roundToInt())
            model.selectedMaxItems[0] = value
            onMaxItemsChanged(value)
        },
        valueRange = AdaptiveLoadPlanner.MIN_MAX_ITEMS.toFloat()..AdaptiveLoadPlanner.MAX_MAX_ITEMS.toFloat(),
        steps = AdaptiveLoadPlanner.MAX_MAX_ITEMS - AdaptiveLoadPlanner.MIN_MAX_ITEMS - 1,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag(SettingsWorkloadTestTags.MAX_ITEMS_SLIDER)
            .semantics { contentDescription = SettingsWorkloadControlDescriptions.MAX_ITEMS_SLIDER }
    )
}

@Composable
internal fun WorkloadPrimaryButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = WorkloadControlButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = WorkloadControlPinkDark,
            contentColor = WorkloadControlWhite
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun WorkloadOutlinedButton(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 50.dp),
        shape = WorkloadControlButtonShape,
        border = BorderStroke(1.dp, WorkloadControlButtonBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = WorkloadControlWhite,
            contentColor = WorkloadControlInk
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
