package dev.bee.kanjianki

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.bee.kanjianki.core.SettingsTextCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsReminderComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersPresetsAndSavesSelectedTime() {
        var saved = false
        val selectedHour = intArrayOf(21)
        val selectedMinute = intArrayOf(0)

        composeRule.setContent {
            SettingsReminderPanel(
                reminderModel(
                    selectedHour = selectedHour,
                    selectedMinute = selectedMinute,
                    onSave = { saved = true }
                )
            )
        }

        composeRule.onNodeWithText(SettingsTextCopy.dailyReminderTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.reminderTimeButtonLabel(21, 0)).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.reminderPresetButtonLabel("Morning", 8, 0)).performClick()
        composeRule.onNodeWithText(SettingsTextCopy.reminderTimeButtonLabel(8, 0)).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.enableReminderLabel()).performClick()

        composeRule.runOnIdle {
            assertEquals(8, selectedHour[0])
            assertEquals(0, selectedMinute[0])
            assertTrue(saved)
        }
    }

    @Test
    fun updatesSelectedTimeFromPickerCallback() {
        var pickerOpened = false
        val selectedHour = intArrayOf(21)
        val selectedMinute = intArrayOf(0)

        composeRule.setContent {
            SettingsReminderPanel(
                reminderModel(
                    selectedHour = selectedHour,
                    selectedMinute = selectedMinute,
                    onPickTime = { hour, minute, onSelected ->
                        assertEquals(21, hour)
                        assertEquals(0, minute)
                        pickerOpened = true
                        onSelected.select(6, 5)
                    }
                )
            )
        }

        composeRule.onNodeWithText(SettingsTextCopy.reminderTimeButtonLabel(21, 0)).performClick()
        composeRule.onNodeWithText(SettingsTextCopy.reminderTimeButtonLabel(6, 5)).assertIsDisplayed()

        composeRule.runOnIdle {
            assertTrue(pickerOpened)
            assertEquals(6, selectedHour[0])
            assertEquals(5, selectedMinute[0])
        }
    }

    @Test
    fun rendersWarningAndOptionalActions() {
        var turnedOff = false
        var openedSettings = false

        composeRule.setContent {
            SettingsReminderPanel(
                reminderModel(
                    status = SettingsTextCopy.reminderStatus(true, true, "21:00"),
                    statusColor = MainActivityUiSupport.CORAL,
                    saveLabel = SettingsTextCopy.saveReminderLabel(),
                    turnOffLabel = SettingsTextCopy.turnOffReminderLabel(),
                    warning = SettingsTextCopy.notificationsBlockedBody(),
                    notificationSettingsLabel = SettingsTextCopy.openNotificationSettingsLabel(),
                    onTurnOff = { turnedOff = true },
                    onOpenNotificationSettings = { openedSettings = true }
                )
            )
        }

        composeRule.onNodeWithText(SettingsTextCopy.notificationsBlockedBody()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.turnOffReminderLabel()).performClick()
        composeRule.onNodeWithText(SettingsTextCopy.openNotificationSettingsLabel()).performClick()

        composeRule.runOnIdle {
            assertTrue(turnedOff)
            assertTrue(openedSettings)
        }
    }

    @Test
    fun hidesTurnOffActionWhenReminderIsDisabled() {
        composeRule.setContent {
            SettingsReminderPanel(reminderModel())
        }

        composeRule.onAllNodesWithText(SettingsTextCopy.turnOffReminderLabel()).assertCountEquals(0)
    }

    private fun reminderModel(
        status: String = SettingsTextCopy.reminderStatus(false, false, "21:00"),
        statusColor: Int = MainActivityUiSupport.MUTED,
        selectedHour: IntArray = intArrayOf(21),
        selectedMinute: IntArray = intArrayOf(0),
        saveLabel: String = SettingsTextCopy.enableReminderLabel(),
        turnOffLabel: String? = null,
        warning: String? = null,
        notificationSettingsLabel: String? = null,
        onPickTime: (
            hour: Int,
            minute: Int,
            onSelected: SettingsReminderSelectedTimeAction,
        ) -> Unit = { _, _, _ -> },
        onSave: () -> Unit = {},
        onTurnOff: (() -> Unit)? = null,
        onOpenNotificationSettings: (() -> Unit)? = null,
    ): SettingsReminderPanelModel {
        return SettingsReminderPanelModel(
            title = SettingsTextCopy.dailyReminderTitle(),
            status = status,
            statusColor = statusColor,
            body = SettingsTextCopy.dailyReminderBody(),
            selectedHour = selectedHour,
            selectedMinute = selectedMinute,
            presets = listOf(
                SettingsReminderPresetModel("Morning", 8, 0),
                SettingsReminderPresetModel("Lunch", 12, 30),
                SettingsReminderPresetModel("Evening", 19, 0),
                SettingsReminderPresetModel("Night", 21, 0)
            ),
            saveLabel = saveLabel,
            turnOffLabel = turnOffLabel,
            warning = warning,
            notificationSettingsLabel = notificationSettingsLabel,
            onPickTime = SettingsReminderTimePickerAction { hour, minute, onSelected ->
                onPickTime(hour, minute, onSelected)
            },
            onSave = SettingsReminderAction { onSave() },
            onTurnOff = onTurnOff?.let { SettingsReminderAction { it() } },
            onOpenNotificationSettings = onOpenNotificationSettings?.let { SettingsReminderAction { it() } }
        )
    }
}
