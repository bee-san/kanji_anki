package dev.bee.kanjianki

import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.StudySessionFocusPolicy
import dev.bee.kanjianki.core.StudyTextCopy

internal class MainActivityStudyQueueCoordinator(private val study: MainActivityStudy) {
    fun renderStudy() {
        withStudyLoadProbe("renderStudy.total") { renderStudyInternal() }
    }

    private fun renderStudyInternal() {
        val rows = withStudyLoadProbe("activeDashboardRows") { study.store.activeDashboardRows() }
        val now = System.currentTimeMillis()
        val ladder = withStudyLoadProbe("studyLadderSettings") { study.studyLadderSettings() }
        studyLoadDebug("renderStudy rows=${rows.size}")
        val currentItems = withStudyLoadProbe("studyItemsForKanji") {
            if (rows.isEmpty()) emptyList() else study.store.studyItemsForKanji(rows.map { it.kanji })
        }
        studyLoadDebug("renderStudy currentItems=${currentItems.size}")
        val plan = withStudyLoadProbe("studyPlanForMode#1") {
            if (rows.isEmpty()) null else study.studyPlanForMode(rows, currentItems, now)
        }
        study.activeStudyPlan = plan
        if (withStudyLoadProbe("renderPendingRepairOrDone#1") { renderPendingRepairOrDone(plan, now, ladder) }) {
            return
        }
        if (rows.isEmpty()) {
            study.renderEmptyStudyQueue()
            return
        }
        val seeded = withStudyLoadProbe("studyQueue") { study.studyQueue(rows, now, true, plan, currentItems) }
        val seededPlan = withStudyLoadProbe("studyPlanForMode#2") { study.studyPlanForMode(rows, seeded, now) }
        study.activeStudyPlan = seededPlan
        if (withStudyLoadProbe("renderPendingRepairOrDone#2") { renderPendingRepairOrDone(seededPlan, now, ladder) }) {
            return
        }
        val allowedKanji = StudySessionFocusPolicy.allowedKanji(seededPlan, study.continueAllKanjiSession)
        study.activeSession = withStudyLoadProbe("plannedStudySession") {
            StudySessionActions.plannedStudySession(
                BridgeScheduler(),
                study.studySessionTracker,
                seeded,
                rows,
                now,
                study.studyAheadMillis(),
                allowedKanji,
                study.settings(),
                ladder,
            )
        }
        study.activeSimilarWritingRepair = null
        val session = study.activeSession
        if (session == null) {
            study.renderNoStudySession(seededPlan)
            return
        }
        if (session.item == null) {
            study.activeSession = null
            study.renderNoStudySession(seededPlan)
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

    fun renderStudyForKanji(kanji: String?) {
        study.clearStudyModeOverrides()
        study.resetStudyRunProgress()
        study.activeSimilarWritingRepair = null
        val rows = study.store.activeDashboardRows()
        val now = System.currentTimeMillis()
        val ladder = study.studyLadderSettings()
        val currentItems = if (rows.isEmpty()) emptyList() else study.store.studyItemsForKanji(rows.map { it.kanji })
        study.activeStudyPlan = if (rows.isEmpty()) null else study.adaptivePlan(rows, currentItems, now)
        val row = study.findRow(rows, kanji ?: "")
        if (row == null) {
            study.renderStudyForKanjiNotAvailable()
            return
        }
        val seeded = study.studyQueue(rows, now, true, study.activeStudyPlan, currentItems)
        study.activeStudyPlan = study.adaptivePlan(rows, seeded, now)
        val session = BridgeScheduler().targetedSession(
            seeded,
            row,
            now,
            ladder
        )
        if (session == null) {
            study.renderStudyForKanjiNotAvailable()
            return
        }
        study.activeSession = session
        StudySessionActions.activateStudySession(
            session,
            now,
            study.store::saveStudyItem,
            study::registerStudyTaskShown,
            study::startActiveStudyTask
        )
        study.renderSession(session)
    }

    private fun renderPendingRepairOrDone(
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
                val active = StudyRepairActions.activateSimilarWritingRepair(
                    repair,
                    now,
                    study.store::saveSimilarWritingRepair,
                )
                val activeRepair = active.repair
                study.activeSimilarWritingRepair = activeRepair
                val item = BridgeScheduler().newTargetedStudyItem(activeRepair.repairKanji, now, ladder)
                val session = RecordsSchedulerModels.StudySession(
                    item.withToken(active.token),
                    null,
                    active.token,
                    MainActivityBase.TASK_REPAIR_WRITING,
                    true,
                    StudyTextCopy.similarRepairPrompt(activeRepair)
                )
                study.activeSession = session
                study.activeStudyPlan = plan
                study.registerStudyTaskShown(active.progressKey)
                study.startActiveStudyTask(
                    active.studyTaskKey,
                    activeRepair.repairKanji,
                    MainActivityBase.TASK_REPAIR_WRITING,
                    now,
                )
                study.renderComposeWritingSession(session)
                return true
            }
        }
        if (study.studySessionTracker.atHardCap(study.continueAllKanjiSession)) {
            study.doneActions.renderStudyRunDone(plan)
            return true
        }
        return false
    }
}
