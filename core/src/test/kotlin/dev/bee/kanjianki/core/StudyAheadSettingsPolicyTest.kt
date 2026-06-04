package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyAheadSettingsPolicyTest {
    @Test
    fun saveRequestAcceptsTrimmedBoundsAndMinutes() {
        assertValid("0", 0)
        assertValid(" 45 ", 45)
        assertValid("1440", 1440)
    }

    @Test
    fun saveRequestRejectsNonWholeNumbersWithExistingCopy() {
        assertInvalid("later", SettingsTextCopy.studyAheadWholeNumberErrorText())
        assertInvalid("", SettingsTextCopy.studyAheadWholeNumberErrorText())
        assertInvalid("1.5", SettingsTextCopy.studyAheadWholeNumberErrorText())
    }

    @Test
    fun saveRequestRejectsOutOfRangeMinutesWithExistingCopy() {
        assertInvalid("-1", SettingsTextCopy.studyAheadOutOfRangeErrorText())
        assertInvalid("1441", SettingsTextCopy.studyAheadOutOfRangeErrorText())
    }

    private fun assertValid(text: String, minutes: Int) {
        val result = StudyAheadSettingsPolicy.saveRequest(text)

        assertTrue(result.valid)
        assertEquals(minutes, result.minutes)
        assertEquals("", result.message)
    }

    private fun assertInvalid(text: String, message: String) {
        val result = StudyAheadSettingsPolicy.saveRequest(text)

        assertFalse(result.valid)
        assertEquals(0, result.minutes)
        assertEquals(message, result.message)
    }
}
