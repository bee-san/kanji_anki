package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsLearningTextCopyTest {
    @Test
    fun learningStepHelpersPreserveFormatting() {
        assertEquals("Learning steps", SettingsLearningTextCopy.learningStepsTitle())
        assertEquals(
            "Set waits for new cards and review misses. Repeats stay practice-only.",
            SettingsLearningTextCopy.learningStepsBody(),
        )
        assertEquals("Review misses", SettingsLearningTextCopy.reviewMissesLabel())
        assertEquals("Anki default", SettingsLearningTextCopy.ankiDefaultLabel())
        assertEquals("Use new-card steps", SettingsLearningTextCopy.sameLearningStepsLabel())
        assertEquals("Save learning steps", SettingsLearningTextCopy.saveLearningStepsLabel())
        assertEquals("Steps saved.", SettingsLearningTextCopy.learningStepsSavedToast())
    }
}
