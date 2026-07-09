package dev.bee.kanjianki.core

import java.util.Calendar

object ReminderReviewBatchPolicy {
    private const val REVIEW_BATCH_GAP_MILLIS = 2 * 60 * 60 * 1000L
    private const val REVIEW_CUTOFF_HOUR = 22
    private const val REVIEW_CUTOFF_MINUTE = 0

    /** Hard per-day fuse for review notifications, independent of user settings. */
    const val MAX_NOTIFICATIONS_PER_DAY: Int = 2

    /**
     * Default smallest batch worth a review notification. A single learning-step
     * tail ("1 review ready" 10 minutes after the user closed the app, D5) is not
     * worth a buzz; it waits for a larger cluster or the daily reminder. The
     * caller lowers this to 1 when an override applies (e.g. the user's configured
     * daily time), so overrides are decided by the caller, not baked in here.
     */
    const val DEFAULT_MIN_BATCH_SIZE: Int = 3

    @JvmStatic
    fun nextBatch(
        nowMillis: Long,
        studyItems: List<RecordsStudyModels.StudyItem>?,
        reviewNotificationsToday: Int,
    ): ReviewBatch? {
        return nextBatch(nowMillis, studyItems, reviewNotificationsToday, DEFAULT_MIN_BATCH_SIZE)
    }

    @JvmStatic
    fun nextBatch(
        nowMillis: Long,
        studyItems: List<RecordsStudyModels.StudyItem>?,
        reviewNotificationsToday: Int,
        minBatchSize: Int,
    ): ReviewBatch? {
        if (reviewNotificationsToday >= MAX_NOTIFICATIONS_PER_DAY) {
            return null
        }
        val requiredBatchSize = minBatchSize.coerceAtLeast(1)
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

        val overdueItems = visibleItems.filter { it.dueAtMillis <= nowMillis }
        if (overdueItems.isNotEmpty()) {
            // Trigger is now, but the signature's latest-due is the newest overdue
            // item's due time — stable across recomputes so an unchanged overdue
            // set keeps the same throttle signature (D1).
            val latestOverdueDue = overdueItems.maxOf { it.dueAtMillis }
            return batchOrNull(nowMillis, overdueItems.size, latestOverdueDue, requiredBatchSize)
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
        return batchOrNull(triggerAtMillis, dueCount, triggerAtMillis, requiredBatchSize)
    }

    private fun batchOrNull(
        triggerAtMillis: Long,
        dueCount: Int,
        latestDueAtMillis: Long,
        requiredBatchSize: Int,
    ): ReviewBatch? {
        if (dueCount < requiredBatchSize) {
            return null
        }
        return ReviewBatch(triggerAtMillis, dueCount, latestDueAtMillis)
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
        /**
         * The latest due-at among the batched items, stable across fire-time
         * recomputes (unlike [triggerAtMillis], which is `now` for overdue sets).
         * Feeds [ReminderThrottlePolicy.signatureFor] so an unchanged overdue set
         * is recognized and not re-notified.
         */
        val latestDueAtMillis: Long = triggerAtMillis,
    )
}
