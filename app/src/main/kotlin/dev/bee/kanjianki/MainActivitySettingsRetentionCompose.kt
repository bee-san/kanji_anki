@file:JvmName("MainActivitySettingsRetentionCompose")

package dev.bee.kanjianki

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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.SettingsTextCopy
import kotlin.math.roundToInt

private val RetentionInk: Color @Composable get() = KaniUiTokens.Ink
private val RetentionMuted: Color @Composable get() = KaniUiTokens.Muted
private val RetentionTeal: Color @Composable get() = KaniUiTokens.Teal
private val RetentionPinkDark: Color @Composable get() = KaniUiTokens.Primary
private val RetentionPanelBorder: Color @Composable get() = KaniUiTokens.PanelBorder
private val RetentionWhite: Color @Composable get() = KaniUiTokens.White
private val RetentionPanelShape = KaniUiTokens.PanelShape

@Composable
fun SettingsRetentionPanel(model: SettingsRetentionPanelModel) {
    var retentionPercent by rememberSaveable { mutableIntStateOf(model.selectedRetentionPercent[0]) }
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
            RetentionSlider(retentionPercent) { value ->
                model.selectedRetentionPercent[0] = value
                retentionPercent = value
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                model.presetValues.forEachIndexed { index, value ->
                    RetentionOutlinedButton(
                        label = SettingsTextCopy.retentionPresetLabel(value),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            model.selectedRetentionPercent[0] = value
                            retentionPercent = value
                        }
                    )
                    if (index < model.presetValues.lastIndex) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }
            RetentionCheckbox(
                label = model.rankRetentionLabel,
                checked = model.state.frequencyRetentionEnabled,
                onCheckedChange = {
                    model.state.frequencyRetentionEnabled = it
                }
            )
            Text(
                text = model.rankRangesBody,
                color = RetentionMuted,
                fontSize = 15.sp
            )
            OutlinedTextField(
                value = model.state.frequencyRetentionRanges,
                onValueChange = {
                    model.state.frequencyRetentionRanges = it
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = SettingsRetentionControlDescriptions.RANK_RANGES_INPUT },
                minLines = 3,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = RetentionInk,
                    fontSize = 16.sp
                )
            )
            RetentionOutlinedButton(
                label = model.exampleRangesLabel,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    model.state.frequencyRetentionRanges = model.exampleRangesText
                }
            )
            KaniPrimaryButton(
                label = model.saveLabel,
                onClick = {
                    model.onSave.save(
                        retentionPercent,
                        model.state.frequencyRetentionEnabled,
                        model.state.frequencyRetentionRanges
                    )
                }
            )
        }
    }
}

@Composable
private fun RetentionCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
            .semantics { contentDescription = SettingsRetentionControlDescriptions.RANK_RETENTION_CHECKBOX },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = RetentionPinkDark)
        )
        Text(
            text = label,
            color = RetentionInk,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RetentionSlider(retentionPercent: Int, onRetentionChanged: (Int) -> Unit) {
    Slider(
        value = retentionPercent.toFloat(),
        onValueChange = { value ->
            onRetentionChanged(value.roundToInt())
        },
        valueRange = 80f..97f,
        steps = 16,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { contentDescription = SettingsRetentionControlDescriptions.RETENTION_SLIDER }
    )
}

@Composable
private fun RetentionOutlinedButton(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    KaniOutlinedButton(label = label, modifier = modifier, onClick = onClick)
}
