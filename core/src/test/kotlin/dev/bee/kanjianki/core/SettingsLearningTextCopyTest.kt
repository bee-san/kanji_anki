package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsLearningTextCopyTest {
    @Test
    fun learningStepHelpersPreserveFormatting() {
        assertEquals("Learning steps", SettingsLearningTextCopy.learningStepsTitle())
        assertEquals(
            "New/relearning cards use short steps. Repeats are practice-only.",
            SettingsLearningTextCopy.learningStepsBody(),
        )
        assertEquals("Relearning", SettingsLearningTextCopy.reviewMissesLabel())
        assertEquals("Anki default", SettingsLearningTextCopy.ankiDefaultLabel())
        assertEquals("Use new-card steps", SettingsLearningTextCopy.sameLearningStepsLabel())
        assertEquals("Save learning steps", SettingsLearningTextCopy.saveLearningStepsLabel())
        assertEquals("Steps saved.", SettingsLearningTextCopy.learningStepsSavedToast())
    }
}
