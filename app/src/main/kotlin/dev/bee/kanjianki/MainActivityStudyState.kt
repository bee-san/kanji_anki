package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.SchedulerTuner
import dev.bee.kanjianki.core.study.HintState
import dev.bee.kanjianki.core.study.WritingHintPolicy

internal class MainActivityStudyState(private val study: MainActivityStudy) {
    fun completeActiveRepairStudyTask(key: String?, outcome: String?, answeredAt: Long) {
        study.studySessionTracker.completeActiveTask(study.store, key, outcome, answeredAt, false)
    }

    fun tuneSchedulerIfNeeded(parameters: RecordsSchedulerModels.SchedulerParameters, now: Long) {
        val tuned = SchedulerTuner().maybeTune(
            parameters,
            study.store.reviewStatsSince(now - SchedulerTuner.MONTH_MILLIS),
            now
        )
        StudyReviewActions.saveTunedSchedulerIfChanged(parameters, tuned, study.store::saveSchedulerParameters)
    }

    fun initialHintState(session: RecordsSchedulerModels.StudySession): HintState {
        val item = session.item ?: return HintState.initial()
        return WritingHintPolicy.initialHintState(
            item.writingLevel,
            item.totalReviews,
            item.learningStep,
            MainActivityBase.TASK_TARGETED_WRITING == session.taskType
        )
    }

    fun setHintState(state: HintState?) {
        study.currentHintState = state ?: HintState.initial()
        study.currentPracticeLevel = study.currentHintState.level().writingLevel()
    }
}
