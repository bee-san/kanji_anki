package dev.bee.kanjianki

import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyLadderRules
import dev.bee.kanjianki.core.StudySessionFocusPolicy
import dev.bee.kanjianki.core.StudyNowCountPolicy
import dev.bee.kanjianki.core.StudyTextCopy

internal class MainActivityStudyQueueCoordinator(private val study: MainActivityStudy) {
    fun renderStudy() {
        val feedback = study.studyAnswerFeedbackState
        val active = study.activeSession
        val feedbackPhase = feedback?.snapshot()?.phase
        if (active != null &&
            feedback?.sessionToken == active.token &&
            (feedbackPhase == StudyAnswerFeedbackPhase.SUBMITTING || feedbackPhase == StudyAnswerFeedbackPhase.APPLIED)
        ) {
            study.loadRouteAsync(
                showLoading = { study.renderStudyLoading(true) },
                load = { study.prepareSessionRender(active) },
                render = { it() },
                traceName = "study-pending-answer-route",
            )
            return
        }
        // Load the Study route through the same async pattern as Home/Settings: all the
        // LocalStore reads/writes and scheduler work run on the background executor, and
        // only the returned render thunk runs on the main thread.
        study.loadRouteAsync(
            showLoading = { study.renderStudyLoading(study.activeSession != null) },
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
        computePendingAnswerRender()?.let { return it }
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
        val dueRepairs = withStudyLoadProbe("dueSimilarWritingRepairs") {
            if (ladder.isEnabled(RecordsBase.LadderRung.WRITE_KANJI)) {
                study.store.dueSimilarWritingRepairs(now)
            } else {
                emptyList()
            }
        }
        if (rows.isEmpty()) {
            initializeSessionTarget(0)
            refreshSessionBadgeCount(studyNowCount(0, dueRepairs))
            pendingRepairOrDoneRender(plan, now, ladder, currentItems, dueRepairs)?.let { return it }
            refreshSessionBadgeCount(0)
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
        val settings = withStudyLoadProbe("settings") { study.settings() }
        val studyAheadMillis = withStudyLoadProbe("studyAheadMillis") { study.studyAheadMillis() }
        val scheduler = withStudyLoadProbe("scheduler") {
            BridgeScheduler.withWeights(study.store.schedulerFsrsWeights())
        }
        val studyItemCount = withStudyLoadProbe("studyNowCount") {
            StudyNowCountPolicy.countSeeded(
                StudyNowCountPolicy.SeededCountRequest(
                    seededItems = seeded,
                    rows = rows,
                    settings = settings,
                    selection = StudyNowCountPolicy.SelectionContext(
                        nowMillis = now,
                        studyAheadMillis = studyAheadMillis,
                        plan = seededPlan,
                        continueAllKanjiSession = study.continueAllKanjiSession,
                        ladder = ladder,
                    ),
                ),
            )
        }
        refreshSessionBadgeCount(studyNowCount(studyItemCount, dueRepairs))
        initializeSessionTarget(studyItemCount)
        pendingRepairOrDoneRender(seededPlan, now, ladder, seeded, dueRepairs)?.let { return it }
        val allowedKanji = StudySessionFocusPolicy.allowedKanji(seededPlan, study.continueAllKanjiSession)
        study.activeSession = withStudyLoadProbe("plannedStudySession") {
            StudySessionActions.plannedStudySession(
                scheduler,
                study.studySessionTracker,
                seeded,
                rows,
                now,
                studyAheadMillis,
                allowedKanji,
                settings,
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

    private fun computePendingAnswerRender(): (() -> Unit)? {
        val saved = study.pendingStudyAnswerSnapshot() ?: return null
        val savedPhase = saved.feedback.phase
        val applied = when (savedPhase) {
            StudyAnswerFeedbackPhase.APPLIED -> true
            StudyAnswerFeedbackPhase.SUBMITTING -> study.store.hasConsumedToken(saved.feedback.sessionToken)
            StudyAnswerFeedbackPhase.UNANSWERED,
            StudyAnswerFeedbackPhase.CONTINUED -> false
        }
        if (!applied) {
            study.clearPendingStudyAnswer()
            return null
        }
        val rows = study.store.activeDashboardRows()
        val row = rows.firstOrNull { it.kanji == saved.kanji }
        val item = study.store.studyItemsForKanji(listOf(saved.kanji))
            .firstOrNull { it.kanji == saved.kanji }
        if (item == null) {
            study.clearPendingStudyAnswer()
            return null
        }
        val appliedSnapshot = if (savedPhase == StudyAnswerFeedbackPhase.APPLIED) {
            saved
        } else {
            saved.copy(feedback = saved.feedback.copy(phase = StudyAnswerFeedbackPhase.APPLIED))
        }
        val session = appliedSnapshot.restoreSession(item, row)
        study.activeSession = session
        study.activeSimilarWritingRepair = null
        study.restorePendingStudyAnswer(appliedSnapshot)
        return study.prepareSessionRender(session)
    }

    fun renderStudyForKanji(kanji: String?) {
        // Same async pattern as renderStudy: the targeted-session compute does full
        // dashboard/study-item reads and queue persistence, which used to run on the
        // main thread for undo and browse-detail entry points.
        study.loadRouteAsync(
            showLoading = { study.renderStudyLoading(study.activeSession != null) },
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
        val settings = study.settings()
        val scheduler = BridgeScheduler.withWeights(study.store.schedulerFsrsWeights())
        val generalStudyItemCount = StudyNowCountPolicy.countSeeded(
            StudyNowCountPolicy.SeededCountRequest(
                seededItems = seeded,
                rows = rows,
                settings = settings,
                selection = StudyNowCountPolicy.SelectionContext(
                    nowMillis = now,
                    studyAheadMillis = study.studyAheadMillis(),
                    plan = study.activeStudyPlan,
                    continueAllKanjiSession = study.continueAllKanjiSession,
                    ladder = ladder,
                ),
            ),
        )
        val dueRepairs = if (ladder.isEnabled(RecordsBase.LadderRung.WRITE_KANJI)) {
            study.store.dueSimilarWritingRepairs(now)
        } else {
            emptyList()
        }
        refreshSessionBadgeCount(studyNowCount(generalStudyItemCount, dueRepairs))
        val session = scheduler.targetedSession(
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
        items: List<RecordsStudyModels.StudyItem>,
        dueRepairs: List<RecordsImportModels.SimilarKanjiWritingRepair>,
    ): (() -> Unit)? {
        for (repair in dueRepairs) {
            study.studySessionTracker.includePendingTask(study.similarRepairProgressKey(repair))
        }
        val repair = dueRepairs.firstOrNull()
        if (repair != null) {
            val active = StudyRepairActions.activateSimilarWritingRepair(
                repair,
                now,
                study.store::saveSimilarWritingRepair,
            )
            val activeRepair = active.repair
            study.activeSimilarWritingRepair = activeRepair
            val item = BridgeScheduler.withWeights(study.store.schedulerFsrsWeights())
                .newTargetedStudyItem(activeRepair.repairKanji, now, ladder)
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
        if (study.studySessionTracker.atHardCap(study.continueAllKanjiSession)) {
            // PS1 learn-ahead: do not declare the run done while this session's own
            // learning-step repeats are due within the learn-ahead horizon. Falling
            // through lets plannedStudySession re-serve the earliest such repeat, so
            // a "finished" session keeps practicing its own cards until they graduate
            // instead of abandoning them to resurface on the home screen minutes later.
            val repeatHorizonMillis = maxOf(study.studyAheadMillis(), StudyLadderRules.LEARN_AHEAD_MILLIS)
            val pendingRepeats = study.studySessionTracker.dueCompletedLearningRepeatTaskKeys(
                items,
                now + repeatHorizonMillis,
            )
            if (pendingRepeats.isEmpty()) {
                refreshSessionBadgeCount(0)
                warmStudyDoneAvailability()
                return { study.doneActions.renderStudyRunDone(plan) }
            }
        }
        return null
    }

    private fun studyNowCount(
        studyItemCount: Int,
        dueRepairs: List<RecordsImportModels.SimilarKanjiWritingRepair>,
    ): Int {
        return StudyNowCountPolicy.includingAdditionalTaskKeys(
            studyItemCount,
            dueRepairs.map(study::similarRepairProgressKey),
        )
    }

    /** Initializes a new run from the tasks the selector can really serve. */
    private fun initializeSessionTarget(studyNowCount: Int) {
        if (study.studySessionTracker.targetCount() <= 0 && study.studySessionTracker.completedCount() == 0) {
            study.studySessionTracker.setTargetCount(studyNowCount)
        }
    }

    /**
     * Keeps the shell's cached Study-badge count in sync with the freshest adaptive
     * plan whenever the study route recomputes. The shell prefers the live session
     * tracker while a run is in flight; this cached value covers idle states (home,
     * stats, study-done screens).
     */
    private fun refreshSessionBadgeCount(studyNowCount: Int) {
        study.studySessionBadgeCount = studyNowCount.coerceAtLeast(0)
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
