package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.RoomStudyRuntimeOwnershipPolicy
import dev.bee.kanjianki.domain.repository.StudyReviewTaskCompletion
import dev.bee.kanjianki.domain.scheduler.ApplyStudyReviewUseCase
import dev.bee.kanjianki.domain.scheduler.LearningStepSettings
import dev.bee.kanjianki.domain.scheduler.StudyReviewTransitionInput
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

class RoomStudyReviewBridge @Inject constructor(
    private val applyStudyReviewUseCase: ApplyStudyReviewUseCase,
    private val ownershipPolicy: RoomStudyRuntimeOwnershipPolicy,
) {
    suspend fun applyReview(
        item: RecordsStudyModels.StudyItem,
        request: RecordsSchedulerModels.ReviewRequest,
        consumedTokens: MutableSet<String>?,
        nowMillis: Long,
        parameters: RecordsSchedulerModels.SchedulerParameters?,
        settings: RecordsSyncModels.Settings?,
        learningSettings: RecordsSchedulerModels.LearningStepSettings?,
        ladder: RecordsBase.StudyLadderSettings?,
        taskCompletion: StudyReviewTaskCompletion? = null,
    ): RoomStudyReviewBridgeResult {
        check(ownershipPolicy.canWriteReviewsToRoom()) {
            "Room review writes require completed legacy reset/migration or active double-write ownership."
        }
        val transitionInput = StudyReviewTransitionInput(
            item = LegacyStudyMappers.toDomain(item),
            request = LegacyStudyMappers.toDomain(request),
            nowMillis = nowMillis,
            consumedTokens = consumedTokens?.let(::LinkedHashSet) ?: linkedSetOf(),
            targetRetention = safeParameters(parameters).targetRetention,
            learningSettings = safeLearningSettings(learningSettings),
            ladderSettings = LegacyStudyMappers.toDomain(settings, ladder),
        )
        val result = applyStudyReviewUseCase.apply(transitionInput, taskCompletion)
        if (!result.transition.duplicate && result.persisted && consumedTokens != null) {
            consumedTokens.add(request.token ?: "")
        }
        if (!result.transition.duplicate && !result.persisted) {
            return RoomStudyReviewBridgeResult(
                reviewResult = RecordsSchedulerModels.ReviewResult(
                    item,
                    RATING_NOT_PERSISTED,
                    false,
                    "Review was not saved.",
                ),
                persisted = false,
            )
        }
        return RoomStudyReviewBridgeResult(
            reviewResult = RecordsSchedulerModels.ReviewResult(
                if (result.transition.duplicate) {
                    item
                } else {
                    LegacyStudyMappers.toLegacy(item, result.transition.item)
                },
                result.transition.appliedRating?.wireName ?: "duplicate",
                result.transition.duplicate,
                result.transition.message,
            ),
            persisted = result.persisted,
        )
    }

    @JvmOverloads
    fun applyReviewBlocking(
        item: RecordsStudyModels.StudyItem,
        request: RecordsSchedulerModels.ReviewRequest,
        consumedTokens: MutableSet<String>?,
        nowMillis: Long,
        parameters: RecordsSchedulerModels.SchedulerParameters?,
        settings: RecordsSyncModels.Settings?,
        learningSettings: RecordsSchedulerModels.LearningStepSettings?,
        ladder: RecordsBase.StudyLadderSettings?,
        taskCompletion: StudyReviewTaskCompletion? = null,
    ): RoomStudyReviewBridgeResult = runBlocking {
        applyReview(
            item = item,
            request = request,
            consumedTokens = consumedTokens,
            nowMillis = nowMillis,
            parameters = parameters,
            settings = settings,
            learningSettings = learningSettings,
            ladder = ladder,
            taskCompletion = taskCompletion,
        )
    }

    private fun safeParameters(
        parameters: RecordsSchedulerModels.SchedulerParameters?,
    ): RecordsSchedulerModels.SchedulerParameters =
        parameters ?: RecordsSchedulerModels.SchedulerParameters.defaults()

    private fun safeLearningSettings(
        settings: RecordsSchedulerModels.LearningStepSettings?,
    ): LearningStepSettings =
        LegacyStudyMappers.toDomain(settings ?: RecordsSchedulerModels.LearningStepSettings.defaults())
}

data class RoomStudyReviewBridgeResult(
    val reviewResult: RecordsSchedulerModels.ReviewResult,
    val persisted: Boolean,
)

private const val RATING_NOT_PERSISTED = "not_persisted"
