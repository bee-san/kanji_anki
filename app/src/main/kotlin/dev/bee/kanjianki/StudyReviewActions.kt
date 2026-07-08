package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels

internal object StudyReviewActions {
    @JvmStatic
    fun saveAppliedReview(
        request: RecordsSchedulerModels.ReviewRequest,
        result: RecordsSchedulerModels.ReviewResult,
        beforeReview: RecordsStudyModels.StudyItem,
        reviewedAt: Long,
        writer: ReviewWriter,
        recorder: ReviewOutcomeRecorder,
        marker: StudyRunMarker,
    ) {
        // One transaction for the item + review-log row: process death between
        // them would advance scheduling with no review_log row (lost review;
        // token wrongly retryable while the item already advanced).
        writer.saveReviewOutcome(item = result.item, request = request, appliedRating = result.appliedRating, reviewedAt = reviewedAt, beforeReview = beforeReview)
        recorder.recordReviewOutcome(request.kanji, result.appliedRating, beforeReview, result.item)
        if (MainActivityBase.RATING_AGAIN != result.appliedRating) {
            marker.markStudyRunPassed(request.kanji)
        }
    }

    @JvmStatic
    fun undoLastAppliedReview(
        snapshot: AppliedReviewSnapshot?,
        currentItem: RecordsStudyModels.StudyItem?,
        writer: UndoWriter,
    ): Boolean {
        if (snapshot == null || currentItem == null || snapshot.token.isEmpty()) {
            return false
        }
        if (!matchesUndoBoundary(currentItem, snapshot.afterReview)) {
            return false
        }
        writer.saveStudyItem(snapshot.beforeReview)
        writer.deleteReviewByToken(snapshot.token)
        return true
    }

    @JvmStatic
    fun matchesUndoBoundary(
        currentItem: RecordsStudyModels.StudyItem?,
        afterReview: RecordsStudyModels.StudyItem?,
    ): Boolean {
        if (currentItem == null || afterReview == null) {
            return false
        }
        return currentItem.kanji == afterReview.kanji &&
            currentItem.answerSignature == afterReview.answerSignature &&
            currentItem.state == afterReview.state &&
            currentItem.dueAtMillis == afterReview.dueAtMillis &&
            currentItem.stability == afterReview.stability &&
            currentItem.difficulty == afterReview.difficulty &&
            currentItem.totalReviews == afterReview.totalReviews &&
            currentItem.lapses == afterReview.lapses &&
            currentItem.learningStep == afterReview.learningStep &&
            currentItem.writingLevel == afterReview.writingLevel &&
            currentItem.recognitionStage == afterReview.recognitionStage &&
            currentItem.consecutiveFailedRecognitionDays == afterReview.consecutiveFailedRecognitionDays &&
            currentItem.lastFailedRecognitionDayMillis == afterReview.lastFailedRecognitionDayMillis &&
            currentItem.writingRemediationPending == afterReview.writingRemediationPending &&
            currentItem.suppressedByTaskType == afterReview.suppressedByTaskType &&
            currentItem.suppressedAtMillis == afterReview.suppressedAtMillis &&
            currentItem.matureIntervalDays == afterReview.matureIntervalDays &&
            currentItem.rung == afterReview.rung &&
            currentItem.phase == afterReview.phase &&
            currentItem.realPassStreak == afterReview.realPassStreak &&
            currentItem.realAgainStreak == afterReview.realAgainStreak &&
            currentItem.lastRealReviewDueAtMillis == afterReview.lastRealReviewDueAtMillis &&
            currentItem.activeToken == afterReview.activeToken
    }

    class AppliedReviewSnapshot(
        @JvmField val token: String,
        @JvmField val beforeReview: RecordsStudyModels.StudyItem,
        @JvmField val afterReview: RecordsStudyModels.StudyItem,
    )

    fun interface ReviewWriter {
        /**
         * Persists the advanced item and the review-log row atomically. Both
         * writes must land in one transaction so a crash cannot advance the
         * item without recording the review.
         */
        fun saveReviewOutcome(
            item: RecordsStudyModels.StudyItem,
            request: RecordsSchedulerModels.ReviewRequest,
            appliedRating: String?,
            reviewedAt: Long,
            beforeReview: RecordsStudyModels.StudyItem,
        )
    }

    fun interface ReviewOutcomeRecorder {
        fun recordReviewOutcome(
            kanji: String,
            appliedRating: String?,
            beforeReview: RecordsStudyModels.StudyItem,
            afterReview: RecordsStudyModels.StudyItem,
        )
    }

    fun interface StudyRunMarker {
        fun markStudyRunPassed(kanji: String)
    }

    interface UndoWriter {
        fun saveStudyItem(item: RecordsStudyModels.StudyItem)

        fun deleteReviewByToken(token: String)
    }
}
