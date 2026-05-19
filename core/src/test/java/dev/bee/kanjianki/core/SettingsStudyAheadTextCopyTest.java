package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SettingsStudyAheadTextCopyTest {
    @Test
    public void studyAheadStringsStayStable() {
        assertEquals("Study ahead", SettingsStudyAheadTextCopy.studyAheadTitle());
        assertEquals("Pull cards becoming due within this many minutes into the queue. Set 0 to disable. Learning step delays still apply normally (just like Anki).", SettingsStudyAheadTextCopy.studyAheadBody());
        assertEquals("Save study ahead", SettingsStudyAheadTextCopy.saveStudyAheadLabel());
        assertEquals("Study ahead saved.", SettingsStudyAheadTextCopy.studyAheadSavedToast());
    }
}
