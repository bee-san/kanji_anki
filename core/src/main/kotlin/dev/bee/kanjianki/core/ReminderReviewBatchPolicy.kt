package dev.bee.kanjianki.core

import java.util.Calendar

object ReminderReviewBatchPolicy {
    private const val REVIEW_BATCH_GAP_MILLIS = 2 * 60 * 60 * 1000L
    private const val REVIEW_CUTOFF_HOUR = 22
    private const val REVIEW_CUTOFF_MINUTE = 0
    private const val MAX_NOTIFICATIONS_PER_DAY = 2

    @JvmStatic
    fun nextBatch(
        nowMillis: Long,
        studyItems: List<RecordsStudyModels.StudyItem>?,
        reviewNotificationsToday: Int,
    ): ReviewBatch? {
        if (reviewNotificationsToday >= MAX_NOTIFICATIONS_PER_DAY) {
            return null
        }
        val items = studyItems.orEmpty().sortedBy { it.dueAtMillis }
        if (items.isEmpty()) {
            return null
        }

        val cutoffMillis = cutoffMillis(nowMillis)
        if (nowMillis >= cutoffMillis) {
            return null
        }

        val visibleItems = items.filter { it.dueAtMillis <= cutoffMillis }
        if (visibleItems.isEmpty()) {
            return null
        }

        val overdueCount = visibleItems.count { it.dueAtMillis <= nowMillis }
        if (overdueCount > 0) {
            return ReviewBatch(nowMillis, overdueCount)
        }

        val futureItems = visibleItems.dropWhile { it.dueAtMillis <= nowMillis }
        if (futureItems.isEmpty()) {
            return null
        }

        var triggerAtMillis = futureItems.first().dueAtMillis
        var dueCount = 1
        for (index in 1 until futureItems.size) {
            val item = futureItems[index]
            if (item.dueAtMillis - triggerAtMillis <= REVIEW_BATCH_GAP_MILLIS) {
                triggerAtMillis = item.dueAtMillis
                dueCount++
            } else {
                break
            }
        }
        return ReviewBatch(triggerAtMillis, dueCount)
    }

    private fun cutoffMillis(nowMillis: Long): Long {
        val dayStart = LocalDayPolicy.localDayStart(nowMillis)
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = dayStart
        calendar.set(Calendar.HOUR_OF_DAY, REVIEW_CUTOFF_HOUR)
        calendar.set(Calendar.MINUTE, REVIEW_CUTOFF_MINUTE)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    @JvmRecord
    data class ReviewBatch(
        val triggerAtMillis: Long,
        val dueCount: Int,
    )
}
