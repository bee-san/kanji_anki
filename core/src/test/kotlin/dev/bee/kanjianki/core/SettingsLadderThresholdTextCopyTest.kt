package dev.bee.kanjianki.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsLadderThresholdTextCopyTest {
    @Test
    fun ladderThresholdStringsStayStable() {
        assertEquals("Ladder thresholds", SettingsLadderThresholdTextCopy.ladderThresholdsTitle())
        assertEquals("Cards climb after strong due reviews; learning repeats stay practice-only.", SettingsLadderThresholdTextCopy.ladderThresholdsBody())
        assertEquals("FSRS days to go up", SettingsLadderThresholdTextCopy.fsrsDaysToGoUpLabel())
        assertEquals("Fails to go down", SettingsLadderThresholdTextCopy.failsToGoDownLabel())
        assertEquals(
            String.format(
                Locale.ROOT,
                "Use %d and %d",
                RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS,
                RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK,
            ),
            SettingsLadderThresholdTextCopy.useDefaultLadderThresholdsLabel(),
        )
        assertEquals("Save ladder thresholds", SettingsLadderThresholdTextCopy.saveLadderThresholdsLabel())
        assertEquals("Ladder thresholds saved.", SettingsLadderThresholdTextCopy.ladderThresholdsSavedToast())
    }
}
