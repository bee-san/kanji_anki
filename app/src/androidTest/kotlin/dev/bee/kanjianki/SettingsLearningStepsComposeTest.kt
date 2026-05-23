package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
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
        var savedNewSteps = ""
        var savedReviewSteps = ""
        val defaults = RecordsSchedulerModels.LearningStepSettings.defaults()

        composeRule.setContent {
            SettingsLearningStepsPanel(
                model = SettingsLearningStepsPanelModel(
                    title = SettingsTextCopy.learningStepsTitle(),
                    body = SettingsTextCopy.learningStepsBody(),
                    newCardsLabel = MainActivityBase.LABEL_NEW_CARDS,
                    initialNewStepsText = "2m 15m",
                    reviewMissesLabel = SettingsTextCopy.reviewMissesLabel(),
                    initialReviewStepsText = "5m 20m",
                    defaultNewStepsText = defaults.newStepsText(),
                    defaultReviewStepsText = defaults.reviewStepsText(),
                    ankiDefaultLabel = SettingsTextCopy.ankiDefaultLabel(),
                    sameStepsLabel = SettingsTextCopy.sameLearningStepsLabel(),
                    saveLabel = SettingsTextCopy.saveLearningStepsLabel(),
                    onSave = SettingsLearningStepsSaveAction { newStepsText, reviewStepsText ->
                        savedNewSteps = newStepsText
                        savedReviewSteps = reviewStepsText
                        saved = true
                    }
                )
            )
        }

        composeRule.onNodeWithText(SettingsTextCopy.learningStepsTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.learningStepsBody()).assertIsDisplayed()
        composeRule.onNodeWithText(MainActivityBase.LABEL_NEW_CARDS).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.reviewMissesLabel()).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsLearningStepsTestTags.NEW_STEPS_INPUT).performTextReplacement("3m 15m")
        composeRule.onNodeWithTag(SettingsLearningStepsTestTags.REVIEW_STEPS_INPUT).performTextReplacement("6m 30m")
        composeRule.onNodeWithText(SettingsTextCopy.ankiDefaultLabel()).performClick()
        composeRule.onNodeWithTag(SettingsLearningStepsTestTags.NEW_STEPS_INPUT)
            .assertTextEquals(defaults.newStepsText())
        composeRule.onNodeWithTag(SettingsLearningStepsTestTags.REVIEW_STEPS_INPUT)
            .assertTextEquals(defaults.reviewStepsText())
        composeRule.onNodeWithText(SettingsTextCopy.sameLearningStepsLabel()).performClick()
        composeRule.onNodeWithTag(SettingsLearningStepsTestTags.NEW_STEPS_INPUT)
            .assertTextEquals(defaults.newStepsText())
        composeRule.onNodeWithTag(SettingsLearningStepsTestTags.REVIEW_STEPS_INPUT)
            .assertTextEquals(defaults.newStepsText())

        composeRule.onNodeWithText(SettingsTextCopy.saveLearningStepsLabel()).performClick()

        composeRule.runOnIdle {
            assertTrue(saved)
            assertEquals(defaults.newStepsText(), savedNewSteps)
            assertEquals(defaults.newStepsText(), savedReviewSteps)
        }
    }
}
