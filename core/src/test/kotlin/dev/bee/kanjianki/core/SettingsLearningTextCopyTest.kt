package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsLearningTextCopyTest {
    @Test
    fun learningStepHelpersPreserveFormatting() {
        assertEquals("Learning steps", SettingsLearningTextCopy.learningStepsTitle())
        assertEquals(
            "Set waits for new and missed cards. Due reviews move the ladder.",
            SettingsLearningTextCopy.learningStepsBody(),
        )
        assertEquals("Missed reviews", SettingsLearningTextCopy.reviewMissesLabel())
        assertEquals("Use Anki defaults", SettingsLearningTextCopy.ankiDefaultLabel())
        assertEquals("Copy new-card steps", SettingsLearningTextCopy.sameLearningStepsLabel())
        assertEquals("Save learning steps", SettingsLearningTextCopy.saveLearningStepsLabel())
        assertEquals("Steps saved.", SettingsLearningTextCopy.learningStepsSavedToast())
    }
}
