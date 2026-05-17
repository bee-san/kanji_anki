package dev.bee.kanjianki.domain.repository

import dev.bee.kanjianki.domain.model.study.StudyQueueItem
import dev.bee.kanjianki.domain.model.study.StudyRating
import dev.bee.kanjianki.domain.scheduler.StudyReviewRequest

interface StudyReviewPersistenceRepository {
    suspend fun saveAppliedReview(input: StudyReviewPersistenceInput): Boolean
}

data class StudyReviewPersistenceInput(
    val before: StudyQueueItem,
    val after: StudyQueueItem,
    val request: StudyReviewRequest,
    val appliedRating: StudyRating,
    val reviewedAtMillis: Long,
    val taskCompletion: StudyReviewTaskCompletion? = null,
)

data class StudyReviewTaskCompletion(
    val taskKey: String,
    val kanji: String,
    val taskType: String,
    val startedAtMillis: Long,
    val activeElapsedMillis: Long,
)
