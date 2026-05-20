package dev.bee.kanjianki

import android.content.Context
import android.widget.EditText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.SettingsTextCopy
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsStudyAheadComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersStudyAheadCopyAndWiresSave() {
        var saved = false
        val input = EditText(ApplicationProvider.getApplicationContext<Context>()).apply {
            setText("45")
        }

        composeRule.setContent {
            SettingsStudyAheadPanel(
                model = SettingsStudyAheadPanelModel(
                    title = SettingsTextCopy.studyAheadTitle(),
                    body = SettingsTextCopy.studyAheadBody(),
                    minutesLabel = SettingsTextCopy.studyAheadMinutesLabel(),
                    minutesInput = input,
                    saveLabel = SettingsTextCopy.saveStudyAheadLabel(),
                    onSave = SettingsStudyAheadSaver { saved = true }
                )
            )
        }

        composeRule.onNodeWithText(SettingsTextCopy.studyAheadTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.studyAheadBody()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.studyAheadMinutesLabel()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.saveStudyAheadLabel()).performClick()

        composeRule.runOnIdle {
            assertTrue(saved)
        }
    }
}
