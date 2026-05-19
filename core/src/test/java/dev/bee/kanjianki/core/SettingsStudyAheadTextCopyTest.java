package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;

public final class SettingsStudyAheadTextCopyTest {
    @Test
    public void studyAheadStringsStayStable() {
        assertEquals("Study ahead", SettingsStudyAheadTextCopy.studyAheadTitle());
        assertEquals("Pull cards becoming due within this many minutes into the queue. Set 0 to disable. Learning step delays still apply normally (just like Anki).", SettingsStudyAheadTextCopy.studyAheadBody());
        assertEquals("Save study ahead", SettingsStudyAheadTextCopy.saveStudyAheadLabel());
        assertEquals("Study ahead saved.", SettingsStudyAheadTextCopy.studyAheadSavedToast());
        assertEquals("Minutes (0-1440)", SettingsStudyAheadTextCopy.studyAheadMinutesLabel());
        assertEquals("0-1440", SettingsStudyAheadTextCopy.studyAheadMinutesRange());
        assertEquals("1440 minutes (24h)", SettingsStudyAheadTextCopy.studyAheadMaxDescription());
        assertEquals("Use a whole number of minutes (0-1440).", SettingsStudyAheadTextCopy.studyAheadWholeNumberErrorText());
        assertEquals(
                String.format(Locale.ROOT, "Use %d to disable, or up to 1440 minutes (24h).", SettingsInputRules.DEFAULT_STUDY_AHEAD_MINUTES),
                SettingsStudyAheadTextCopy.studyAheadOutOfRangeErrorText()
        );
    }
}
