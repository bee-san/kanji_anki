package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsStudyAheadTextCopyTest {
    @Test
    fun studyAheadStringsStayStable() {
        assertEquals("Study ahead", SettingsStudyAheadTextCopy.studyAheadTitle())
        assertEquals("Include reviews before due. Learning steps keep their waits.", SettingsStudyAheadTextCopy.studyAheadBody())
        assertEquals("Save study-ahead window", SettingsStudyAheadTextCopy.saveStudyAheadLabel())
        assertEquals("Study ahead saved.", SettingsStudyAheadTextCopy.studyAheadSavedToast())
        assertEquals("Minutes ahead (0-1440)", SettingsStudyAheadTextCopy.studyAheadMinutesLabel())
        assertEquals("0-1440", SettingsStudyAheadTextCopy.studyAheadMinutesRange())
        assertEquals("1440 minutes (24h)", SettingsStudyAheadTextCopy.studyAheadMaxDescription())
        assertEquals("Use whole minutes from 0-1440.", SettingsStudyAheadTextCopy.studyAheadWholeNumberErrorText())
        assertEquals(
            "Use 0 to turn off. Max 1440 minutes (24h).",
            SettingsStudyAheadTextCopy.studyAheadOutOfRangeErrorText(),
        )
    }
}
