package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsLearningTextCopyTest {
    @Test
    fun learningStepHelpersPreserveFormatting() {
        assertEquals("Learning steps", SettingsLearningTextCopy.learningStepsTitle())
        assertEquals(
            "New cards and review misses can come back fast. First-answer repeats stay in practice.",
            SettingsLearningTextCopy.learningStepsBody(),
        )
        assertEquals("Review misses", SettingsLearningTextCopy.reviewMissesLabel())
        assertEquals("Anki default", SettingsLearningTextCopy.ankiDefaultLabel())
        assertEquals("Use new-card steps for both", SettingsLearningTextCopy.sameLearningStepsLabel())
        assertEquals("Save learning steps", SettingsLearningTextCopy.saveLearningStepsLabel())
        assertEquals("Learning steps saved.", SettingsLearningTextCopy.learningStepsSavedToast())
    }
}
