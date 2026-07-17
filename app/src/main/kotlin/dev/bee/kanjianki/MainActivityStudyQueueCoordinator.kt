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
    fun renderStudy(recoveryOnly: Boolean) {
        if (!recoveryOnly && !study.armContinuedStudyRecoveryForExplicitRoute()) {
            study.renderStudyRecoveryOnly()
            return
        }
        val feedback = study.studyAnswerFeedbackState
        val active = study.activeSession
        val feedbackPhase = feedback?.snapshot()?.phase
        if (!recoveryOnly && !study.preserveStudyRecoveryForHarnessRoute && active != null &&
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
            load = { withStudyLoadProbe("renderStudy.total") { computeStudyRender(recoveryOnly) } },
            render = { it() },
            traceName = "study-route",
        )
    }

    /**
     * Runs on the background executor: performs every LocalStore read/write and the
     * scheduler computation, mutates the study session state, and returns a thunk that
     * renders the resulting screen when invoked on the main thread.
     */
    private fun computeStudyRender(recoveryOnly: Boolean): () -> Unit {
        val continued = inspectContinuedRecovery(recoveryOnly)
        continued.render?.let { return it }
        val advancingRecovery = continued.marker
        if (advancingRecovery == null) {
            computeStoredRecoveryRender(recoveryOnly)?.let { return it }
        }
        val sourceSyncFinishedAt = study.store.latestSuccessfulSyncFinishedAt() ?: 0L
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
            pendingRepairOrDoneRender(plan, now, ladder, currentItems, dueRepairs, advancingRecovery)
                ?.let { return it }
            refreshSessionBadgeCount(0)
            return terminalRender(advancingRecovery) { expectedRoute ->
                study.doneActions.renderEmptyStudyQueue(expectedRoute)
            }
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
        pendingRepairOrDoneRender(seededPlan, now, ladder, seeded, dueRepairs, advancingRecovery)
            ?.let { return it }
        val allowedKanji = StudySessionFocusPolicy.allowedKanji(seededPlan, study.continueAllKanjiSession)
        val session = withStudyLoadProbe("plannedStudySession") {
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
        if (session == null) {
            warmStudyDoneAvailability()
            return terminalRender(advancingRecovery) { expectedRoute ->
                study.doneActions.renderNoStudySession(seededPlan, expectedRoute)
            }
        }
        if (session.item == null) {
            warmStudyDoneAvailability()
            return terminalRender(advancingRecovery) { expectedRoute ->
                study.doneActions.renderNoStudySession(seededPlan, expectedRoute)
            }
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
        val prepared = study.prepareSessionRender(session)
        if ((study.store.latestSuccessfulSyncFinishedAt() ?: 0L) != sourceSyncFinishedAt) {
            return { study.renderStudy() }
        }
        return {
            if (study.acceptNewActiveStudySession(
                    session,
                    StudyPromptSource.REASON_TEXT,
                    sourceSyncFinishedAt,
                    similarChoiceSignatureDigest = prepared.similarChoiceSignatureDigest,
                    advancingRecovery = advancingRecovery,
                )
            ) {
                prepared()
            } else {
                study.renderStudyRecoveryOnly()
            }
        }
    }

    private fun inspectContinuedRecovery(recoveryOnly: Boolean): ContinuedRecoveryInspection {
        if (study.preserveStudyRecoveryForHarnessRoute) return ContinuedRecoveryInspection()
        val stored = study.pendingStudyRecovery() ?: return ContinuedRecoveryInspection()
        val saved = stored.snapshot
        if (saved.feedback.phase != StudyAnswerFeedbackPhase.CONTINUED) {
            return ContinuedRecoveryInspection()
        }
        if (!stored.resumeOnOrdinaryLaunch) {
            return ContinuedRecoveryInspection(render = { study.renderHome() })
        }
        val signature = saved.answerSignature
        val valid = stored.fallbackActive == null &&
            stored.fallbackWriteEpoch == null &&
            saved.taskType != MainActivityBase.TASK_REPAIR_WRITING &&
            signature != null &&
            saved.schedulerRevision != null &&
            study.store.hasMatchingConsumedReview(
                saved.feedback.sessionToken,
                saved.kanji,
                saved.taskType,
                signature,
            )
        if (valid) {
            return ContinuedRecoveryInspection(marker = stored)
        }
        if (!study.clearStudyRecoveryIfUnchanged(stored)) {
            return ContinuedRecoveryInspection(render = { study.renderStudyRecoveryOnly() })
        }
        return if (recoveryOnly) {
            ContinuedRecoveryInspection(render = { study.renderHome() })
        } else {
            ContinuedRecoveryInspection()
        }
    }

    private fun computeStoredRecoveryRender(recoveryOnly: Boolean): (() -> Unit)? {
        if (study.preserveStudyRecoveryForHarnessRoute) return null
        val allowDormantRecovery = !recoveryOnly
        computePendingAnswerRender(allowDormantRecovery)?.let { return it }
        computeActiveSessionRender(allowDormantRecovery)?.let { return it }
        if (!recoveryOnly) return null
        return if (study.shouldResumeStudyOnOrdinaryLaunch()) {
            { study.renderStudyRecoveryOnly() }
        } else {
            { study.renderHome() }
        }
    }

    private fun computePendingAnswerRender(allowDormantRecovery: Boolean): (() -> Unit)? {
        val stored = study.pendingStudyRecovery() ?: return null
        if (!allowDormantRecovery && !stored.resumeOnOrdinaryLaunch) return null
        val saved = reconcileRepairSubmitting(stored.snapshot)
        if (saved == null) {
            study.clearStudyRecoveryIfUnchanged(stored)
            return null
        }
        val savedPhase = saved.feedback.phase
        val rows = study.store.activeDashboardRows()
        val row = rows.firstOrNull { it.kanji == saved.kanji }
        val canonicalItems = study.store.studyItemsForKanji(listOf(saved.kanji))
        val repair = saved.taskType == MainActivityBase.TASK_REPAIR_WRITING
        val consumed = !repair && study.store.hasConsumedToken(saved.feedback.sessionToken)
        val trustedLegacyApplied = isTrustedLegacyApplied(saved)
        if (needsPendingFallback(repair, consumed, trustedLegacyApplied)) {
            return computePendingFallbackRender(stored)
        }
        if (!isAppliedPendingAnswer(repair, savedPhase, consumed, trustedLegacyApplied)) {
            study.clearStudyRecoveryIfUnchanged(stored)
            return null
        }
        return computeAppliedPendingAnswerRender(stored, repair, row, canonicalItems)
    }

    private fun reconcileRepairSubmitting(
        saved: StudyPendingAnswerSnapshot,
    ): StudyPendingAnswerSnapshot? {
        if (saved.taskType != MainActivityBase.TASK_REPAIR_WRITING ||
            saved.feedback.phase != StudyAnswerFeedbackPhase.SUBMITTING
        ) {
            return saved
        }
        val repairId = saved.repairId ?: return null
        val attemptsBefore = saved.repairAttempts ?: return null
        val passed = saved.feedback.outcome == StudyAnswerOutcome.CORRECT
        if (!study.store.hasFinishedSimilarWritingRepairAttempt(
                repairId,
                saved.feedback.sessionToken,
                attemptsBefore,
                passed,
            )
        ) {
            return null
        }
        return saved.copy(
            feedback = saved.feedback.copy(phase = StudyAnswerFeedbackPhase.APPLIED),
        )
    }

    private fun isTrustedLegacyApplied(saved: StudyPendingAnswerSnapshot): Boolean =
        saved.answerSignature == null &&
            saved.schedulerRevision == null &&
            saved.feedback.phase == StudyAnswerFeedbackPhase.APPLIED

    private fun needsPendingFallback(
        repair: Boolean,
        consumed: Boolean,
        trustedLegacyApplied: Boolean,
    ): Boolean = !repair && !consumed && !trustedLegacyApplied

    private fun isAppliedPendingAnswer(
        repair: Boolean,
        phase: StudyAnswerFeedbackPhase,
        consumed: Boolean,
        trustedLegacyApplied: Boolean,
    ): Boolean = (repair && phase == StudyAnswerFeedbackPhase.APPLIED) || consumed || trustedLegacyApplied

    private fun computePendingFallbackRender(
        stored: StoredPendingStudyRecovery,
    ): (() -> Unit)? {
        val fallbackSnapshot = stored.fallbackActive
        val fallbackSession = fallbackSnapshot?.let(::restoredActiveSession)
        if (fallbackSession == null) {
            study.clearStudyRecoveryIfUnchanged(stored)
            return null
        }
        val prepared = study.prepareSessionRender(fallbackSession)
        if (!prepared.matches(fallbackSnapshot)) {
            study.clearStudyRecoveryIfUnchanged(stored)
            return null
        }
        return {
            if (study.acceptPendingFallbackStudySession(stored, fallbackSession)) {
                prepared()
            } else {
                study.renderStudyRecoveryOnly()
            }
        }
    }

    private fun computeAppliedPendingAnswerRender(
        stored: StoredPendingStudyRecovery,
        repair: Boolean,
        row: RecordsImportModels.DashboardRow?,
        canonicalItems: List<RecordsStudyModels.StudyItem>,
    ): (() -> Unit)? {
        val saved = stored.snapshot
        val savedPhase = saved.feedback.phase
        val item = if (repair) {
            pendingRepairItem(saved)
        } else {
            StudySessionRestorationPolicy.restorePendingItem(saved, canonicalItems, row, tokenConsumed = true)
        }
        if (item == null) {
            study.clearStudyRecoveryIfUnchanged(stored)
            return null
        }
        val appliedSnapshot = if (savedPhase == StudyAnswerFeedbackPhase.APPLIED) {
            saved
        } else {
            saved.copy(feedback = saved.feedback.copy(phase = StudyAnswerFeedbackPhase.APPLIED))
        }
        val session = appliedSnapshot.restoreSession(item, row)
        val prepared = study.prepareSessionRender(session)
        return {
            if (study.acceptRestoredPendingStudySession(stored, appliedSnapshot, session)) {
                study.activeSimilarWritingRepair = null
                prepared()
            } else {
                study.renderStudyRecoveryOnly()
            }
        }
    }

    private fun pendingRepairItem(saved: StudyPendingAnswerSnapshot): RecordsStudyModels.StudyItem? {
        return BridgeScheduler.withWeights(study.store.schedulerFsrsWeights())
            .newTargetedStudyItem(
                saved.kanji,
                System.currentTimeMillis(),
                study.studyLadderSettings(),
            )
    }

    private fun computeActiveSessionRender(allowDormantRecovery: Boolean): (() -> Unit)? {
        val stored = study.activeStudyRecovery() ?: return null
        if (!allowDormantRecovery && !stored.resumeOnOrdinaryLaunch) return null
        val session = restoredActiveSession(stored.snapshot)
        if (session == null) {
            study.clearStudyRecoveryIfUnchanged(stored)
            return null
        }
        val prepared = study.prepareSessionRender(session)
        if (!prepared.matches(stored.snapshot)) {
            study.clearStudyRecoveryIfUnchanged(stored)
            return null
        }
        return {
            if (study.acceptRestoredActiveStudySession(stored, session)) {
                study.activeSimilarWritingRepair = null
                prepared()
            } else {
                study.renderStudyRecoveryOnly()
            }
        }
    }

    private fun restoredActiveSession(snapshot: StudyActiveSessionSnapshot): RecordsSchedulerModels.StudySession? {
        val rows = study.store.activeDashboardRows()
        val row = rows.firstOrNull { it.kanji == snapshot.kanji }
        val items = study.store.studyItemsForKanji(listOf(snapshot.kanji))
        return StudySessionRestorationPolicy.restoreActive(
            snapshot = snapshot,
            items = items,
            row = row,
            ladder = study.studyLadderSettings(),
            latestSuccessfulSyncAtMillis = study.store.latestSuccessfulSyncFinishedAt() ?: 0L,
            tokenConsumed = study.store.hasConsumedToken(snapshot.sessionToken),
        )
    }

    fun renderStudyForKanji(kanji: String?) {
        val supersededRecoveryToken = study.studyRecoverySessionToken()
        // Same async pattern as renderStudy: the targeted-session compute does full
        // dashboard/study-item reads and queue persistence, which used to run on the
        // main thread for undo and browse-detail entry points.
        study.loadRouteAsync(
            showLoading = { study.renderStudyLoading(study.activeSession != null) },
            load = { computeStudyForKanjiRender(kanji, supersededRecoveryToken) },
            render = { it() },
            traceName = "study-kanji-route",
        )
    }

    /**
     * Background-thread compute for the targeted-kanji study entry points (undo,
     * browse detail). Returns a thunk that renders the resulting screen on main.
     */
    private fun computeStudyForKanjiRender(
        kanji: String?,
        supersededRecoveryToken: String?,
    ): () -> Unit {
        study.clearStudyModeOverrides()
        study.resetStudyRunProgress()
        study.activeSimilarWritingRepair = null
        val sourceSyncFinishedAt = study.store.latestSuccessfulSyncFinishedAt() ?: 0L
        val rows = study.store.activeDashboardRows()
        val now = System.currentTimeMillis()
        val ladder = study.studyLadderSettings()
        val currentItems = if (rows.isEmpty()) emptyList() else study.store.studyItemsForKanji(rows.map { it.kanji })
        study.activeStudyPlan = if (rows.isEmpty()) null else study.adaptivePlan(rows, currentItems, now)
        val row = study.findRow(rows, kanji ?: "")
        if (row == null) {
            return terminalRender(null) { expectedRoute ->
                study.doneActions.renderStudyForKanjiNotAvailable(expectedRoute)
            }
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
            return terminalRender(null) { expectedRoute ->
                study.doneActions.renderStudyForKanjiNotAvailable(expectedRoute)
            }
        }
        StudySessionActions.activateStudySession(
            session,
            now,
            study.store::saveStudyItem,
            study::registerStudyTaskShown,
            study::startActiveStudyTask
        )
        val prepared = study.prepareSessionRender(session)
        if ((study.store.latestSuccessfulSyncFinishedAt() ?: 0L) != sourceSyncFinishedAt) {
            return { study.renderStudyForKanji(kanji) }
        }
        return {
            study.acceptNewActiveStudySession(
                session,
                StudyPromptSource.PRIMARY_MEANING,
                sourceSyncFinishedAt,
                supersededRecoveryToken,
                prepared.similarChoiceSignatureDigest,
            )
            prepared()
        }
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
        advancingRecovery: StoredPendingStudyRecovery?,
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
            // Use the same background preparation as every other session so the
            // current local mnemonic is loaded alongside dictionary/stroke assets.
            val render = study.prepareSessionRender(session).render
            return nonRestorableSessionRender(advancingRecovery, session, render)
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
                return terminalRender(advancingRecovery) { expectedRoute ->
                    study.doneActions.renderStudyRunDone(plan, expectedRoute)
                }
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
        if (study.recoveredStudyRunNeedsTargetReconciliation) {
            val reconciledTarget = recoveredStudyRunTarget(
                study.studySessionTracker.targetCount(),
                study.studySessionTracker.completedCount(),
                studyNowCount,
            )
            if (reconciledTarget != study.studySessionTracker.targetCount()) {
                study.studySessionTracker.setTargetCount(reconciledTarget)
            }
            study.recoveredStudyRunNeedsTargetReconciliation = false
        }
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

    private fun nonRestorableSessionRender(
        advancingRecovery: StoredPendingStudyRecovery?,
        session: RecordsSchedulerModels.StudySession,
        render: () -> Unit,
    ): () -> Unit = {
        if (advancingRecovery == null || study.clearAdvancingStudyRecovery(advancingRecovery, session)) {
            render()
        } else {
            study.renderStudyRecoveryOnly()
        }
    }

    private fun terminalRender(
        advancingRecovery: StoredPendingStudyRecovery?,
        render: (StudyRouteSnapshot) -> Unit,
    ): () -> Unit {
        val expectedRoute = study.studySessionViewModel.acceptedRouteSnapshot()
        return {
            if (study.studySessionViewModel.isCurrentRoute(expectedRoute)) {
                if (advancingRecovery == null) {
                    render(expectedRoute)
                } else if (study.clearAdvancingStudyRecovery(advancingRecovery, null)) {
                    render(study.studySessionViewModel.acceptedRouteSnapshot())
                } else {
                    study.renderStudyRecoveryOnly()
                }
            }
        }
    }
}

private data class ContinuedRecoveryInspection(
    val marker: StoredPendingStudyRecovery? = null,
    val render: (() -> Unit)? = null,
)

internal fun recoveredStudyRunTarget(currentTarget: Int, completed: Int, selectableRemaining: Int): Int =
    maxOf(currentTarget, completed + selectableRemaining)
