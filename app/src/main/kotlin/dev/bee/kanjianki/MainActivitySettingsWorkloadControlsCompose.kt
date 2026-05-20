package dev.bee.kanjianki

import android.widget.SeekBar
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.bee.kanjianki.core.AdaptiveLoadPlanner
import dev.bee.kanjianki.core.SettingsTextCopy

private val WorkloadControlInk = Color(0xFF2D1635)
private val WorkloadControlTeal = Color(0xFF24756C)
private val WorkloadControlPinkDark = Color(0xFFDA3A7A)
private val WorkloadControlButtonBorder = Color(0xFFEEBDDA)
private val WorkloadControlWhite = Color(0xFFFFFFFF)
private val WorkloadControlButtonShape = RoundedCornerShape(12.dp)

@Composable
internal fun WorkloadSlider(
    model: SettingsWorkloadPanelModel,
    onWorkloadChanged: (Int) -> Unit,
) {
    AndroidView(
        factory = {
            model.workloadSlider.apply {
                max = 100
                progress = model.selectedWorkloadPercent[0]
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        val snapped = AdaptiveLoadPlanner.snapWorkloadPercent(progress)
                        model.selectedWorkloadPercent[0] = snapped
                        onWorkloadChanged(snapped)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        seekBar.progress = model.selectedWorkloadPercent[0]
                    }
                })
            }
        },
        update = { slider ->
            if (slider.progress != model.selectedWorkloadPercent[0]) {
                slider.progress = model.selectedWorkloadPercent[0]
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
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
    AndroidView(
        factory = {
            model.maxItemsSlider.apply {
                max = AdaptiveLoadPlanner.MAX_MAX_ITEMS - AdaptiveLoadPlanner.MIN_MAX_ITEMS
                progress = maxItems - AdaptiveLoadPlanner.MIN_MAX_ITEMS
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        val value = AdaptiveLoadPlanner.normalizeMaxItems(progress + AdaptiveLoadPlanner.MIN_MAX_ITEMS)
                        model.selectedMaxItems[0] = value
                        onMaxItemsChanged(value)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        seekBar.progress = model.selectedMaxItems[0] - AdaptiveLoadPlanner.MIN_MAX_ITEMS
                    }
                })
            }
        },
        update = { slider ->
            val progress = model.selectedMaxItems[0] - AdaptiveLoadPlanner.MIN_MAX_ITEMS
            if (slider.progress != progress) {
                slider.progress = progress
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
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
