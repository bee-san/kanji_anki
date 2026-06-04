package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Arrays
import java.util.Calendar
import java.util.TimeZone

class StudyStreakPolicyTest {
    @Test
    fun emptyEvidenceProducesZeroStreak() {
        val streak = StudyStreakPolicy.summarize(emptyList(), utcDay(15), 4, 123L)

        assertEquals(0, streak.currentDays)
        assertEquals(0, streak.bestDays)
        assertFalse(streak.studiedToday)
        assertEquals(0, streak.reviewsToday)
        assertEquals(0L, streak.lastStudyAtMillis)
    }

    @Test
    fun currentStreakCountsTodayBackThroughConsecutiveDays() {
        withUtcZone {
            val today = utcDay(15)
            val streak = StudyStreakPolicy.summarize(
                Arrays.asList(today, utcDay(14), utcDay(13), utcDay(10)),
                today,
                3,
                today + 180_000L,
            )

            assertEquals(3, streak.currentDays)
            assertEquals(3, streak.bestDays)
            assertTrue(streak.studiedToday)
            assertEquals(3, streak.reviewsToday)
            assertEquals(today + 180_000L, streak.lastStudyAtMillis)
        }
    }

    @Test
    fun currentStreakContinuesThroughYesterdayWhenTodayIsMissing() {
        withUtcZone {
            val today = utcDay(15)
            val streak = StudyStreakPolicy.summarize(
                Arrays.asList(utcDay(14), utcDay(13), utcDay(11), utcDay(10)),
                today,
                0,
                utcDay(14) + 60_000L,
            )

            assertEquals(2, streak.currentDays)
            assertEquals(2, streak.bestDays)
            assertFalse(streak.studiedToday)
            assertEquals(0, streak.reviewsToday)
        }
    }

    @Test
    fun missedYesterdayBreaksCurrentButKeepsBestHistoricalRun() {
        withUtcZone {
            val today = utcDay(15)
            val streak = StudyStreakPolicy.summarize(
                Arrays.asList(utcDay(13), utcDay(12), utcDay(11), utcDay(8)),
                today,
                0,
                utcDay(13) + 60_000L,
            )

            assertEquals(0, streak.currentDays)
            assertEquals(3, streak.bestDays)
            assertFalse(streak.studiedToday)
        }
    }

    @Test
    fun nullEvidenceIsTreatedAsEmpty() {
        val streak = StudyStreakPolicy.summarize(null, utcDay(15), 1, 50L)

        assertEquals(0, streak.currentDays)
        assertEquals(0, streak.bestDays)
        assertFalse(streak.studiedToday)
    }

    private fun withUtcZone(body: () -> Unit) {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            body()
        } finally {
            TimeZone.setDefault(original)
        }
    }

    private fun utcDay(day: Int): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.set(2026, Calendar.MAY, day, 0, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
