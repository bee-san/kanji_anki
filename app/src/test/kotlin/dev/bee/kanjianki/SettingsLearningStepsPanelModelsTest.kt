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
        var newStepsText: String? = null
        var reviewStepsText: String? = null

        val model = SettingsLearningStepsPanelModels.create(current) { newStepsTextArg, reviewStepsTextArg ->
            newStepsText = newStepsTextArg
            reviewStepsText = reviewStepsTextArg
        }

        assertEquals("Learning steps", model.title)
        assertEquals("New cards", model.newCardsLabel)
        assertEquals("2m, 15m", model.initialNewStepsText)
        assertEquals("7m", model.initialReviewStepsText)
        assertEquals("1m, 10m", model.defaultNewStepsText)
        assertEquals("10m", model.defaultReviewStepsText)

        model.onSave.save("3m", "8m")
        assertEquals("3m", newStepsText)
        assertEquals("8m", reviewStepsText)
    }
}
