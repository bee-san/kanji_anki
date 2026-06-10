package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsStudyAheadTextCopyTest {
    @Test
    fun studyAheadStringsStayStable() {
        assertEquals("Study ahead", SettingsStudyAheadTextCopy.studyAheadTitle())
        assertEquals("Review early; learning waits stay fixed.", SettingsStudyAheadTextCopy.studyAheadBody())
        assertEquals("Save look-ahead", SettingsStudyAheadTextCopy.saveStudyAheadLabel())
        assertEquals("Look-ahead saved.", SettingsStudyAheadTextCopy.studyAheadSavedToast())
        assertEquals("Look-ahead minutes (0-1440)", SettingsStudyAheadTextCopy.studyAheadMinutesLabel())
        assertEquals("0-1440", SettingsStudyAheadTextCopy.studyAheadMinutesRange())
        assertEquals("1440 minutes (24h)", SettingsStudyAheadTextCopy.studyAheadMaxDescription())
        assertEquals("Enter whole minutes (0-1440).", SettingsStudyAheadTextCopy.studyAheadWholeNumberErrorText())
        assertEquals(
            "Enter 0-1440 minutes; 0 turns it off.",
            SettingsStudyAheadTextCopy.studyAheadOutOfRangeErrorText(),
        )
    }
}
