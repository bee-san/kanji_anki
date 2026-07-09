package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ReminderReviewBatchPolicyTest {
    @Test
    fun batchesFutureReviewsThatArriveWithinTwoHours() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val now = utc(2026, Calendar.MAY, 15, 9, 0)
            val batch = ReminderReviewBatchPolicy.nextBatch(
                now,
                listOf(
                    studyItem(now + 3 * HOUR),
                    studyItem(now + 4 * HOUR),
                    studyItem(now + 5 * HOUR),
                    studyItem(now + 9 * HOUR),
                ),
                0,
            )

            assertEquals(now + 5 * HOUR, batch?.triggerAtMillis)
            assertEquals(3, batch?.dueCount)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun firesImmediatelyForOverdueReviewsBeforeTheCutoff() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val now = utc(2026, Calendar.MAY, 15, 15, 30)
            // A single overdue item below the default minimum batch size only fires
            // when the caller lowers the threshold (e.g. a daily-time override).
            val batch = ReminderReviewBatchPolicy.nextBatch(
                now,
                listOf(
                    studyItem(now - HOUR),
                    studyItem(now + 2 * HOUR),
                ),
                0,
                1,
            )

            assertEquals(now, batch?.triggerAtMillis)
            assertEquals(1, batch?.dueCount)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun suppressesSmallOverdueTailBelowDefaultMinimumBatch() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val now = utc(2026, Calendar.MAY, 15, 15, 30)
            // One overdue learning-step tail (D5): nothing fires at the default
            // minimum batch size of 3.
            val tail = ReminderReviewBatchPolicy.nextBatch(
                now,
                listOf(studyItem(now - HOUR)),
                0,
            )
            assertNull(tail)

            // Three overdue reviews clear the default minimum and fire now.
            val cluster = ReminderReviewBatchPolicy.nextBatch(
                now,
                listOf(
                    studyItem(now - HOUR),
                    studyItem(now - 30 * MINUTE),
                    studyItem(now - 10 * MINUTE),
                ),
                0,
            )
            assertEquals(now, cluster?.triggerAtMillis)
            assertEquals(3, cluster?.dueCount)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun ignoresLateEveningReviewsAndStopsAfterTwoNotifications() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val now = utc(2026, Calendar.MAY, 15, 21, 30)
            val late = ReminderReviewBatchPolicy.nextBatch(
                now,
                listOf(studyItem(now + 2 * HOUR)),
                0,
            )
            val capped = ReminderReviewBatchPolicy.nextBatch(
                now,
                listOf(studyItem(now + HOUR)),
                2,
            )

            assertNull(late)
            assertNull(capped)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    private fun utc(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.set(year, month, day, hour, minute, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun studyItem(dueAtMillis: Long): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(
            "裂",
            "review",
            dueAtMillis,
            1.0,
            5.0,
            2,
            0,
            2,
            1,
            null,
            dueAtMillis,
        )
    }

    private companion object {
        private const val HOUR = 60 * 60 * 1000L
        private const val MINUTE = 60 * 1000L
    }
}
