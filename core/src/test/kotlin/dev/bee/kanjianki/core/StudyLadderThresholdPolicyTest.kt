package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyLadderThresholdPolicyTest {
    @Test
    fun saveRequestParsesTrimmedPositiveWholeNumbers() {
        val result = StudyLadderThresholdPolicy.saveRequest(" 21 ", "3")

        assertTrue(result.valid)
        assertEquals(21, result.promotionDays)
        assertEquals(3, result.failStreak)
        assertEquals("", result.message)
    }

    @Test
    fun saveRequestRejectsNonWholeNumbersWithExistingCopy() {
        assertInvalid("oops", "3")
        assertInvalid("", "3")
        assertInvalid("21", "1.5")
    }

    @Test
    fun saveRequestRejectsNonPositiveNumbersWithExistingCopy() {
        assertInvalid("0", "3")
        assertInvalid("21", "0")
        assertInvalid("-1", "3")
        assertInvalid("21", "-3")
    }

    private fun assertInvalid(promotionDaysText: String, failStreakText: String) {
        val result = StudyLadderThresholdPolicy.saveRequest(promotionDaysText, failStreakText)

        assertFalse(result.valid)
        assertEquals(0, result.promotionDays)
        assertEquals(0, result.failStreak)
        assertEquals(StudyLadderThresholdPolicy.POSITIVE_WHOLE_NUMBER_ERROR, result.message)
    }
}
