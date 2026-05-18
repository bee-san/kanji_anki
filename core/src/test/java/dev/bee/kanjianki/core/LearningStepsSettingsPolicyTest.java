package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class LearningStepsSettingsPolicyTest {
    @Test
    public void saveRequestBuildsLearningStepSettingsFromBothInputs() {
        LearningStepsSettingsPolicy.SaveResult result = LearningStepsSettingsPolicy.saveRequest("1m, 10m", "10m 1h");

        assertTrue(result.valid);
        assertEquals(Arrays.asList(1, 10), result.settings.newStepsMinutes);
        assertEquals(Arrays.asList(10, 60), result.settings.reviewStepsMinutes);
        assertEquals("", result.message);
    }

    @Test
    public void saveRequestRejectsInvalidNewStepsWithExistingCopy() {
        assertInvalid("", "10m");
        assertInvalid("soon", "10m");
        assertInvalid("0m", "10m");
    }

    @Test
    public void saveRequestRejectsInvalidReviewStepsWithExistingCopy() {
        assertInvalid("1m, 10m", "");
        assertInvalid("1m, 10m", "soon");
        assertInvalid("1m, 10m", "0m");
    }

    private static void assertInvalid(String newStepsText, String reviewStepsText) {
        LearningStepsSettingsPolicy.SaveResult result = LearningStepsSettingsPolicy.saveRequest(newStepsText, reviewStepsText);

        assertFalse(result.valid);
        assertNull(result.settings);
        assertEquals(LearningStepsSettingsPolicy.STEP_FORMAT_ERROR, result.message);
    }
}
