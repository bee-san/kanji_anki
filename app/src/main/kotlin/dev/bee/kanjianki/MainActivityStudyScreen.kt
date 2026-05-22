package dev.bee.kanjianki

import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.StudySessionFocusPolicy

internal class MainActivityStudyScreen(private val study: MainActivityStudy) {
    fun renderStudy() {
        val rows = study.store.activeDashboardRows()
        val now = System.currentTimeMillis()
        val ladder = study.studyLadderSettings()
        study.activeStudyPlan = if (rows.isEmpty()) null else study.studyPlanForMode(rows, study.store.studyItems(), now)
        if (renderPendingRepairOrDone(study.activeStudyPlan, now, ladder)) {
            return
        }
        if (rows.isEmpty()) {
            renderEmptyStudyQueue()
            return
        }
        val beforeSeed = study.store.studyItems()
        val plan = study.studyPlanForMode(rows, beforeSeed, now)
        val seeded = study.studyQueue(rows, now, true, plan)
        val seededPlan = study.studyPlanForMode(rows, seeded, now)
        study.activeStudyPlan = seededPlan
        if (renderPendingRepairOrDone(seededPlan, now, ladder)) {
            return
        }
        study.activeSession = BridgeScheduler().nextSession(
            seeded,
            rows,
            now,
            study.studyAheadMillis(),
            StudySessionFocusPolicy.allowedKanji(seededPlan, study.continueAllKanjiSession),
            study.settings(),
            study.studyLadderSettings()
        )
        study.activeSimilarWritingRepair = null
        val session = study.activeSession
        if (session == null) {
            renderNoStudySession(seededPlan)
            return
        }
        StudySessionActions.activateStudySession(
            session,
            now,
            study.store::saveStudyItem,
            study::registerStudyTaskShown,
            study::startActiveStudyTask
        )
        study.renderSession(session)
    }

    fun renderPendingRepairOrDone(
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        now: Long,
        ladder: RecordsBase.StudyLadderSettings,
    ): Boolean {
        study.initializeSessionProgressTarget(plan)
        if (ladder.isEnabled(RecordsBase.LadderRung.WRITE_KANJI)) {
            for (repair in study.store.dueSimilarWritingRepairs(now)) {
                study.studySessionTracker.includePendingTask(study.similarRepairProgressKey(repair))
            }
            val repair = study.store.nextDueSimilarWritingRepair(now)
            if (repair != null) {
                study.renderLegacyStudyRoute()
                study.renderSimilarWritingRepair(repair, plan, now)
                return true
            }
        }
        if (study.studySessionTracker.atHardCap(study.continueAllKanjiSession)) {
            study.doneActions.renderStudyRunDone(plan)
            return true
        }
        return false
    }

    fun renderEmptyStudyQueue() {
        study.doneActions.renderEmptyStudyQueue()
    }

    fun renderNoStudySession(seededPlan: RecordsSchedulerModels.AdaptiveLoadPlan) {
        study.doneActions.renderNoStudySession(seededPlan)
    }

    fun renderFocusDone(plan: RecordsSchedulerModels.AdaptiveLoadPlan) {
        study.doneActions.renderFocusDone(plan)
    }

    fun renderStudyRunDone(plan: RecordsSchedulerModels.AdaptiveLoadPlan?) {
        study.doneActions.renderStudyRunDone(plan)
    }

    fun renderStudyForKanji(kanji: String?) {
        study.targetedLaunch.renderStudyForKanji(kanji)
    }

    fun renderStudyForKanjiNotAvailable() {
        study.doneActions.renderStudyForKanjiNotAvailable()
    }
}
