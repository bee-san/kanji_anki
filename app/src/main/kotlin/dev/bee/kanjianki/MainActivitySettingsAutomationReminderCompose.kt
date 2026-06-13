@file:JvmName("MainActivitySettingsAutomationReminderCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.SettingsTextCopy

private val ReminderInk: Color @Composable get() = KaniUiTokens.Ink
private val ReminderMuted: Color @Composable get() = KaniUiTokens.Muted
private val ReminderCoral: Color @Composable get() = KaniUiTokens.Coral
private val ReminderPanelBorder: Color @Composable get() = KaniUiTokens.PanelBorder
private val ReminderWhite: Color @Composable get() = KaniUiTokens.White
private val ReminderPanelShape = KaniUiTokens.PanelShape

internal fun reminderPresetRowTestTag(rowIndex: Int): String = "settings-reminder-preset-row-$rowIndex"

@Composable
fun SettingsReminderPanel(model: SettingsReminderPanelModel) {
    var hour by remember { mutableIntStateOf(model.selectedHour[0]) }
    var minute by remember { mutableIntStateOf(model.selectedMinute[0]) }

    fun updateSelection(nextHour: Int, nextMinute: Int) {
        model.selectedHour[0] = nextHour
        model.selectedMinute[0] = nextMinute
        hour = nextHour
        minute = nextMinute
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ReminderPanelShape,
        color = ReminderWhite,
        border = BorderStroke(1.dp, ReminderPanelBorder),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = model.title,
                color = ReminderInk,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = model.status,
                color = kaniColor(model.statusColor),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = model.body,
                color = ReminderMuted,
                fontSize = 15.sp
            )
            ReminderOutlinedButton(
                label = SettingsTextCopy.reminderTimeButtonLabel(hour, minute),
                onClick = {
                    model.onPickTime.pick(hour, minute) { nextHour, nextMinute ->
                        updateSelection(nextHour, nextMinute)
                    }
                }
            )
            ReminderPresetGrid(model.presets, ::updateSelection)
            ReminderPrimaryButton(model.saveLabel) { model.onSave.run() }
            ReminderOptionalOutlinedButton(model.turnOffLabel, model.onTurnOff)
            ReminderWarning(model.warning)
            ReminderOptionalOutlinedButton(model.notificationSettingsLabel, model.onOpenNotificationSettings)
        }
    }
}

@Composable
private fun ReminderPresetGrid(
    presets: List<SettingsReminderPresetModel>,
    onSelect: (Int, Int) -> Unit,
) {
    val presetRows = remember(presets) {
        presets.chunked(2)
    }
    presetRows.forEachIndexed { rowIndex, rowPresets ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(reminderPresetRowTestTag(rowIndex)),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rowPresets.forEach { preset ->
                ReminderOutlinedButton(
                    label = SettingsTextCopy.reminderPresetButtonLabel(preset.label, preset.hour, preset.minute),
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(preset.hour, preset.minute) }
                )
            }
        }
    }
}

@Composable
private fun ReminderWarning(warning: String?) {
    if (warning == null) {
        return
    }
    Text(
        text = warning,
        color = ReminderCoral,
        fontSize = 14.sp
    )
}

@Composable
private fun ReminderPrimaryButton(label: String, onClick: () -> Unit) {
    KaniPrimaryButton(label = label, onClick = onClick)
}

@Composable
private fun ReminderOptionalOutlinedButton(label: String?, action: SettingsReminderAction?) {
    if (label == null || action == null) {
        return
    }
    ReminderOutlinedButton(label) { action.run() }
}

@Composable
private fun ReminderOutlinedButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    KaniOutlinedButton(label = label, modifier = modifier, textSizeSp = 15, onClick = onClick)
}
