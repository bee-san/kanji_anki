package dev.bee.kanjianki.core

import java.util.Locale
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
        withLocale(Locale.ENGLISH) {
            assertInvalid("oops", "3", StudyLadderThresholdPolicy.POSITIVE_WHOLE_NUMBER_ERROR)
            assertInvalid("", "3", StudyLadderThresholdPolicy.POSITIVE_WHOLE_NUMBER_ERROR)
            assertInvalid("21", "1.5", StudyLadderThresholdPolicy.POSITIVE_WHOLE_NUMBER_ERROR)
            assertEquals(
                StudyLadderThresholdPolicy.POSITIVE_WHOLE_NUMBER_ERROR,
                StudyLadderThresholdPolicy.positiveWholeNumberError(),
            )
        }
    }

    @Test
    fun saveRequestRejectsNonPositiveNumbersWithExistingCopy() {
        withLocale(Locale.ENGLISH) {
            assertInvalid("0", "3", StudyLadderThresholdPolicy.POSITIVE_WHOLE_NUMBER_ERROR)
            assertInvalid("21", "0", StudyLadderThresholdPolicy.POSITIVE_WHOLE_NUMBER_ERROR)
            assertInvalid("-1", "3", StudyLadderThresholdPolicy.POSITIVE_WHOLE_NUMBER_ERROR)
            assertInvalid("21", "-3", StudyLadderThresholdPolicy.POSITIVE_WHOLE_NUMBER_ERROR)
        }
    }

    @Test
    fun japaneseLocaleTranslatesInvalidNumberCopy() {
        withLocale(Locale.JAPANESE) {
            val message = "正の整数を入力してください。"

            assertInvalid("oops", "3", message)
            assertInvalid("0", "3", message)
            assertEquals(message, StudyLadderThresholdPolicy.positiveWholeNumberError())
        }
    }

    private fun assertInvalid(promotionDaysText: String, failStreakText: String, expectedMessage: String) {
        val result = StudyLadderThresholdPolicy.saveRequest(promotionDaysText, failStreakText)

        assertFalse(result.valid)
        assertEquals(0, result.promotionDays)
        assertEquals(0, result.failStreak)
        assertEquals(expectedMessage, result.message)
    }

    @Test
    fun saveRequestRejectsValuesAboveTheUpperBounds() {
        withLocale(Locale.ENGLISH) {
            val overDays = StudyLadderThresholdPolicy.saveRequest(
                (StudyLadderThresholdPolicy.MAX_PROMOTION_INTERVAL_DAYS + 1).toString(),
                "3",
            )
            assertFalse(overDays.valid)
            assertEquals(StudyLadderThresholdPolicy.rangeError(), overDays.message)

            val overStreak = StudyLadderThresholdPolicy.saveRequest(
                "21",
                (StudyLadderThresholdPolicy.MAX_DEMOTION_FAIL_STREAK + 1).toString(),
            )
            assertFalse(overStreak.valid)
            assertEquals(StudyLadderThresholdPolicy.rangeError(), overStreak.message)
        }
    }

    @Test
    fun saveRequestAcceptsValuesAtTheUpperBounds() {
        val result = StudyLadderThresholdPolicy.saveRequest(
            StudyLadderThresholdPolicy.MAX_PROMOTION_INTERVAL_DAYS.toString(),
            StudyLadderThresholdPolicy.MAX_DEMOTION_FAIL_STREAK.toString(),
        )
        assertTrue(result.valid)
        assertEquals(StudyLadderThresholdPolicy.MAX_PROMOTION_INTERVAL_DAYS, result.promotionDays)
        assertEquals(StudyLadderThresholdPolicy.MAX_DEMOTION_FAIL_STREAK, result.failStreak)
    }

    private inline fun withLocale(locale: Locale, block: () -> Unit) {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(original)
        }
    }
}
