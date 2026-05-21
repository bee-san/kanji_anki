package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels

internal class MainActivityStudyReviewWriter(
    private val activity: MainActivityStudy,
) : StudyReviewActions.ReviewWriter {
    override fun saveStudyItem(item: RecordsStudyModels.StudyItem) {
        activity.store.saveStudyItem(item)
    }

    override fun saveReview(
        request: RecordsSchedulerModels.ReviewRequest,
        appliedRating: String,
        reviewedAt: Long,
        beforeReview: RecordsStudyModels.StudyItem?,
        afterReview: RecordsStudyModels.StudyItem?,
    ) {
        activity.store.saveReview(request, appliedRating, reviewedAt, beforeReview, afterReview)
    }
}
