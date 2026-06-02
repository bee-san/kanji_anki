package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test


class SettingsLadderThresholdTextCopyTest {
    @Test
    fun ladderThresholdStringsStayStable() {
        assertEquals("Ladder thresholds", SettingsLadderThresholdTextCopy.ladderThresholdsTitle())
        assertEquals("Only due reviews move the ladder; learning/relearning repeats are practice only.", SettingsLadderThresholdTextCopy.ladderThresholdsBody())
        assertEquals("Promotion interval days", SettingsLadderThresholdTextCopy.fsrsDaysToGoUpLabel())
        assertEquals("Demotion fail streak", SettingsLadderThresholdTextCopy.failsToGoDownLabel())
        assertEquals("Use default ladder thresholds", SettingsLadderThresholdTextCopy.useDefaultLadderThresholdsLabel())
        assertEquals("Save ladder thresholds", SettingsLadderThresholdTextCopy.saveLadderThresholdsLabel())
        assertEquals("Ladder thresholds saved.", SettingsLadderThresholdTextCopy.ladderThresholdsSavedToast())
    }
}
