package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsSchedulerModels
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsLearningStepsPanelModelsTest {
    @Test
    fun createBuildsLearningStepsPanelModelFromCurrentSettings() {
        val current = RecordsSchedulerModels.LearningStepSettings(
            listOf(2, 15),
            listOf(7),
        )
        val action = RecordingSaveAction()

        val model = SettingsLearningStepsPanelModels.create(current, action)

        assertEquals("Learning steps", model.title)
        assertEquals("New cards", model.newCardsLabel)
        assertEquals("2m, 15m", model.initialNewStepsText)
        assertEquals("7m", model.initialReviewStepsText)
        assertEquals("1m, 10m", model.defaultNewStepsText)
        assertEquals("10m", model.defaultReviewStepsText)

        model.onSave.save("3m", "8m")
        assertEquals("3m", action.newStepsText)
        assertEquals("8m", action.reviewStepsText)
    }

    private class RecordingSaveAction : SettingsLearningStepsSaveAction {
        var newStepsText: String? = null
        var reviewStepsText: String? = null

        override fun save(newStepsText: String, reviewStepsText: String) {
            this.newStepsText = newStepsText
            this.reviewStepsText = reviewStepsText
        }
    }
}
