package dev.bee.kanjianki

import android.content.Context
import android.widget.EditText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.SettingsTextCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsLearningStepsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersLearningStepCopyAndWiresPresetsAndSave() {
        var saved = false
        val context = ApplicationProvider.getApplicationContext<Context>()
        val newSteps = EditText(context).apply { setText("2m 15m") }
        val reviewSteps = EditText(context).apply { setText("5m 20m") }
        val defaults = RecordsSchedulerModels.LearningStepSettings.defaults()

        composeRule.setContent {
            SettingsLearningStepsPanel(
                model = SettingsLearningStepsPanelModel(
                    title = SettingsTextCopy.learningStepsTitle(),
                    body = SettingsTextCopy.learningStepsBody(),
                    newCardsLabel = "New cards",
                    newStepsInput = newSteps,
                    reviewMissesLabel = SettingsTextCopy.reviewMissesLabel(),
                    reviewStepsInput = reviewSteps,
                    ankiDefaultLabel = SettingsTextCopy.ankiDefaultLabel(),
                    sameStepsLabel = SettingsTextCopy.sameLearningStepsLabel(),
                    saveLabel = SettingsTextCopy.saveLearningStepsLabel(),
                    onUseAnkiDefault = SettingsLearningStepsAction {
                        newSteps.setText(defaults.newStepsText())
                        reviewSteps.setText(defaults.reviewStepsText())
                    },
                    onUseSameSteps = SettingsLearningStepsAction {
                        newSteps.setText(defaults.newStepsText())
                        reviewSteps.setText(defaults.newStepsText())
                    },
                    onSave = SettingsLearningStepsAction { saved = true }
                )
            )
        }

        composeRule.onNodeWithText(SettingsTextCopy.learningStepsTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.learningStepsBody()).assertIsDisplayed()
        composeRule.onNodeWithText("New cards").assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.reviewMissesLabel()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.ankiDefaultLabel()).performClick()
        composeRule.runOnIdle {
            assertEquals(defaults.newStepsText(), newSteps.text.toString())
            assertEquals(defaults.reviewStepsText(), reviewSteps.text.toString())
        }
        composeRule.onNodeWithText(SettingsTextCopy.sameLearningStepsLabel()).performClick()
        composeRule.runOnIdle {
            assertEquals(defaults.newStepsText(), reviewSteps.text.toString())
        }

        composeRule.onNodeWithText(SettingsTextCopy.saveLearningStepsLabel()).performClick()

        composeRule.runOnIdle {
            assertTrue(saved)
        }
    }
}
