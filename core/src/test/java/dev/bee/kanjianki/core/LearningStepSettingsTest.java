package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class LearningStepSettingsTest {
    @Test
    public void parsesMinuteAndHourSteps() {
        List<Integer> parsed = Records.LearningStepSettings.tryParseSteps("1m, 10m 1h");

        assertEquals(Arrays.asList(1, 10, 60), parsed);
        assertEquals("1m, 10m, 1h", Records.LearningStepSettings.formatSteps(parsed));
    }

    @Test
    public void rejectsInvalidSteps() {
        assertNull(Records.LearningStepSettings.tryParseSteps(""));
        assertNull(Records.LearningStepSettings.tryParseSteps("0m, 10m"));
        assertNull(Records.LearningStepSettings.tryParseSteps("soon"));
    }

    @Test
    public void defaultsMatchAnkiStyleLearningAndRelearning() {
        Records.LearningStepSettings defaults = Records.LearningStepSettings.defaults();

        assertEquals(Arrays.asList(1, 10), defaults.newStepsMinutes);
        assertEquals(Arrays.asList(10), defaults.reviewStepsMinutes);
    }
}
