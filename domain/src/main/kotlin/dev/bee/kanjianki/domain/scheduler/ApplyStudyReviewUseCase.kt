package dev.bee.kanjianki.domain.scheduler

import dev.bee.kanjianki.domain.repository.StudyReviewPersistenceInput
import dev.bee.kanjianki.domain.repository.StudyReviewPersistenceRepository
import dev.bee.kanjianki.domain.repository.StudyReviewTaskCompletion

class ApplyStudyReviewUseCase(
    private val reviewPersistenceRepository: StudyReviewPersistenceRepository,
    private val transitionEngine: StudyReviewTransitionEngine = StudyReviewTransitionEngine(),
) {
    suspend fun apply(
        input: StudyReviewTransitionInput,
        taskCompletion: StudyReviewTaskCompletion? = null,
    ): ApplyStudyReviewResult {
        val transition = transitionEngine.apply(input)
        if (transition.duplicate) {
            return ApplyStudyReviewResult(
                transition = transition,
                persisted = false,
            )
        }
        return ApplyStudyReviewResult(
            transition = transition,
            persisted = reviewPersistenceRepository.saveAppliedReview(
                StudyReviewPersistenceInput(
                    before = input.item,
                    after = transition.item,
                    request = input.request,
                    appliedRating = requireNotNull(transition.appliedRating),
                    reviewedAtMillis = input.nowMillis,
                    taskCompletion = taskCompletion,
                ),
            ),
        )
    }
}

data class ApplyStudyReviewResult(
    val transition: StudyReviewTransitionResult,
    val persisted: Boolean,
)
