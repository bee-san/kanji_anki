package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;

final class MainActivityStudyProgress {
    private final MainActivityStudy study;

    MainActivityStudyProgress(MainActivityStudy study) {
        this.study = study;
    }

    void resetStudyRunProgress() {
        study.activeSimilarWritingRepair = null;
        study.studySessionTracker.resetProgress();
    }

    void clearStudyModeOverrides() {
        study.continueAllKanjiSession = false;
        study.studyMoreNewCardKanji.clear();
    }

    void markStudyRunPassed(String kanji) {
        if (study.activeSession != null) {
            study.markStudyTaskCompleted(sessionTaskKey(study.activeSession));
            return;
        }
        if (kanji != null && !kanji.isEmpty()) {
            study.markStudyTaskCompleted("kanji:" + kanji);
        }
    }

    void initializeSessionProgressTarget(RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        study.studySessionTracker.initializeTarget(plan);
    }

    void registerStudyTaskShown(String key) {
        study.studySessionTracker.registerTaskShown(key);
    }

    void markStudyTaskCompleted(String key) {
        study.studySessionTracker.markTaskCompleted(key);
    }

    String sessionTaskKey(RecordsSchedulerModels.StudySession session) {
        return StudySessionTracker.sessionTaskKey(session);
    }

    String similarRepairProgressKey(RecordsImportModels.SimilarKanjiWritingRepair repair) {
        return StudySessionTracker.similarRepairProgressKey(repair);
    }

    String similarRepairStudyTaskKey(RecordsImportModels.SimilarKanjiWritingRepair repair) {
        return StudySessionTracker.similarRepairStudyTaskKey(repair);
    }

    void startActiveStudyTask(String key, String kanji, String taskType, long startedAt) {
        study.studySessionTracker.startActiveTask(key, kanji, taskType, startedAt, !study.activityPaused);
    }

    void completeActiveStudyTask(String key, String outcome, long answeredAt) {
        study.studySessionTracker.completeActiveTask(study.store, key, outcome, answeredAt, true);
    }

    void pauseActiveStudyTask() {
        study.studySessionTracker.pauseActiveTask();
    }

    void resumeActiveStudyTask() {
        study.studySessionTracker.resumeActiveTask();
    }

    void abandonActiveStudyTask() {
        study.studySessionTracker.abandonActiveTask();
    }
}
