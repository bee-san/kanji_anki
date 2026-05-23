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
        writer.saveStudyItem(result.item)
        writer.saveReview(request, result.appliedRating, reviewedAt, beforeReview, result.item)
        recorder.recordReviewOutcome(request.kanji, result.appliedRating, beforeReview, result.item)
        if (MainActivityBase.RATING_AGAIN != result.appliedRating) {
            marker.markStudyRunPassed(request.kanji)
        }
    }

    @JvmStatic
    fun saveTunedSchedulerIfChanged(
        original: RecordsSchedulerModels.SchedulerParameters,
        tuned: RecordsSchedulerModels.SchedulerParameters,
        writer: SchedulerParametersWriter,
    ) {
        if (
            tuned.lastAdjustedAtMillis != original.lastAdjustedAtMillis ||
            tuned.lastAdjustmentReviewCount != original.lastAdjustmentReviewCount
        ) {
            writer.saveSchedulerParameters(tuned)
        }
    }

    interface ReviewWriter {
        fun saveStudyItem(item: RecordsStudyModels.StudyItem)

        fun saveReview(
            request: RecordsSchedulerModels.ReviewRequest,
            appliedRating: String?,
            reviewedAt: Long,
            beforeReview: RecordsStudyModels.StudyItem,
            afterReview: RecordsStudyModels.StudyItem,
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

    fun interface SchedulerParametersWriter {
        fun saveSchedulerParameters(parameters: RecordsSchedulerModels.SchedulerParameters)
    }
}
