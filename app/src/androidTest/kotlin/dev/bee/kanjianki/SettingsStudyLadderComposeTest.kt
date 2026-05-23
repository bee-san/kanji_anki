package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.bee.kanjianki.core.SettingsTextCopy
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsStudyLadderComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersRungsAndWiresActions() {
        var toggled = false
        var movedUp = false
        var movedDown = false
        var restored = false

        composeRule.setContent {
            SettingsStudyLadderPanel(
                model = SettingsStudyLadderPanelModel(
                    title = SettingsTextCopy.studyLadderTitle(),
                    body = SettingsTextCopy.studyLadderBody(),
                    rungs = listOf(
                        SettingsStudyLadderRungModel(
                            label = "Similar kanji",
                            subtitle = "Before recognition",
                            toggleLabel = "On",
                            moveUpLabel = "Up",
                            moveDownLabel = "Down",
                            canMoveUp = false,
                            canMoveDown = true,
                            toggleDescription = "Turn off Similar kanji",
                            moveUpDescription = "Up Similar kanji",
                            moveDownDescription = "Down Similar kanji",
                            onToggle = SettingsStudyLadderAction { toggled = true },
                            onMoveUp = SettingsStudyLadderAction { movedUp = true },
                            onMoveDown = SettingsStudyLadderAction { movedDown = true }
                        ),
                        SettingsStudyLadderRungModel(
                            label = "Word reading",
                            subtitle = "Always available",
                            toggleLabel = "Off",
                            moveUpLabel = "Up",
                            moveDownLabel = "Down",
                            canMoveUp = true,
                            canMoveDown = false,
                            toggleDescription = "Off Word reading",
                            moveUpDescription = "Up Word reading",
                            moveDownDescription = "Down Word reading",
                            onToggle = SettingsStudyLadderAction {},
                            onMoveUp = SettingsStudyLadderAction { movedUp = true },
                            onMoveDown = SettingsStudyLadderAction {}
                        )
                    ),
                    restoreLabel = SettingsTextCopy.restoreDefaultLadderLabel(),
                    restoreDescription = SettingsTextCopy.restoreDefaultLadderLabel(),
                    onRestore = SettingsStudyLadderAction { restored = true }
                )
            )
        }

        composeRule.onNodeWithText(SettingsTextCopy.studyLadderTitle()).assertIsDisplayed()
        composeRule.onNodeWithText("Similar kanji").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Turn off Similar kanji").performClick()
        composeRule.onNodeWithContentDescription("Down Similar kanji").performClick()
        composeRule.onNodeWithContentDescription("Up Word reading").performClick()
        composeRule.onNodeWithContentDescription(SettingsTextCopy.restoreDefaultLadderLabel()).performClick()

        composeRule.runOnIdle {
            assertTrue(toggled)
            assertTrue(movedDown)
            assertTrue(movedUp)
            assertTrue(restored)
        }
    }
}
