package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsStudyAheadTextCopyTest {
    @Test
    fun studyAheadStringsStayStable() {
        assertEquals("Study ahead", SettingsStudyAheadTextCopy.studyAheadTitle())
        assertEquals("Review soon-due cards. Learning waits stay fixed.", SettingsStudyAheadTextCopy.studyAheadBody())
        assertEquals("Save study ahead", SettingsStudyAheadTextCopy.saveStudyAheadLabel())
        assertEquals("Study ahead saved.", SettingsStudyAheadTextCopy.studyAheadSavedToast())
        assertEquals("Minutes ahead (0-1440)", SettingsStudyAheadTextCopy.studyAheadMinutesLabel())
        assertEquals("0-1440", SettingsStudyAheadTextCopy.studyAheadMinutesRange())
        assertEquals("1440 minutes (24h)", SettingsStudyAheadTextCopy.studyAheadMaxDescription())
        assertEquals("Enter whole minutes from 0-1440.", SettingsStudyAheadTextCopy.studyAheadWholeNumberErrorText())
        assertEquals(
            "Use 0-1440 minutes. 0 turns it off.",
            SettingsStudyAheadTextCopy.studyAheadOutOfRangeErrorText(),
        )
    }
}
