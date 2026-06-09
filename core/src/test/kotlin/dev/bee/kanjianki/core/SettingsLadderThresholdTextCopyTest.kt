package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test


class SettingsLadderThresholdTextCopyTest {
    @Test
    fun ladderThresholdStringsStayStable() {
        assertEquals("Ladder movement", SettingsLadderThresholdTextCopy.ladderThresholdsTitle())
        assertEquals("Due reviews move cards; learning repeats stay practice-only.", SettingsLadderThresholdTextCopy.ladderThresholdsBody())
        assertEquals("Review days to move up", SettingsLadderThresholdTextCopy.fsrsDaysToGoUpLabel())
        assertEquals("Misses to move down", SettingsLadderThresholdTextCopy.failsToGoDownLabel())
        assertEquals("Use default rules", SettingsLadderThresholdTextCopy.useDefaultLadderThresholdsLabel())
        assertEquals("Save movement rules", SettingsLadderThresholdTextCopy.saveLadderThresholdsLabel())
        assertEquals("Movement rules saved.", SettingsLadderThresholdTextCopy.ladderThresholdsSavedToast())
    }
}
