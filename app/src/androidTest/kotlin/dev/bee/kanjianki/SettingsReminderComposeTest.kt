package dev.bee.kanjianki

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
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
    fun selectedTimeSurvivesStateRestorationAndSyncsToFreshModel() {
        var selectedHour = intArrayOf(21)
        var selectedMinute = intArrayOf(0)
        var model = reminderModel(selectedHour = selectedHour, selectedMinute = selectedMinute)
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { SettingsReminderPanel(model) }

        composeRule.onNodeWithText(SettingsTextCopy.reminderPresetButtonLabel("Morning", 8, 0)).performClick()

        selectedHour = intArrayOf(21)
        selectedMinute = intArrayOf(0)
        model = reminderModel(selectedHour = selectedHour, selectedMinute = selectedMinute)
        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText(SettingsTextCopy.reminderTimeButtonLabel(8, 0)).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(8, selectedHour[0])
            assertEquals(0, selectedMinute[0])
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

    @Test
    fun rendersPresetGridInTwoColumns() {
        composeRule.setContent {
            SettingsReminderPanel(reminderModel())
        }

        composeRule.onNodeWithTag(reminderPresetRowTestTag(0), useUnmergedTree = true)
            .onChildren()
            .assertCountEquals(2)
        composeRule.onNodeWithTag(reminderPresetRowTestTag(1), useUnmergedTree = true)
            .onChildren()
            .assertCountEquals(2)
    }

    @Test
    fun antiSpamControlsRenderAndFireCallbacks() {
        var increased = false
        var decreased = false
        var pickedStart = false
        var pickedEnd = false

        composeRule.setContent {
            SettingsReminderPanel(
                reminderModel(
                    antiSpam = SettingsReminderAntiSpamModel(
                        quietHoursLabel = SettingsTextCopy.reminderQuietHoursLabel(22 * 60, 8 * 60),
                        quietHoursBody = SettingsTextCopy.reminderQuietHoursBody(),
                        quietStartLabel = SettingsTextCopy.reminderQuietStartButtonLabel(22 * 60),
                        quietEndLabel = SettingsTextCopy.reminderQuietEndButtonLabel(8 * 60),
                        maxPerDayLabel = SettingsTextCopy.reminderMaxPerDayLabel(2),
                        onPickQuietStart = SettingsReminderAction { pickedStart = true },
                        onPickQuietEnd = SettingsReminderAction { pickedEnd = true },
                        onDecreaseMaxPerDay = SettingsReminderAction { decreased = true },
                        onIncreaseMaxPerDay = SettingsReminderAction { increased = true },
                    ),
                ),
            )
        }

        composeRule.onNodeWithText(SettingsTextCopy.reminderMaxPerDayLabel(2)).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.reminderQuietStartButtonLabel(22 * 60)).performClick()
        composeRule.onNodeWithText(SettingsTextCopy.reminderQuietEndButtonLabel(8 * 60)).performClick()
        composeRule.onNodeWithText("+").performClick()
        composeRule.onNodeWithText("−").performClick()

        composeRule.runOnIdle {
            assertTrue(pickedStart)
            assertTrue(pickedEnd)
            assertTrue(increased)
            assertTrue(decreased)
        }
    }

    @Test
    fun antiSpamControlsHiddenWhenAbsent() {
        composeRule.setContent {
            SettingsReminderPanel(reminderModel())
        }

        composeRule.onAllNodesWithText(SettingsTextCopy.reminderMaxPerDayLabel(2)).assertCountEquals(0)
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
        antiSpam: SettingsReminderAntiSpamModel? = null,
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
            onOpenNotificationSettings = onOpenNotificationSettings?.let { SettingsReminderAction { it() } },
            antiSpam = antiSpam,
        )
    }
}
