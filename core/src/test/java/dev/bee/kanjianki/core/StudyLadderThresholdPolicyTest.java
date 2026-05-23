package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class StudyLadderThresholdPolicyTest {
    @Test
    public void saveRequestParsesTrimmedPositiveWholeNumbers() {
        StudyLadderThresholdPolicy.SaveResult result = StudyLadderThresholdPolicy.saveRequest(" 21 ", "3");

        assertTrue(result.valid);
        assertEquals(21, result.promotionDays);
        assertEquals(3, result.failStreak);
        assertEquals("", result.message);
    }

    @Test
    public void saveRequestRejectsNonWholeNumbersWithExistingCopy() {
        assertInvalid("oops", "3");
        assertInvalid("", "3");
        assertInvalid("21", "1.5");
    }

    @Test
    public void saveRequestRejectsNonPositiveNumbersWithExistingCopy() {
        assertInvalid("0", "3");
        assertInvalid("21", "0");
        assertInvalid("-1", "3");
        assertInvalid("21", "-3");
    }

    private static void assertInvalid(String promotionDaysText, String failStreakText) {
        StudyLadderThresholdPolicy.SaveResult result = StudyLadderThresholdPolicy.saveRequest(promotionDaysText, failStreakText);

        assertFalse(result.valid);
        assertEquals(0, result.promotionDays);
        assertEquals(0, result.failStreak);
        assertEquals(StudyLadderThresholdPolicy.POSITIVE_WHOLE_NUMBER_ERROR, result.message);
    }
}
