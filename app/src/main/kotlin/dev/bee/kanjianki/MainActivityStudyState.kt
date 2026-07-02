package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.study.HintState
import dev.bee.kanjianki.core.study.WritingHintPolicy

internal class MainActivityStudyState(private val study: MainActivityStudy) {
    fun completeActiveRepairStudyTask(key: String?, outcome: String?, answeredAt: Long) {
        study.studySessionTracker.completeActiveTask(study.store, key, outcome, answeredAt, false)
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
