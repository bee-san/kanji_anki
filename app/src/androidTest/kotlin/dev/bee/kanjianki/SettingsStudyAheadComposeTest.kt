package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import dev.bee.kanjianki.core.SettingsTextCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsStudyAheadComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersStudyAheadCopyAndWiresSave() {
        var saved = false
        var savedText = ""

        composeRule.setContent {
            SettingsStudyAheadPanel(
                model = SettingsStudyAheadPanelModel(
                    title = SettingsTextCopy.studyAheadTitle(),
                    body = SettingsTextCopy.studyAheadBody(),
                    minutesLabel = SettingsTextCopy.studyAheadMinutesLabel(),
                    initialMinutesText = "30",
                    saveLabel = SettingsTextCopy.saveStudyAheadLabel(),
                    onSave = SettingsStudyAheadSaver {
                        savedText = it
                        saved = true
                    }
                )
            )
        }

        composeRule.onNodeWithText(SettingsTextCopy.studyAheadTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.studyAheadBody()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.studyAheadMinutesLabel()).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsStudyAheadTestTags.MINUTES_INPUT).performTextReplacement("45")
        composeRule.onNodeWithText(SettingsTextCopy.saveStudyAheadLabel()).performClick()

        composeRule.runOnIdle {
            assertTrue(saved)
            assertEquals("45", savedText)
        }
    }
}
