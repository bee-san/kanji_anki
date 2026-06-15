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
                            moveUpLabel = "Move up",
                            moveDownLabel = "Move down",
                            canMoveUp = false,
                            canMoveDown = true,
                            toggleDescription = "Turn off Similar kanji",
                            moveUpDescription = "Move up Similar kanji",
                            moveDownDescription = "Move down Similar kanji",
                            onToggle = SettingsStudyLadderAction { toggled = true },
                            onMoveUp = SettingsStudyLadderAction { movedUp = true },
                            onMoveDown = SettingsStudyLadderAction { movedDown = true }
                        ),
                        SettingsStudyLadderRungModel(
                            label = "Word reading",
                            subtitle = "Always available",
                            toggleLabel = "Off",
                            moveUpLabel = "Move up",
                            moveDownLabel = "Move down",
                            canMoveUp = true,
                            canMoveDown = false,
                            toggleDescription = "Off Word reading",
                            moveUpDescription = "Move up Word reading",
                            moveDownDescription = "Move down Word reading",
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
        composeRule.onNodeWithContentDescription("Move down Similar kanji").assertIsEnabled().performClick()
        composeRule.onNodeWithText("On").assertHasClickAction().assertIsEnabled().performClick()
        composeRule.onNodeWithContentDescription("Move up Word reading").assertIsEnabled().performClick()
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
                            moveUpLabel = "Move up",
                            moveDownLabel = "Move down",
                            canMoveUp = false,
                            canMoveDown = true,
                            toggleDescription = "Turn off Write kanji",
                            moveUpDescription = "Move up Write kanji",
                            moveDownDescription = "Move down Write kanji",
                            onToggle = SettingsStudyLadderAction {},
                            onMoveUp = SettingsStudyLadderAction { movedFirstUp = true },
                            onMoveDown = SettingsStudyLadderAction {}
                        ),
                        SettingsStudyLadderRungModel(
                            label = "Word reading",
                            subtitle = "Last rung",
                            toggleLabel = "On",
                            moveUpLabel = "Move up",
                            moveDownLabel = "Move down",
                            canMoveUp = true,
                            canMoveDown = false,
                            toggleDescription = "Turn off Word reading",
                            moveUpDescription = "Move up Word reading",
                            moveDownDescription = "Move down Word reading",
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

        composeRule.onNodeWithContentDescription("Move up Write kanji").assertIsNotEnabled().assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        composeRule.onNodeWithContentDescription("Move down Word reading").assertIsNotEnabled().assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))

        composeRule.runOnIdle {
            assertFalse(movedFirstUp)
            assertFalse(movedLastDown)
        }
    }
}
