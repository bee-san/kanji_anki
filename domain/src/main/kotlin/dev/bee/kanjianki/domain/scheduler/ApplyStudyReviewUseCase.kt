package dev.bee.kanjianki.domain.scheduler

import dev.bee.kanjianki.domain.repository.StudyQueueRepository

class ApplyStudyReviewUseCase(
    private val studyQueueRepository: StudyQueueRepository,
    private val transitionEngine: StudyReviewTransitionEngine = StudyReviewTransitionEngine(),
) {
    suspend fun apply(input: StudyReviewTransitionInput): ApplyStudyReviewResult {
        val transition = transitionEngine.apply(input)
        if (transition.duplicate) {
            return ApplyStudyReviewResult(
                transition = transition,
                persisted = false,
            )
        }
        return ApplyStudyReviewResult(
            transition = transition,
            persisted = studyQueueRepository.updateReviewedItem(transition.item),
        )
    }
}

data class ApplyStudyReviewResult(
    val transition: StudyReviewTransitionResult,
    val persisted: Boolean,
)
