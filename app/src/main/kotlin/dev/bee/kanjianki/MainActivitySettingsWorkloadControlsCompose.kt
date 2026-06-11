package dev.bee.kanjianki

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.AdaptiveLoadPlanner
import dev.bee.kanjianki.core.SettingsTextCopy
import kotlin.math.roundToInt

private val WorkloadControlTeal = KaniUiTokens.Teal

object SettingsWorkloadControlDescriptions {
    val WORKLOAD_PERCENT_SLIDER: String
        get() = SettingsTextCopy.workloadPercentSliderDescription()

    val MAX_ITEMS_SLIDER: String
        get() = SettingsTextCopy.maxItemsSliderDescription()
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
    KaniPrimaryButton(label = label, onClick = onClick)
}

@Composable
internal fun WorkloadOutlinedButton(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    KaniOutlinedButton(label = label, modifier = modifier, onClick = onClick)
}
