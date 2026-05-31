package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class StudyTaskTimingPolicyTest {
    @Test
    fun windowUsesLocalDayStartAndSevenDayInclusiveStart() {
        withUtcZone {
            val today = utcDay(15)
            val window = StudyTaskTimingPolicy.windowFor(today + 12 * 60_000L)

            assertEquals(today, window.todayStartMillis)
            assertEquals(utcDay(9), window.sevenDayStartMillis)
            assertEquals(utcDay(16), window.tomorrowStartMillis)
            assertEquals(Record::class.java, StudyTaskTimingPolicy.Window::class.java.superclass)
        }
    }

    @Test
    fun summaryClampsInputsAndAveragesWeekTime() {
        val empty = StudyTaskTimingPolicy.summarize(-1L, -2L, -3)
        assertEquals(0L, empty.todayMillis)
        assertEquals(0L, empty.lastSevenDaysMillis)
        assertEquals(0, empty.answeredTasks)
        assertEquals(0L, empty.averageMillisPerTask())

        val summary = StudyTaskTimingPolicy.summarize(50L, 99L, 4)
        assertEquals(24L, summary.averageMillisPerTask())
        assertEquals(Record::class.java, StudyTaskTimingPolicy.Summary::class.java.superclass)
    }

    @Test
    fun elapsedClampingAndPauseResumeIgnoreNegativeOrInvisibleTime() {
        assertEquals(0L, StudyTaskTimingPolicy.boundedElapsed(-1L, 100L))
        assertEquals(100L, StudyTaskTimingPolicy.boundedElapsed(120L, 100L))
        assertEquals(0L, StudyTaskTimingPolicy.boundedElapsed(120L, -1L))

        assertEquals(15L, StudyTaskTimingPolicy.elapsedAfterPause(15L, 0L, 30L))
        assertEquals(15L, StudyTaskTimingPolicy.elapsedAfterPause(15L, 40L, 35L))
        assertEquals(30L, StudyTaskTimingPolicy.elapsedAfterPause(15L, 40L, 55L))
        assertEquals(60L, StudyTaskTimingPolicy.visibleSinceAfterResume(0L, 60L))
        assertEquals(50L, StudyTaskTimingPolicy.visibleSinceAfterResume(50L, 60L))
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
