package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;

public final class SettingsLadderThresholdTextCopyTest {
    @Test
    public void ladderThresholdStringsStayStable() {
        assertEquals("Ladder thresholds", SettingsLadderThresholdTextCopy.ladderThresholdsTitle());
        assertEquals("Recognition rungs climb when a real FSRS-due pass schedules the next review beyond the day threshold. Learning-step repeats stay practice-only.", SettingsLadderThresholdTextCopy.ladderThresholdsBody());
        assertEquals("FSRS days to go up", SettingsLadderThresholdTextCopy.fsrsDaysToGoUpLabel());
        assertEquals("Fails to go down", SettingsLadderThresholdTextCopy.failsToGoDownLabel());
        assertEquals(
                String.format(
                        Locale.ROOT,
                        "Use %d and %d",
                        RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS,
                        RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK
                ),
                SettingsLadderThresholdTextCopy.useDefaultLadderThresholdsLabel()
        );
        assertEquals("Save ladder thresholds", SettingsLadderThresholdTextCopy.saveLadderThresholdsLabel());
        assertEquals("Ladder thresholds saved.", SettingsLadderThresholdTextCopy.ladderThresholdsSavedToast());
    }
}
