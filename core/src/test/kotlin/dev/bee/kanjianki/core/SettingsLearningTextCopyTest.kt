package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsLearningTextCopyTest {
    @Test
    fun learningStepHelpersPreserveFormatting() {
        assertEquals("Learning steps", SettingsLearningTextCopy.learningStepsTitle())
        assertEquals(
            "Set new and missed waits. Due reviews move up the ladder.",
            SettingsLearningTextCopy.learningStepsBody(),
        )
        assertEquals("Missed reviews", SettingsLearningTextCopy.reviewMissesLabel())
        assertEquals("Use Anki defaults", SettingsLearningTextCopy.ankiDefaultLabel())
        assertEquals("Copy new-card steps", SettingsLearningTextCopy.sameLearningStepsLabel())
        assertEquals("Save learning steps", SettingsLearningTextCopy.saveLearningStepsLabel())
        assertEquals("Steps saved.", SettingsLearningTextCopy.learningStepsSavedToast())
    }
}
