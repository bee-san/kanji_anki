@file:JvmName("MainActivitySettingsRetentionCompose")

package dev.bee.kanjianki

import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.SeekBar
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.viewinterop.AndroidView
import dev.bee.kanjianki.core.SettingsTextCopy

private val RetentionInk = Color(0xFF2D1635)
private val RetentionMuted = Color(0xFF6C5674)
private val RetentionTeal = Color(0xFF24756C)
private val RetentionPinkDark = Color(0xFFDA3A7A)
private val RetentionPanelBorder = Color(0xFFFFC7DE)
private val RetentionButtonBorder = Color(0xFFEEBDDA)
private val RetentionWhite = Color(0xFFFFFFFF)
private val RetentionPanelShape = RoundedCornerShape(24.dp)
private val RetentionButtonShape = RoundedCornerShape(12.dp)

fun interface SettingsRetentionAction {
    fun run()
}

data class SettingsRetentionPanelModel(
    val title: String,
    val body: String,
    val selectedRetentionPercent: IntArray,
    val retentionSlider: SeekBar,
    val presetValues: IntArray,
    val rankRetentionEnabled: CheckBox,
    val rankRangesBody: String,
    val rankRangesInput: EditText,
    val exampleRangesLabel: String,
    val saveLabel: String,
    val onUseExampleRanges: SettingsRetentionAction,
    val onSave: SettingsRetentionAction,
)

internal fun retentionSettingsPanelView(
    activity: MainActivitySettings,
    model: SettingsRetentionPanelModel,
): View {
    return ComposeView(activity).apply {
        layoutParams = settingsPanelLayoutParams(activity)
        setContent {
            MaterialTheme {
                SettingsRetentionPanel(model)
            }
        }
    }
}

@Composable
fun SettingsRetentionPanel(model: SettingsRetentionPanelModel) {
    var retentionPercent by remember { mutableIntStateOf(model.selectedRetentionPercent[0]) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RetentionPanelShape,
        color = RetentionWhite,
        border = BorderStroke(1.dp, RetentionPanelBorder),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = model.title,
                color = RetentionInk,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = SettingsTextCopy.retentionStatusText(retentionPercent),
                color = RetentionTeal,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = model.body,
                color = RetentionMuted,
                fontSize = 15.sp
            )
            RetentionSlider(model) { value ->
                retentionPercent = value
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                model.presetValues.forEachIndexed { index, value ->
                    RetentionOutlinedButton(
                        label = SettingsTextCopy.retentionPresetLabel(value),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            model.selectedRetentionPercent[0] = value
                            model.retentionSlider.progress = value - 80
                            retentionPercent = value
                        }
                    )
                    if (index < model.presetValues.lastIndex) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }
            AndroidView(
                factory = { model.rankRetentionEnabled },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = model.rankRangesBody,
                color = RetentionMuted,
                fontSize = 15.sp
            )
            AndroidView(
                factory = { model.rankRangesInput },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
            )
            RetentionOutlinedButton(
                label = model.exampleRangesLabel,
                modifier = Modifier.fillMaxWidth(),
                onClick = { model.onUseExampleRanges.run() }
            )
            Button(
                onClick = { model.onSave.run() },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = RetentionButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RetentionPinkDark,
                    contentColor = RetentionWhite
                )
            ) {
                Text(
                    text = model.saveLabel,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RetentionSlider(model: SettingsRetentionPanelModel, onRetentionChanged: (Int) -> Unit) {
    AndroidView(
        factory = {
            model.retentionSlider.apply {
                max = 17
                progress = model.selectedRetentionPercent[0] - 80
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        val value = 80 + progress
                        model.selectedRetentionPercent[0] = value
                        onRetentionChanged(value)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

                    override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
                })
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    )
}

@Composable
private fun RetentionOutlinedButton(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 50.dp),
        shape = RetentionButtonShape,
        border = BorderStroke(1.dp, RetentionButtonBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = RetentionWhite,
            contentColor = RetentionInk
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
