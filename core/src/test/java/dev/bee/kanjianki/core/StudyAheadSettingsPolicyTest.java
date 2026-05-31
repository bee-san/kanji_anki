package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class StudyAheadSettingsPolicyTest {
    @Test
    public void saveRequestAcceptsTrimmedBoundsAndMinutes() {
        assertValid("0", 0);
        assertValid(" 45 ", 45);
        assertValid("1440", 1440);
    }

    @Test
    public void saveRequestRejectsNonWholeNumbersWithExistingCopy() {
        assertInvalid("later", SettingsTextCopy.studyAheadWholeNumberErrorText());
        assertInvalid("", SettingsTextCopy.studyAheadWholeNumberErrorText());
        assertInvalid("1.5", SettingsTextCopy.studyAheadWholeNumberErrorText());
    }

    @Test
    public void saveRequestRejectsOutOfRangeMinutesWithExistingCopy() {
        assertInvalid("-1", SettingsTextCopy.studyAheadOutOfRangeErrorText());
        assertInvalid("1441", SettingsTextCopy.studyAheadOutOfRangeErrorText());
    }

    private static void assertValid(String text, int minutes) {
        StudyAheadSettingsPolicy.SaveResult result = StudyAheadSettingsPolicy.saveRequest(text);

        assertTrue(result.valid);
        assertEquals(minutes, result.minutes);
        assertEquals("", result.message);
    }

    private static void assertInvalid(String text, String message) {
        StudyAheadSettingsPolicy.SaveResult result = StudyAheadSettingsPolicy.saveRequest(text);

        assertFalse(result.valid);
        assertEquals(0, result.minutes);
        assertEquals(message, result.message);
    }
}
