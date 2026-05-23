package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SettingsLearningTextCopyTest {
    @Test
    public void learningStepHelpersPreserveFormatting() {
        assertEquals("Learning steps", SettingsLearningTextCopy.learningStepsTitle());
        assertEquals(
                "New cards and review misses can come back quickly for practice. These repeats do not change Kani's SRS after the first answer.",
                SettingsLearningTextCopy.learningStepsBody()
        );
        assertEquals("Review misses", SettingsLearningTextCopy.reviewMissesLabel());
        assertEquals("Anki default", SettingsLearningTextCopy.ankiDefaultLabel());
        assertEquals("Both 1m 10m", SettingsLearningTextCopy.sameLearningStepsLabel());
        assertEquals("Save learning steps", SettingsLearningTextCopy.saveLearningStepsLabel());
        assertEquals("Learning steps saved.", SettingsLearningTextCopy.learningStepsSavedToast());
    }
}
