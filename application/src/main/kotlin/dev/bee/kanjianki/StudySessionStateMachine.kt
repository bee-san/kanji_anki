package dev.bee.kanjianki

import dev.bee.kanjianki.core.AppliedReviewSnapshot
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels

enum class StudySessionPhase {
    IDLE,
    LOADING,
    ACTIVE,
    SUBMITTING,
    FEEDBACK,
    ADVANCING,
    COMPLETE,
}

data class StudySessionProgressUiState(
    val targetCount: Int = 0,
    val completedCount: Int = 0,
    val movedForwardCount: Int = 0,
    val missedCount: Int = 0,
    val activeTask: Boolean = false,
) {
    init {
        require(completedCount >= 0) { "completedCount must be non-negative" }
        require(targetCount >= completedCount) { "targetCount must be at least completedCount" }
        require(movedForwardCount >= 0) { "movedForwardCount must be non-negative" }
        require(missedCount >= 0) { "missedCount must be non-negative" }
    }
}

data class StudySessionUiState(
    val phase: StudySessionPhase = StudySessionPhase.IDLE,
    val currentSession: RecordsSchedulerModels.StudySession? = null,
    val feedback: StudyAnswerFeedbackSnapshot? = null,
    val progress: StudySessionProgressUiState = StudySessionProgressUiState(),
    val routeVersion: StudyRouteVersion = StudyRouteVersion(0L),
    val sessionGeneration: StudySessionGeneration = StudySessionGeneration(0L),
    val completionEvidenceReason: StudyRouteCompletionReason? = null,
    val completionReason: StudyRouteCompletionReason? = null,
    val runtimeRevision: Long = 0L,
) {
    val sessionActive: Boolean
        get() = currentSession != null

    val routeSnapshot: StudyRouteSnapshot
        get() = StudyRouteSnapshot(
            version = routeVersion,
            sessionGeneration = sessionGeneration,
            sessionToken = currentSession?.token,
            phase = phase,
            feedback = feedback,
            progress = progress,
            completionEvidenceReason = completionEvidenceReason,
            completionReason = completionReason,
        )
}

sealed interface StudySessionEvent {
    data class SessionMounted(
        val session: RecordsSchedulerModels.StudySession?,
    ) : StudySessionEvent

    data class FeedbackChanged(
        val feedback: StudyAnswerFeedbackSnapshot?,
    ) : StudySessionEvent

    data class ProgressChanged(
        val progress: StudySessionProgressUiState,
    ) : StudySessionEvent

    data class PresentationChanged(
        val phase: StudySessionPhase,
    ) : StudySessionEvent

    data class TargetReconciled(
        val progress: StudySessionProgressUiState,
    ) : StudySessionEvent

    data class CompletionAccepted(
        val reason: StudyRouteCompletionReason,
    ) : StudySessionEvent

    data class CompletionEvidenceAccepted(
        val reason: StudyRouteCompletionReason,
    ) : StudySessionEvent

    data class TerminalPresentationRestored(
        val reason: StudyRouteCompletionReason,
    ) : StudySessionEvent

    data object RouteActionClaimed : StudySessionEvent

    data object Reset : StudySessionEvent
}

object StudySessionReducer {
    fun reduce(state: StudySessionUiState, event: StudySessionEvent): StudySessionUiState = when (event) {
        is StudySessionEvent.SessionMounted -> reduceMountedSession(state, event.session)
        is StudySessionEvent.FeedbackChanged -> reduceFeedback(state, event.feedback)
        is StudySessionEvent.ProgressChanged -> reduceProgress(state, event.progress)
        is StudySessionEvent.PresentationChanged -> reducePresentation(state, event.phase)
        is StudySessionEvent.TargetReconciled -> reduceTargetReconciliation(state, event.progress)
        is StudySessionEvent.CompletionEvidenceAccepted -> reduceCompletionEvidence(state, event.reason)
        is StudySessionEvent.CompletionAccepted -> reduceCompletion(state, event.reason)
        is StudySessionEvent.TerminalPresentationRestored -> reduceTerminalPresentationRestored(state, event.reason)
        StudySessionEvent.RouteActionClaimed -> if (state.phase == StudySessionPhase.COMPLETE) {
            state
        } else {
            state.copy(
                routeVersion = state.routeVersion.next(),
                completionEvidenceReason = null,
            )
        }
        StudySessionEvent.Reset -> StudySessionUiState(
            routeVersion = state.routeVersion.next(),
            sessionGeneration = state.sessionGeneration.next(),
        )
    }

    private fun reduceMountedSession(
        state: StudySessionUiState,
        session: RecordsSchedulerModels.StudySession?,
    ): StudySessionUiState {
        if (session?.token == state.currentSession?.token) {
            return state.copy(
                currentSession = session,
                routeVersion = state.routeVersion.next(),
                completionEvidenceReason = null,
            )
        }
        return state.copy(
            phase = phaseFor(session, null),
            currentSession = session,
            feedback = null,
            routeVersion = state.routeVersion.next(),
            sessionGeneration = state.sessionGeneration.next(),
            completionEvidenceReason = null,
            completionReason = null,
        )
    }

    private fun reduceFeedback(
        state: StudySessionUiState,
        feedback: StudyAnswerFeedbackSnapshot?,
    ): StudySessionUiState {
        if (state.phase == StudySessionPhase.COMPLETE) return state
        if (feedback != null && feedback.sessionToken != state.currentSession?.token) return state
        val phase = phaseFor(state.currentSession, feedback)
        if (feedback == state.feedback && phase == state.phase) return state
        return state.copy(
            phase = phase,
            feedback = feedback,
            routeVersion = state.routeVersion.next(),
            completionEvidenceReason = null,
        )
    }

    private fun reduceProgress(
        state: StudySessionUiState,
        progress: StudySessionProgressUiState,
    ): StudySessionUiState {
        if (progress == state.progress) return state
        val candidate = state.copy(progress = progress)
        val mustLeaveComplete = state.phase == StudySessionPhase.COMPLETE && !candidate.routeSnapshot.canComplete
        return state.copy(
            phase = if (mustLeaveComplete) phaseFor(state.currentSession, state.feedback) else state.phase,
            progress = progress,
            routeVersion = state.routeVersion.next(),
            completionEvidenceReason = null,
            completionReason = if (mustLeaveComplete) null else state.completionReason,
        )
    }

    private fun reducePresentation(
        state: StudySessionUiState,
        phase: StudySessionPhase,
    ): StudySessionUiState {
        if (phase != StudySessionPhase.LOADING || phase == state.phase) return state
        return state.copy(
            phase = phase,
            routeVersion = state.routeVersion.next(),
        )
    }

    private fun reduceTargetReconciliation(
        state: StudySessionUiState,
        progress: StudySessionProgressUiState,
    ): StudySessionUiState {
        if (progress.completedCount != progress.targetCount) return state
        if (
            progress == state.progress &&
            state.phase != StudySessionPhase.COMPLETE &&
            state.completionReason == StudyRouteCompletionReason.TARGET_RECONCILIATION
        ) {
            return state
        }
        return state.copy(
            phase = phaseFor(state.currentSession, state.feedback),
            progress = progress,
            routeVersion = state.routeVersion.next(),
            completionEvidenceReason = StudyRouteCompletionReason.TARGET_RECONCILIATION,
            completionReason = StudyRouteCompletionReason.TARGET_RECONCILIATION,
        )
    }

    private fun reduceCompletionEvidence(
        state: StudySessionUiState,
        reason: StudyRouteCompletionReason,
    ): StudySessionUiState {
        if (state.phase == StudySessionPhase.COMPLETE || !completionEvidenceAllowed(state, reason)) return state
        if (!state.routeSnapshot.canComplete || state.completionEvidenceReason == reason) return state
        return state.copy(
            routeVersion = state.routeVersion.next(),
            completionEvidenceReason = reason,
        )
    }

    private fun reduceCompletion(
        state: StudySessionUiState,
        reason: StudyRouteCompletionReason,
    ): StudySessionUiState {
        if (state.phase == StudySessionPhase.COMPLETE || state.completionEvidenceReason != reason) return state
        if (!state.routeSnapshot.canComplete) return state
        return state.copy(
            phase = StudySessionPhase.COMPLETE,
            routeVersion = state.routeVersion.next(),
            completionReason = reason,
        )
    }

    private fun reduceTerminalPresentationRestored(
        state: StudySessionUiState,
        reason: StudyRouteCompletionReason,
    ): StudySessionUiState {
        if (state.currentSession != null || !state.routeSnapshot.canComplete) return state
        if (state.routeSnapshot.isComplete && state.completionReason == reason) return state
        return state.copy(
            phase = StudySessionPhase.COMPLETE,
            routeVersion = state.routeVersion.next(),
            completionEvidenceReason = reason,
            completionReason = reason,
        )
    }

    private fun completionEvidenceAllowed(
        state: StudySessionUiState,
        reason: StudyRouteCompletionReason,
    ): Boolean = when (reason) {
        StudyRouteCompletionReason.HARD_CAP ->
            state.currentSession != null &&
                state.progress.targetCount > 0 &&
                state.phase != StudySessionPhase.IDLE
        StudyRouteCompletionReason.FOCUS_COMPLETE -> state.currentSession == null
        StudyRouteCompletionReason.NO_SESSION -> state.currentSession == null
        StudyRouteCompletionReason.TARGET_RECONCILIATION ->
            false
        StudyRouteCompletionReason.EXPLICIT_CONTINUE ->
            state.feedback?.phase == StudyAnswerFeedbackPhase.CONTINUED
        StudyRouteCompletionReason.LEARN_AHEAD_REPEAT,
        StudyRouteCompletionReason.REPAIR,
        StudyRouteCompletionReason.UNDO,
        StudyRouteCompletionReason.RESTORE,
        -> false
        StudyRouteCompletionReason.STALE_CALLBACK_DROPPED -> false
    }

    private fun phaseFor(
        session: RecordsSchedulerModels.StudySession?,
        feedback: StudyAnswerFeedbackSnapshot?,
    ): StudySessionPhase {
        if (session == null) return StudySessionPhase.IDLE
        return when (feedback?.phase) {
            StudyAnswerFeedbackPhase.SUBMITTING -> StudySessionPhase.SUBMITTING
            StudyAnswerFeedbackPhase.APPLIED -> StudySessionPhase.FEEDBACK
            StudyAnswerFeedbackPhase.CONTINUED -> StudySessionPhase.ADVANCING
            StudyAnswerFeedbackPhase.UNANSWERED, null -> StudySessionPhase.ACTIVE
        }
    }
}

class StudySessionStateMachine(
    private val onStateChanged: (StudySessionUiState) -> Unit = {},
) {
    private val routeStateLock = Any()
    private var currentState = StudySessionUiState()
    private var feedbackState: StudyAnswerFeedbackState? = null

    val tracker = StudySessionTracker(onChanged = ::publishProgress)
    val undoState = StudyUndoState(onChanged = ::publishRuntimeChange)
    private val claimedReviewTokens = HashSet<String>()
    private var activeRepair: RecordsImportModels.SimilarKanjiWritingRepair? = null
    private var activePlan: RecordsSchedulerModels.AdaptiveLoadPlan? = null
    private var answerRevealed = false
    private var hintsUsed = 0
    private var currentPracticeLevel = 0
    private var checkingWriting = false
    private var continueAllKanji = false
    private val moreNewCardKanji = ArrayList<String>()
    private var activeRecovery: StoredActiveStudyRecovery? = null
    private var recoveryRouteActive = false
    private var targetReconciliationPending = false

    fun snapshot(): StudySessionUiState = synchronized(routeStateLock) { currentState }

    fun activeSessionToken(): String? = snapshot().currentSession?.token

    fun activeSession(): RecordsSchedulerModels.StudySession? = snapshot().currentSession

    fun mountSession(session: RecordsSchedulerModels.StudySession?) {
        synchronized(routeStateLock) {
            dispatchLocked(StudySessionEvent.SessionMounted(session))
            feedbackState
                ?.takeIf { it.sessionToken == session?.token }
                ?.let(::publishFeedbackLocked)
        }
    }

    fun feedbackFor(sessionToken: String): StudyAnswerFeedbackState = synchronized(routeStateLock) {
        feedbackState?.takeIf { it.sessionToken == sessionToken }?.let { return@synchronized it }
        requireNotNull(installFeedbackLocked(StudyAnswerFeedbackState(sessionToken)))
    }

    fun feedbackFor(
        sessionToken: String,
        restored: StudyAnswerFeedbackState?,
    ): StudyAnswerFeedbackState = synchronized(routeStateLock) {
        feedbackState?.takeIf { it.sessionToken == sessionToken }?.let { return@synchronized it }
        requireNotNull(
            installFeedbackLocked(
                restored?.takeIf { it.sessionToken == sessionToken }
                    ?: StudyAnswerFeedbackState(sessionToken),
            ),
        )
    }

    fun feedbackState(): StudyAnswerFeedbackState? = synchronized(routeStateLock) { feedbackState }

    fun installFeedback(state: StudyAnswerFeedbackState?): StudyAnswerFeedbackState? =
        synchronized(routeStateLock) { installFeedbackLocked(state) }

    private fun installFeedbackLocked(state: StudyAnswerFeedbackState?): StudyAnswerFeedbackState? {
        val mountedToken = currentState.currentSession?.token
        if (state != null && mountedToken != null && state.sessionToken != mountedToken) return null
        if (feedbackState === state) {
            publishFeedbackLocked(state)
            return state
        }
        clearFeedbackLocked()
        feedbackState = state
        state?.observeChanges { publishFeedback(state) }
        dispatchLocked(StudySessionEvent.FeedbackChanged(state?.snapshot()))
        return state
    }

    fun feedbackChanged() {
        synchronized(routeStateLock) {
            publishFeedbackLocked(feedbackState)
        }
    }

    fun acceptFeedback(
        feedback: StudyAnswerFeedbackSnapshot,
        expectedGeneration: StudySessionGeneration,
        expectedVersion: StudyRouteVersion,
    ): Boolean = synchronized(routeStateLock) {
        if (!matchesRouteLocked(expectedGeneration, expectedVersion, feedback.sessionToken)) return@synchronized false
        if (currentState.phase == StudySessionPhase.COMPLETE) return@synchronized false
        if (feedback == currentState.feedback) return@synchronized false
        clearFeedbackLocked()
        val restored = StudyAnswerFeedbackState.restore(feedback)
        feedbackState = restored
        restored.observeChanges { publishFeedback(restored) }
        dispatchLocked(StudySessionEvent.FeedbackChanged(feedback))
    }

    fun showLoading() {
        dispatch(StudySessionEvent.PresentationChanged(StudySessionPhase.LOADING))
    }

    fun acceptedRouteSnapshot(): StudyRouteSnapshot = snapshot().routeSnapshot

    fun acceptTerminalSessionAbsence(expectedRoute: StudyRouteSnapshot): StudyRouteSnapshot? =
        synchronized(routeStateLock) {
            val current = currentState.routeSnapshot
            if (current != expectedRoute) return@synchronized null
            if (current.isComplete) return@synchronized current
            if (currentState.currentSession == null) return@synchronized current
            clearFeedbackLocked()
            dispatchLocked(StudySessionEvent.SessionMounted(null))
            currentState.routeSnapshot
        }

    fun reconcileRouteTarget(
        reconciledTarget: Int,
        expectedGeneration: StudySessionGeneration,
        expectedVersion: StudyRouteVersion,
    ): Boolean = synchronized(routeStateLock) {
        if (!matchesRouteLocked(expectedGeneration, expectedVersion)) return@synchronized false
        val current = currentState.progress
        if (reconciledTarget != current.completedCount || reconciledTarget == current.targetCount) {
            return@synchronized false
        }
        val trackerBefore = tracker.snapshot()
        val trackerUi = trackerBefore.toUiState()
        if (
            trackerUi.completedCount != current.completedCount ||
            trackerUi.movedForwardCount != current.movedForwardCount ||
            trackerUi.missedCount != current.missedCount ||
            trackerUi.activeTask != current.activeTask
        ) {
            return@synchronized false
        }
        val trackerProgress = when (trackerUi.targetCount) {
            current.targetCount -> tracker.reconcileTargetCountWithoutNotification(reconciledTarget)
            reconciledTarget -> trackerBefore
            else -> return@synchronized false
        }
        dispatchLocked(
            StudySessionEvent.TargetReconciled(
                trackerProgress.toUiState(),
            ),
        )
    }

    fun acceptCompletionEvidence(
        reason: StudyRouteCompletionReason,
        expectedGeneration: StudySessionGeneration,
        expectedVersion: StudyRouteVersion,
        expectedSessionToken: String?,
    ): StudyRouteSnapshot? = synchronized(routeStateLock) {
        val state = currentState
        if (
            !matchesRouteLocked(expectedGeneration, expectedVersion) ||
            expectedSessionToken != state.currentSession?.token
        ) {
            return@synchronized null
        }
        if (state.routeSnapshot.isComplete) {
            return@synchronized state.routeSnapshot.takeIf { state.completionReason == reason }
        }
        dispatchLocked(StudySessionEvent.CompletionEvidenceAccepted(reason))
        currentState.routeSnapshot.takeIf { it.completionEvidenceReason == reason }
    }

    fun completeRoute(
        reason: StudyRouteCompletionReason,
        expectedGeneration: StudySessionGeneration,
        expectedVersion: StudyRouteVersion,
        expectedSessionToken: String?,
    ): Boolean = synchronized(routeStateLock) {
        val state = currentState
        if (
            !matchesRouteLocked(expectedGeneration, expectedVersion) ||
            expectedSessionToken != state.currentSession?.token
        ) {
            return@synchronized false
        }
        if (state.routeSnapshot.isComplete) {
            state.completionReason == reason
        } else {
            dispatchLocked(StudySessionEvent.CompletionAccepted(reason))
        }
    }

    fun restoreTerminalPresentation(reason: StudyRouteCompletionReason): Boolean = synchronized(routeStateLock) {
        if (currentState.currentSession != null) {
            return@synchronized false
        }
        clearFeedbackLocked()
        val restored = StudySessionReducer.reduce(
            currentState,
            StudySessionEvent.TerminalPresentationRestored(reason),
        )
        if (restored == currentState) {
            return@synchronized restored.routeSnapshot.isComplete &&
                restored.completionReason == reason
        }
        clearFeedbackLocked()
        setStateLocked(restored)
        restored.routeSnapshot.isComplete
    }

    fun reset() {
        synchronized(routeStateLock) {
            clearFeedbackLocked()
            tracker.resetProgressWithoutNotification()
            dispatchLocked(StudySessionEvent.Reset)
        }
    }

    fun isCurrentRoute(
        sessionToken: String,
        expectedGeneration: StudySessionGeneration,
        expectedVersion: StudyRouteVersion,
    ): Boolean = synchronized(routeStateLock) {
        matchesRouteLocked(expectedGeneration, expectedVersion, sessionToken)
    }

    fun isCurrentRoute(expected: StudyRouteSnapshot): Boolean = synchronized(routeStateLock) {
        currentState.routeSnapshot == expected
    }

    fun acceptStudyLoadTracker(
        expected: StudyRouteSnapshot,
        staged: StudySessionTracker,
    ): StudyRouteSnapshot? = synchronized(routeStateLock) {
        val current = currentState.routeSnapshot
        if (!isRouteCompatibleForStudyLoad(expected, current)) {
            return@synchronized null
        }
        if (!tracker.replaceStateFrom(staged)) {
            return@synchronized null
        }
        currentState.routeSnapshot
    }

    fun acceptStudyLoadRoute(expected: StudyRouteSnapshot): StudyRouteSnapshot? = synchronized(routeStateLock) {
        currentState.routeSnapshot.takeIf { current ->
            isRouteCompatibleForStudyLoad(expected, current)
        }
    }

    private fun isRouteCompatibleForStudyLoad(
        expected: StudyRouteSnapshot,
        current: StudyRouteSnapshot,
    ): Boolean = current == expected || (
        current.phase == StudySessionPhase.LOADING &&
            current.version == expected.version.next() &&
            current.copy(phase = expected.phase, version = expected.version) == expected
    )

    fun claimCurrentRouteAction(
        sessionToken: String,
        expectedGeneration: StudySessionGeneration,
        expectedVersion: StudyRouteVersion,
    ): StudyRouteActionClaim? = synchronized(routeStateLock) {
        if (
            currentState.phase == StudySessionPhase.COMPLETE ||
            !matchesRouteLocked(expectedGeneration, expectedVersion, sessionToken)
        ) {
            return@synchronized null
        }
        dispatchLocked(StudySessionEvent.RouteActionClaimed)
        val claimed = currentState
        StudyRouteActionClaim(sessionToken, claimed.sessionGeneration, claimed.routeVersion)
    }

    fun consumeRouteAction(claim: StudyRouteActionClaim): Boolean = synchronized(routeStateLock) {
        if (
            currentState.phase == StudySessionPhase.COMPLETE ||
            !matchesRouteLocked(claim.sessionGeneration, claim.routeVersion, claim.sessionToken)
        ) {
            return@synchronized false
        }
        dispatchLocked(StudySessionEvent.RouteActionClaimed)
    }

    fun tryClaimReviewToken(token: String): Boolean = synchronized(routeStateLock) {
        token.isNotEmpty() && claimedReviewTokens.add(token)
    }

    fun releaseReviewToken(token: String) {
        synchronized(routeStateLock) {
            claimedReviewTokens.remove(token)
        }
    }

    fun acceptAppliedReview(
        snapshot: AppliedReviewSnapshot,
        label: String,
        createdAtMillis: Long,
    ): Boolean = synchronized(routeStateLock) {
        if (undoState.pending?.snapshot?.token == snapshot.token) {
            return@synchronized false
        }
        val current = currentState.currentSession
        val currentItem = current?.item
        val before = snapshot.beforeReview
        if (
            current?.token != snapshot.token ||
            currentItem?.kanji != before.kanji ||
            currentItem.answerSignature != before.answerSignature ||
            currentItem.schedulerRevision != before.schedulerRevision
        ) {
            return@synchronized false
        }
        undoState.capture(snapshot, label, createdAtMillis)
        true
    }

    fun activeRepair(): RecordsImportModels.SimilarKanjiWritingRepair? =
        synchronized(routeStateLock) { activeRepair }

    fun setActiveRepair(value: RecordsImportModels.SimilarKanjiWritingRepair?) {
        updateRuntime { activeRepair = value }
    }

    fun activePlan(): RecordsSchedulerModels.AdaptiveLoadPlan? =
        synchronized(routeStateLock) { activePlan }

    fun setActivePlan(value: RecordsSchedulerModels.AdaptiveLoadPlan?) {
        updateRuntime { activePlan = value }
    }

    fun answerRevealed(): Boolean = synchronized(routeStateLock) { answerRevealed }

    fun setAnswerRevealed(value: Boolean) {
        updateRuntime { answerRevealed = value }
    }

    fun hintsUsed(): Int = synchronized(routeStateLock) { hintsUsed }

    fun setHintsUsed(value: Int) {
        updateRuntime { hintsUsed = value.coerceAtLeast(0) }
    }

    fun incrementHintsUsed() {
        updateRuntime { hintsUsed = Math.addExact(hintsUsed, 1) }
    }

    fun currentPracticeLevel(): Int = synchronized(routeStateLock) { currentPracticeLevel }

    fun setCurrentPracticeLevel(value: Int) {
        updateRuntime { currentPracticeLevel = value.coerceAtLeast(0) }
    }

    fun checkingWriting(): Boolean = synchronized(routeStateLock) { checkingWriting }

    fun setCheckingWriting(value: Boolean) {
        updateRuntime { checkingWriting = value }
    }

    fun continueAllKanji(): Boolean = synchronized(routeStateLock) { continueAllKanji }

    fun setContinueAllKanji(value: Boolean) {
        updateRuntime { continueAllKanji = value }
    }

    fun moreNewCardKanji(): MutableList<String> = moreNewCardKanji

    fun activeRecovery(): StoredActiveStudyRecovery? = synchronized(routeStateLock) { activeRecovery }

    fun setActiveRecovery(value: StoredActiveStudyRecovery?) {
        updateRuntime { activeRecovery = value }
    }

    fun recoveryRouteActive(): Boolean = synchronized(routeStateLock) { recoveryRouteActive }

    fun setRecoveryRouteActive(value: Boolean) {
        updateRuntime { recoveryRouteActive = value }
    }

    fun targetReconciliationPending(): Boolean =
        synchronized(routeStateLock) { targetReconciliationPending }

    fun setTargetReconciliationPending(value: Boolean) {
        updateRuntime { targetReconciliationPending = value }
    }

    private fun publishFeedback(observed: StudyAnswerFeedbackState) {
        synchronized(routeStateLock) {
            publishFeedbackLocked(observed)
        }
    }

    private fun publishFeedbackLocked(observed: StudyAnswerFeedbackState?) {
        if (observed == null || feedbackState !== observed) return
        if (observed.sessionToken != currentState.currentSession?.token) return
        dispatchLocked(StudySessionEvent.FeedbackChanged(observed.snapshot()))
    }

    private fun publishProgress() {
        synchronized(routeStateLock) {
            val progress = tracker.snapshot().toUiState()
            val current = currentState.progress
            if (
                progress.targetCount < current.targetCount &&
                progress.targetCount == progress.completedCount
            ) {
                dispatchLocked(StudySessionEvent.TargetReconciled(progress))
                return@synchronized
            }
            dispatchLocked(StudySessionEvent.ProgressChanged(progress))
        }
    }

    private fun updateRuntime(update: () -> Unit) {
        synchronized(routeStateLock) {
            update()
            publishRuntimeChangeLocked()
        }
    }

    private fun publishRuntimeChange() {
        synchronized(routeStateLock) {
            publishRuntimeChangeLocked()
        }
    }

    private fun publishRuntimeChangeLocked() {
        val current = currentState
        setStateLocked(current.copy(runtimeRevision = Math.addExact(current.runtimeRevision, 1L)))
    }

    private fun StudySessionTracker.Snapshot.toUiState(): StudySessionProgressUiState =
        StudySessionProgressUiState(
            targetCount = targetCount,
            completedCount = completedCount,
            movedForwardCount = movedForwardCount,
            missedCount = missedCount,
            activeTask = activeTask,
        )

    private fun dispatch(event: StudySessionEvent): Boolean =
        synchronized(routeStateLock) { dispatchLocked(event) }

    private fun matchesRouteLocked(
        expectedGeneration: StudySessionGeneration,
        expectedVersion: StudyRouteVersion,
        expectedSessionToken: String? = null,
    ): Boolean {
        val state = currentState
        return expectedGeneration == state.sessionGeneration &&
            expectedVersion == state.routeVersion &&
            (expectedSessionToken == null || expectedSessionToken == state.currentSession?.token)
    }

    private fun dispatchLocked(event: StudySessionEvent): Boolean {
        val current = currentState
        val next = StudySessionReducer.reduce(current, event)
        if (next === current) return false
        setStateLocked(next)
        return true
    }

    private fun setStateLocked(next: StudySessionUiState) {
        currentState = next
        onStateChanged(next)
    }

    private fun clearFeedbackLocked() {
        feedbackState?.observeChanges(null)
        feedbackState = null
    }

    fun close() {
        synchronized(routeStateLock) {
            clearFeedbackLocked()
            claimedReviewTokens.clear()
        }
    }
}
