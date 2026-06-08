package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SettingsStudyAheadTextCopyTest {
    @Test
    fun studyAheadStringsStayStable() {
        assertEquals("Study ahead", SettingsStudyAheadTextCopy.studyAheadTitle())
        assertEquals("Set how early due reviews appear. Learning delays still wait.", SettingsStudyAheadTextCopy.studyAheadBody())
        assertEquals("Save study ahead", SettingsStudyAheadTextCopy.saveStudyAheadLabel())
        assertEquals("Study ahead saved.", SettingsStudyAheadTextCopy.studyAheadSavedToast())
        assertEquals("Minutes early (0-1440)", SettingsStudyAheadTextCopy.studyAheadMinutesLabel())
        assertEquals("0-1440", SettingsStudyAheadTextCopy.studyAheadMinutesRange())
        assertEquals("1440 minutes (24h)", SettingsStudyAheadTextCopy.studyAheadMaxDescription())
        assertEquals("Enter whole minutes (0-1440).", SettingsStudyAheadTextCopy.studyAheadWholeNumberErrorText())
        assertEquals(
            "Use 0 to turn off. Max 1440 minutes (24h).",
            SettingsStudyAheadTextCopy.studyAheadOutOfRangeErrorText(),
        )
    }
}
