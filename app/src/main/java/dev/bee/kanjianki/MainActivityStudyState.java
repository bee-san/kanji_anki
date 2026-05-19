package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.SchedulerTuner;
import dev.bee.kanjianki.core.study.HintState;
import dev.bee.kanjianki.core.study.WritingHintPolicy;

final class MainActivityStudyState {
    private final MainActivityStudy study;

    MainActivityStudyState(MainActivityStudy study) {
        this.study = study;
    }

    void completeActiveRepairStudyTask(String key, String outcome, long answeredAt) {
        study.studySessionTracker.completeActiveTask(study.store, key, outcome, answeredAt, false);
    }

    void tuneSchedulerIfNeeded(RecordsSchedulerModels.SchedulerParameters parameters, long now) {
        RecordsSchedulerModels.SchedulerParameters tuned = new SchedulerTuner().maybeTune(parameters, study.store.reviewStatsSince(now - SchedulerTuner.MONTH_MILLIS), now);
        StudyReviewActions.saveTunedSchedulerIfChanged(parameters, tuned, study.store::saveSchedulerParameters);
    }

    HintState initialHintState(RecordsSchedulerModels.StudySession session) {
        return WritingHintPolicy.initialHintState(
                session.item.writingLevel,
                session.item.totalReviews,
                session.item.learningStep,
                MainActivityBase.TASK_TARGETED_WRITING.equals(session.taskType)
        );
    }

    void setHintState(HintState state) {
        study.currentHintState = state == null ? HintState.initial() : state;
        study.currentPracticeLevel = study.currentHintState.level().writingLevel();
    }
}
