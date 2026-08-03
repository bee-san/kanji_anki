package dev.bee.kanjianki

import dev.bee.kanjianki.core.AppliedReviewSnapshot
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyRatings
import dev.bee.kanjianki.data.ReviewCommitCommand
import dev.bee.kanjianki.data.ReviewCommitDisposition
import dev.bee.kanjianki.data.ReviewCommitResult

/**
 * The portable review-commit boundary, shared by both hosts.
 *
 * This is the `:app`-internal `StudyReviewActions` promoted into `:application` so
 * the desktop host can commit a review through the same code the Android host does.
 * The one `:app` tie it had — `MainActivityBase.RATING_AGAIN` — is just
 * [StudyRatings.AGAIN], so nothing Android-specific crossed with it.
 *
 * The invariant it guards is CLAUDE.md's: the advanced item and the review-log row
 * are written in one transaction ([ReviewWriter.commitReview]), so process death
 * between them cannot advance scheduling with no log row. Only an APPLIED disposition
 * records an outcome or marks a pass — a DUPLICATE or STALE commit changed nothing and
 * must not.
 */
object StudyReviewCommit {
    suspend fun saveAppliedReview(
        request: RecordsSchedulerModels.ReviewRequest,
        result: RecordsSchedulerModels.ReviewResult,
        beforeReview: RecordsStudyModels.StudyItem,
        reviewedAt: Long,
        writer: ReviewWriter,
        recorder: ReviewOutcomeRecorder,
        marker: StudyRunMarker,
    ): ReviewCommitResult {
        val commit = writer.commitReview(
            ReviewCommitCommand(
                afterReview = result.item,
                request = request,
                appliedRating = result.appliedRating,
                reviewedAtMillis = reviewedAt,
                beforeReview = beforeReview,
            ),
        )
        val committedItem = commit.item
        if (commit.disposition != ReviewCommitDisposition.APPLIED || committedItem == null) {
            return commit
        }
        recorder.recordReviewOutcome(request.kanji, result.appliedRating, beforeReview, committedItem)
        if (StudyRatings.AGAIN != result.appliedRating) {
            marker.markStudyRunPassed(request.kanji)
        }
        return commit
    }

    /**
     * Reverses the last applied review, only when the current item still matches the
     * one the review produced.
     *
     * The boundary check is exact: undo restores the pre-review item and deletes the
     * review-log row, so it must not fire when a later review has already advanced the
     * item past the one being reversed. [matchesUndoBoundary] is the full-field
     * comparison that decides that.
     */
    fun undoLastAppliedReview(
        snapshot: AppliedReviewSnapshot?,
        currentItem: RecordsStudyModels.StudyItem?,
        writer: UndoWriter,
    ): Boolean {
        if (snapshot == null || currentItem == null || snapshot.token.isEmpty()) return false
        if (!matchesUndoBoundary(currentItem, snapshot.afterReview)) return false
        writer.saveStudyItem(snapshot.beforeReview)
        writer.deleteReviewByToken(snapshot.token)
        return true
    }

    fun matchesUndoBoundary(
        currentItem: RecordsStudyModels.StudyItem?,
        afterReview: RecordsStudyModels.StudyItem?,
    ): Boolean {
        if (currentItem == null || afterReview == null) return false
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
            currentItem.activeToken == afterReview.activeToken &&
            currentItem.schedulerRevision == afterReview.schedulerRevision &&
            currentItem.routingVersion == afterReview.routingVersion &&
            currentItem.adaptiveRouteStateJson == afterReview.adaptiveRouteStateJson
    }

    fun interface ReviewWriter {
        /** Persists the advanced item and the review-log row in one transaction. */
        suspend fun commitReview(command: ReviewCommitCommand): ReviewCommitResult
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
