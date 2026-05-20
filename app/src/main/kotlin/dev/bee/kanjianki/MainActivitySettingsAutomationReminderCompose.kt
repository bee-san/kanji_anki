@file:JvmName("MainActivitySettingsAutomationReminderCompose")

package dev.bee.kanjianki

import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.SettingsTextCopy

private val ReminderInk = Color(0xFF2D1635)
private val ReminderMuted = Color(0xFF6C5674)
private val ReminderPinkDark = Color(0xFFDA3A7A)
private val ReminderCoral = Color(MainActivityUiSupport.CORAL)
private val ReminderPanelBorder = Color(0xFFFFC7DE)
private val ReminderButtonBorder = Color(0xFFEEBDDA)
private val ReminderWhite = Color(0xFFFFFFFF)
private val ReminderPanelShape = RoundedCornerShape(24.dp)
private val ReminderButtonShape = RoundedCornerShape(12.dp)

fun interface SettingsReminderAction {
    fun run()
}

fun interface SettingsReminderSelectedTimeAction {
    fun select(hour: Int, minute: Int)
}

fun interface SettingsReminderTimePickerAction {
    fun pick(hour: Int, minute: Int, onSelected: SettingsReminderSelectedTimeAction)
}

data class SettingsReminderPresetModel(
    val label: String,
    val hour: Int,
    val minute: Int,
)

data class SettingsReminderPanelModel(
    val title: String,
    val status: String,
    val statusColor: Int,
    val body: String,
    val selectedHour: IntArray,
    val selectedMinute: IntArray,
    val presets: List<SettingsReminderPresetModel>,
    val saveLabel: String,
    val turnOffLabel: String?,
    val warning: String?,
    val notificationSettingsLabel: String?,
    val onPickTime: SettingsReminderTimePickerAction,
    val onSave: SettingsReminderAction,
    val onTurnOff: SettingsReminderAction?,
    val onOpenNotificationSettings: SettingsReminderAction?,
)

internal fun reminderPresetRowTestTag(rowIndex: Int): String = "settings-reminder-preset-row-$rowIndex"

internal fun reminderSettingsPanelView(
    activity: MainActivitySettings,
    model: SettingsReminderPanelModel,
): View {
    return ComposeView(activity).apply {
        layoutParams = settingsPanelLayoutParams(activity)
        setContent {
            MaterialTheme {
                SettingsReminderPanel(model)
            }
        }
    }
}

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
                color = Color(model.statusColor),
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
    presets.chunked(2).forEachIndexed { rowIndex, rowPresets ->
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
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = ReminderButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = ReminderPinkDark,
            contentColor = ReminderWhite
        )
    ) {
        ReminderButtonText(label, 17)
    }
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
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp),
        shape = ReminderButtonShape,
        border = BorderStroke(1.dp, ReminderButtonBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = ReminderWhite,
            contentColor = ReminderInk
        )
    ) {
        ReminderButtonText(label, 15)
    }
}

@Composable
private fun ReminderButtonText(label: String, sizeSp: Int) {
    Text(
        text = label,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        fontSize = sizeSp.sp,
        fontWeight = FontWeight.Bold
    )
}
