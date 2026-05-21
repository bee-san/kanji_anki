package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels

internal class MainActivityStudyProgress(private val study: MainActivityStudy) {
    fun resetStudyRunProgress() {
        study.activeSimilarWritingRepair = null
        study.studySessionTracker.resetProgress()
    }

    fun clearStudyModeOverrides() {
        study.continueAllKanjiSession = false
        study.studyMoreNewCardKanji.clear()
    }

    fun markStudyRunPassed(kanji: String?) {
        if (study.activeSession != null) {
            study.markStudyTaskCompleted(sessionTaskKey(study.activeSession))
            return
        }
        if (!kanji.isNullOrEmpty()) {
            study.markStudyTaskCompleted("kanji:$kanji")
        }
    }

    fun initializeSessionProgressTarget(plan: RecordsSchedulerModels.AdaptiveLoadPlan?) {
        study.studySessionTracker.initializeTarget(plan)
    }

    fun registerStudyTaskShown(key: String?) {
        study.studySessionTracker.registerTaskShown(key)
    }

    fun markStudyTaskCompleted(key: String?) {
        study.studySessionTracker.markTaskCompleted(key)
    }

    fun sessionTaskKey(session: RecordsSchedulerModels.StudySession?): String {
        return StudySessionTracker.sessionTaskKey(session)
    }

    fun similarRepairProgressKey(repair: RecordsImportModels.SimilarKanjiWritingRepair?): String {
        return StudySessionTracker.similarRepairProgressKey(repair)
    }

    fun similarRepairStudyTaskKey(repair: RecordsImportModels.SimilarKanjiWritingRepair?): String {
        return StudySessionTracker.similarRepairStudyTaskKey(repair)
    }

    fun startActiveStudyTask(key: String?, kanji: String?, taskType: String?, startedAt: Long) {
        study.studySessionTracker.startActiveTask(key, kanji, taskType, startedAt, !study.activityPaused)
    }

    fun completeActiveStudyTask(key: String?, outcome: String?, answeredAt: Long) {
        study.studySessionTracker.completeActiveTask(study.store, key, outcome, answeredAt, true)
    }

    fun pauseActiveStudyTask() {
        study.studySessionTracker.pauseActiveTask()
    }

    fun resumeActiveStudyTask() {
        study.studySessionTracker.resumeActiveTask()
    }

    fun abandonActiveStudyTask() {
        study.studySessionTracker.abandonActiveTask()
    }
}
