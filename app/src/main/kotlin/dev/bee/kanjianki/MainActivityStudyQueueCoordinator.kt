package dev.bee.kanjianki

import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.StudySessionFocusPolicy
import dev.bee.kanjianki.core.StudyTextCopy

internal class MainActivityStudyQueueCoordinator(private val study: MainActivityStudy) {
    fun renderStudy() {
        // Load the Study route through the same async pattern as Home/Settings: all the
        // LocalStore reads/writes and scheduler work run on the background executor, and
        // only the returned render thunk runs on the main thread.
        study.loadRouteAsync(
            showLoading = { study.renderStudyLoading() },
            load = { withStudyLoadProbe("renderStudy.total") { computeStudyRender() } },
            render = { it() },
            traceName = "study-route",
        )
    }

    /**
     * Runs on the background executor: performs every LocalStore read/write and the
     * scheduler computation, mutates the study session state, and returns a thunk that
     * renders the resulting screen when invoked on the main thread.
     */
    private fun computeStudyRender(): () -> Unit {
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
        refreshSessionBadgeCount(plan)
        pendingRepairOrDoneRender(plan, now, ladder)?.let { return it }
        if (rows.isEmpty()) {
            return { study.renderEmptyStudyQueue() }
        }
        val seeded = withStudyLoadProbe("studyQueue") { study.studyQueue(rows, now, true, plan, currentItems) }
        val seededPlan = withStudyLoadProbe("studyPlanForMode#2") {
            // The plan is a pure function of (rows, items, now) plus mode state that
            // cannot change mid-compute, so when seeding left the queue unchanged the
            // first plan is still exact and the second computation is skipped.
            if (plan != null && StudyItemComparators.sameStudyQueue(currentItems, seeded)) {
                plan
            } else {
                study.studyPlanForMode(rows, seeded, now)
            }
        }
        study.activeStudyPlan = seededPlan
        refreshSessionBadgeCount(seededPlan)
        pendingRepairOrDoneRender(seededPlan, now, ladder)?.let { return it }
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
            warmStudyDoneAvailability()
            return { study.renderNoStudySession(seededPlan) }
        }
        if (session.item == null) {
            study.activeSession = null
            warmStudyDoneAvailability()
            return { study.renderNoStudySession(seededPlan) }
        }
        StudySessionActions.activateStudySession(
            session,
            now,
            study.store::saveStudyItem,
            study::registerStudyTaskShown,
            study::startActiveStudyTask
        )
        // Prepare the session render (choice cards, dictionary, stroke guides) here on
        // the background executor; only the returned render thunk touches the UI.
        return study.prepareSessionRender(session)
    }

    fun renderStudyForKanji(kanji: String?) {
        // Same async pattern as renderStudy: the targeted-session compute does full
        // dashboard/study-item reads and queue persistence, which used to run on the
        // main thread for undo and browse-detail entry points.
        study.loadRouteAsync(
            showLoading = { study.renderStudyLoading() },
            load = { computeStudyForKanjiRender(kanji) },
            render = { it() },
            traceName = "study-kanji-route",
        )
    }

    /**
     * Background-thread compute for the targeted-kanji study entry points (undo,
     * browse detail). Returns a thunk that renders the resulting screen on main.
     */
    private fun computeStudyForKanjiRender(kanji: String?): () -> Unit {
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
            return { study.renderStudyForKanjiNotAvailable() }
        }
        val seeded = study.studyQueue(rows, now, true, study.activeStudyPlan, currentItems)
        val preSeedPlan = study.activeStudyPlan
        study.activeStudyPlan = if (preSeedPlan != null && StudyItemComparators.sameStudyQueue(currentItems, seeded)) {
            preSeedPlan
        } else {
            study.adaptivePlan(rows, seeded, now)
        }
        refreshSessionBadgeCount(study.activeStudyPlan)
        val session = BridgeScheduler().targetedSession(
            seeded,
            row,
            now,
            ladder
        )
        if (session == null) {
            return { study.renderStudyForKanjiNotAvailable() }
        }
        study.activeSession = session
        StudySessionActions.activateStudySession(
            session,
            now,
            study.store::saveStudyItem,
            study::registerStudyTaskShown,
            study::startActiveStudyTask
        )
        return study.prepareSessionRender(session)
    }

    /**
     * Background-thread compute for the pending-repair / done branches. Performs the
     * repair writes and session-state mutation, returning a render thunk when an early
     * screen should be shown, or null to continue building the main study session.
     */
    private fun pendingRepairOrDoneRender(
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        now: Long,
        ladder: RecordsBase.StudyLadderSettings,
    ): (() -> Unit)? {
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
                // Warm the stroke-guide asset and the repair kanji's dictionary entry
                // on this background thread so the writing pad render never parses or
                // queries them on main.
                study.warmStrokeGuides()
                study.warmSessionDictionaryEntry(session)
                return { study.renderComposeWritingSession(session) }
            }
        }
        if (study.studySessionTracker.atHardCap(study.continueAllKanjiSession)) {
            warmStudyDoneAvailability()
            return { study.doneActions.renderStudyRunDone(plan) }
        }
        return null
    }

    /**
     * Keeps the shell's cached Study-badge count in sync with the freshest adaptive
     * plan whenever the study route recomputes. The shell prefers the live session
     * tracker while a run is in flight; this cached value covers idle states (home,
     * stats, study-done screens).
     */
    private fun refreshSessionBadgeCount(plan: RecordsSchedulerModels.AdaptiveLoadPlan?) {
        study.studySessionBadgeCount = plan?.remaining?.coerceAtLeast(0) ?: 0
    }

    /**
     * Pre-computes the "study more new cards" availability on the background thread.
     * The study-done screens read it while rendering on main; with the caches warmed
     * here that read is a cache hit instead of dashboard/study-item queries plus a
     * scheduler count on the UI thread.
     */
    private fun warmStudyDoneAvailability() {
        withStudyLoadProbe("studyDoneAvailability") {
            study.availableStudyMoreNewCards()
        }
    }
}
