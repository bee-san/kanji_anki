package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SettingsStudyAheadTextCopyTest {
    @Test
    fun studyAheadStringsStayStable() {
        assertEquals("Study ahead", SettingsStudyAheadTextCopy.studyAheadTitle())
        assertEquals("Show due reviews early. Learning delays still apply. 0 turns it off.", SettingsStudyAheadTextCopy.studyAheadBody())
        assertEquals("Save study ahead", SettingsStudyAheadTextCopy.saveStudyAheadLabel())
        assertEquals("Study ahead saved.", SettingsStudyAheadTextCopy.studyAheadSavedToast())
        assertEquals("Minutes (0-1440)", SettingsStudyAheadTextCopy.studyAheadMinutesLabel())
        assertEquals("0-1440", SettingsStudyAheadTextCopy.studyAheadMinutesRange())
        assertEquals("1440 minutes (24h)", SettingsStudyAheadTextCopy.studyAheadMaxDescription())
        assertEquals("Use whole minutes (0-1440).", SettingsStudyAheadTextCopy.studyAheadWholeNumberErrorText())
        assertEquals(
            "0 turns it off. Max 1440 minutes (24h).",
            SettingsStudyAheadTextCopy.studyAheadOutOfRangeErrorText(),
        )
    }
}
