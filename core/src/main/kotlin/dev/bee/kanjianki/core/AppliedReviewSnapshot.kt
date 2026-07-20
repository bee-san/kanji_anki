package dev.bee.kanjianki.core

class AppliedReviewSnapshot(
    @JvmField val token: String,
    @JvmField val beforeReview: RecordsStudyModels.StudyItem,
    @JvmField val afterReview: RecordsStudyModels.StudyItem,
)
