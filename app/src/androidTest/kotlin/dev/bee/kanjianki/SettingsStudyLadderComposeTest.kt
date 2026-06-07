package dev.bee.kanjianki

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsActions
import dev.bee.kanjianki.core.SettingsTextCopy
import org.junit.Assert.assertFalse
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
        var offToggled = false
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
                            onToggle = SettingsStudyLadderAction { offToggled = true },
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
        composeRule.onNodeWithContentDescription("Turn off Similar kanji").assertIsEnabled().performClick()
        composeRule.onNodeWithContentDescription("Down Similar kanji").assertIsEnabled().performClick()
        composeRule.onNodeWithText("On").assertHasClickAction().assertIsEnabled().performClick()
        composeRule.onNodeWithContentDescription("Up Word reading").assertIsEnabled().performClick()
        composeRule.onNodeWithText("Off").assertHasClickAction().assertIsEnabled().performClick()
        composeRule.onNodeWithText("Restore defaults").assertHasClickAction().assertIsEnabled().performClick()

        composeRule.runOnIdle {
            assertTrue(toggled)
            assertTrue(movedDown)
            assertTrue(movedUp)
            assertTrue(offToggled)
            assertTrue(restored)
        }
    }

    @Test
    fun disabledReorderControlsAreDisabledAndDoNotCallCallbacks() {
        var movedFirstUp = false
        var movedLastDown = false

        composeRule.setContent {
            SettingsStudyLadderPanel(
                model = SettingsStudyLadderPanelModel(
                    title = SettingsTextCopy.studyLadderTitle(),
                    body = SettingsTextCopy.studyLadderBody(),
                    rungs = listOf(
                        SettingsStudyLadderRungModel(
                            label = "Write kanji",
                            subtitle = "First rung",
                            toggleLabel = "On",
                            moveUpLabel = "Up",
                            moveDownLabel = "Down",
                            canMoveUp = false,
                            canMoveDown = true,
                            toggleDescription = "Turn off Write kanji",
                            moveUpDescription = "Up Write kanji",
                            moveDownDescription = "Down Write kanji",
                            onToggle = SettingsStudyLadderAction {},
                            onMoveUp = SettingsStudyLadderAction { movedFirstUp = true },
                            onMoveDown = SettingsStudyLadderAction {}
                        ),
                        SettingsStudyLadderRungModel(
                            label = "Word reading",
                            subtitle = "Last rung",
                            toggleLabel = "On",
                            moveUpLabel = "Up",
                            moveDownLabel = "Down",
                            canMoveUp = true,
                            canMoveDown = false,
                            toggleDescription = "Turn off Word reading",
                            moveUpDescription = "Up Word reading",
                            moveDownDescription = "Down Word reading",
                            onToggle = SettingsStudyLadderAction {},
                            onMoveUp = SettingsStudyLadderAction {},
                            onMoveDown = SettingsStudyLadderAction { movedLastDown = true }
                        )
                    ),
                    restoreLabel = SettingsTextCopy.restoreDefaultLadderLabel(),
                    restoreDescription = SettingsTextCopy.restoreDefaultLadderLabel(),
                    onRestore = SettingsStudyLadderAction {}
                )
            )
        }

        composeRule.onNodeWithContentDescription("Up Write kanji").assertIsNotEnabled().assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        composeRule.onNodeWithContentDescription("Down Word reading").assertIsNotEnabled().assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))

        composeRule.runOnIdle {
            assertFalse(movedFirstUp)
            assertFalse(movedLastDown)
        }
    }
}
