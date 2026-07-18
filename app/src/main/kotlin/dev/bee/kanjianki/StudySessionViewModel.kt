package dev.bee.kanjianki

import androidx.lifecycle.ViewModel
import dev.bee.kanjianki.core.RecordsSchedulerModels
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

internal enum class StudySessionPhase {
    IDLE,
    LOADING,
    ACTIVE,
    SUBMITTING,
    FEEDBACK,
    ADVANCING,
    COMPLETE,
}

internal data class StudySessionProgressUiState(
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

internal data class StudySessionUiState(
    val phase: StudySessionPhase = StudySessionPhase.IDLE,
    val currentSession: RecordsSchedulerModels.StudySession? = null,
    val feedback: StudyAnswerFeedbackSnapshot? = null,
    val progress: StudySessionProgressUiState = StudySessionProgressUiState(),
    val routeVersion: StudyRouteVersion = StudyRouteVersion(0L),
    val sessionGeneration: StudySessionGeneration = StudySessionGeneration(0L),
    val pendingWork: StudyRoutePendingWork = StudyRoutePendingWork.NONE,
    val completionEvidenceReason: StudyRouteCompletionReason? = null,
    val completionReason: StudyRouteCompletionReason? = null,
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
            pendingWork = pendingWork,
            completionEvidenceReason = completionEvidenceReason,
            completionReason = completionReason,
        )
}

/** One-shot work that must be handled by the currently started Activity instance. */
internal sealed interface StudySessionEffect {
    data class AutoContinue(
        val sessionToken: String,
        val sessionGeneration: StudySessionGeneration,
        val routeVersion: StudyRouteVersion,
    ) : StudySessionEffect
}

internal sealed interface StudySessionEvent {
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

    data class PendingWorkChanged(
        val pendingWork: StudyRoutePendingWork,
        val reason: StudyRouteCompletionReason?,
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

    data object RouteActionClaimed : StudySessionEvent

    data object Reset : StudySessionEvent
}

internal object StudySessionReducer {
    fun reduce(state: StudySessionUiState, event: StudySessionEvent): StudySessionUiState = when (event) {
        is StudySessionEvent.SessionMounted -> reduceMountedSession(state, event.session)
        is StudySessionEvent.FeedbackChanged -> reduceFeedback(state, event.feedback)
        is StudySessionEvent.ProgressChanged -> reduceProgress(state, event.progress)
        is StudySessionEvent.PresentationChanged -> reducePresentation(state, event.phase)
        is StudySessionEvent.PendingWorkChanged -> reducePendingWork(state, event)
        is StudySessionEvent.TargetReconciled -> reduceTargetReconciliation(state, event.progress)
        is StudySessionEvent.CompletionEvidenceAccepted -> reduceCompletionEvidence(state, event.reason)
        is StudySessionEvent.CompletionAccepted -> reduceCompletion(state, event.reason)
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
            pendingWork = StudyRoutePendingWork.NONE,
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

    private fun reducePendingWork(
        state: StudySessionUiState,
        event: StudySessionEvent.PendingWorkChanged,
    ): StudySessionUiState {
        if (state.phase == StudySessionPhase.COMPLETE) return state
        if (
            event.reason != null &&
            event.reason != StudyRouteCompletionReason.LEARN_AHEAD_REPEAT &&
            event.reason != StudyRouteCompletionReason.REPAIR &&
            event.reason != StudyRouteCompletionReason.UNDO &&
            event.reason != StudyRouteCompletionReason.RESTORE
        ) {
            return state
        }
        if (event.pendingWork == state.pendingWork && event.reason == state.completionReason) return state
        return state.copy(
            pendingWork = event.pendingWork,
            routeVersion = state.routeVersion.next(),
            completionEvidenceReason = null,
            completionReason = event.reason,
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

internal class StudySessionViewModel : ViewModel(), StudyAnswerStateStore {
    private val routeStateLock = Any()
    private val _uiState = MutableStateFlow(StudySessionUiState())
    private val effectChannel = Channel<StudySessionEffect>(Channel.BUFFERED)
    private var feedbackState: StudyAnswerFeedbackState? = null

    val uiState: StateFlow<StudySessionUiState> = _uiState.asStateFlow()
    val effects: Flow<StudySessionEffect> = effectChannel.receiveAsFlow()
    val tracker = StudySessionTracker(onChanged = ::publishProgress)

    override fun activeSessionToken(): String? = _uiState.value.currentSession?.token

    fun activeSession(): RecordsSchedulerModels.StudySession? = _uiState.value.currentSession

    fun mountSession(session: RecordsSchedulerModels.StudySession?) {
        synchronized(routeStateLock) {
            dispatchLocked(StudySessionEvent.SessionMounted(session))
            feedbackState
                ?.takeIf { it.sessionToken == session?.token }
                ?.let(::publishFeedbackLocked)
        }
    }

    override fun feedbackFor(sessionToken: String): StudyAnswerFeedbackState = synchronized(routeStateLock) {
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
        val mountedToken = _uiState.value.currentSession?.token
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

    override fun feedbackChanged() {
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
        if (_uiState.value.phase == StudySessionPhase.COMPLETE) return@synchronized false
        if (feedback == _uiState.value.feedback) return@synchronized false
        clearFeedbackLocked()
        val restored = StudyAnswerFeedbackState.restore(feedback)
        feedbackState = restored
        restored.observeChanges { publishFeedback(restored) }
        dispatchLocked(StudySessionEvent.FeedbackChanged(feedback))
    }

    fun showLoading() {
        dispatch(StudySessionEvent.PresentationChanged(StudySessionPhase.LOADING))
    }

    fun acceptedRouteSnapshot(): StudyRouteSnapshot = _uiState.value.routeSnapshot

    fun acceptTerminalSessionAbsence(expectedRoute: StudyRouteSnapshot): StudyRouteSnapshot? =
        synchronized(routeStateLock) {
            val current = _uiState.value.routeSnapshot
            if (current != expectedRoute || current.isComplete) return@synchronized null
            if (_uiState.value.currentSession == null) return@synchronized current
            clearFeedbackLocked()
            dispatchLocked(StudySessionEvent.SessionMounted(null))
            _uiState.value.routeSnapshot
        }

    fun acceptPendingWork(
        pendingWork: StudyRoutePendingWork,
        reason: StudyRouteCompletionReason?,
        expectedGeneration: StudySessionGeneration,
        expectedVersion: StudyRouteVersion,
    ): Boolean = synchronized(routeStateLock) {
        if (!matchesRouteLocked(expectedGeneration, expectedVersion)) return@synchronized false
        val state = _uiState.value
        if (state.phase == StudySessionPhase.COMPLETE || !pendingWork.hasBlockers) return@synchronized false
        dispatchLocked(
            StudySessionEvent.PendingWorkChanged(
                state.pendingWork.mergedWith(pendingWork),
                reason,
            ),
        )
    }

    fun resolvePendingWork(
        resolvedTaskKeys: Set<String>,
        expectedGeneration: StudySessionGeneration,
        expectedVersion: StudyRouteVersion,
    ): Boolean = synchronized(routeStateLock) {
        if (!matchesRouteLocked(expectedGeneration, expectedVersion)) return@synchronized false
        val state = _uiState.value
        if (state.phase == StudySessionPhase.COMPLETE) return@synchronized false
        val acceptedKeys = resolvedTaskKeys intersect state.pendingWork.taskKeys
        if (acceptedKeys.isEmpty()) return@synchronized false
        val remaining = state.pendingWork.resolving(acceptedKeys)
        dispatchLocked(
            StudySessionEvent.PendingWorkChanged(
                remaining,
                state.completionReason.takeIf { remaining.hasBlockers },
            ),
        )
    }

    fun reconcileRouteTarget(
        reconciledTarget: Int,
        expectedGeneration: StudySessionGeneration,
        expectedVersion: StudyRouteVersion,
    ): Boolean = synchronized(routeStateLock) {
        if (!matchesRouteLocked(expectedGeneration, expectedVersion)) return@synchronized false
        val current = _uiState.value.progress
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
        val state = _uiState.value
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
        _uiState.value.routeSnapshot.takeIf { it.completionEvidenceReason == reason }
    }

    fun completeRoute(
        reason: StudyRouteCompletionReason,
        expectedGeneration: StudySessionGeneration,
        expectedVersion: StudyRouteVersion,
        expectedSessionToken: String?,
    ): Boolean = synchronized(routeStateLock) {
        val state = _uiState.value
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

    fun reset() {
        synchronized(routeStateLock) {
            clearFeedbackLocked()
            tracker.resetProgressWithoutNotification()
            dispatchLocked(StudySessionEvent.Reset)
        }
    }

    fun requestAutoContinue(sessionToken: String): Boolean = synchronized(routeStateLock) {
        val state = _uiState.value
        if (state.phase == StudySessionPhase.COMPLETE || sessionToken != state.currentSession?.token) {
            return@synchronized false
        }
        effectChannel.trySend(
            StudySessionEffect.AutoContinue(
                sessionToken,
                state.sessionGeneration,
                state.routeVersion,
            ),
        ).isSuccess
    }

    fun requestAutoContinue(
        sessionToken: String,
        expectedGeneration: StudySessionGeneration,
        expectedVersion: StudyRouteVersion,
    ): Boolean = synchronized(routeStateLock) {
        if (
            _uiState.value.phase == StudySessionPhase.COMPLETE ||
            !matchesRouteLocked(expectedGeneration, expectedVersion, sessionToken)
        ) {
            return@synchronized false
        }
        effectChannel.trySend(
            StudySessionEffect.AutoContinue(
                sessionToken,
                expectedGeneration,
                expectedVersion,
            ),
        ).isSuccess
    }

    fun isCurrentRoute(
        sessionToken: String,
        expectedGeneration: StudySessionGeneration,
        expectedVersion: StudyRouteVersion,
    ): Boolean = synchronized(routeStateLock) {
        matchesRouteLocked(expectedGeneration, expectedVersion, sessionToken)
    }

    fun isCurrentRoute(expected: StudyRouteSnapshot): Boolean = synchronized(routeStateLock) {
        _uiState.value.routeSnapshot == expected
    }

    fun acceptStudyLoadTracker(
        expected: StudyRouteSnapshot,
        staged: StudySessionTracker,
    ): StudyRouteSnapshot? = synchronized(routeStateLock) {
        val current = _uiState.value.routeSnapshot
        if (!isRouteCompatibleForStudyLoad(expected, current)) {
            return@synchronized null
        }
        if (!tracker.replaceStateFrom(staged)) {
            return@synchronized null
        }
        _uiState.value.routeSnapshot
    }

    fun acceptStudyLoadRoute(expected: StudyRouteSnapshot): StudyRouteSnapshot? = synchronized(routeStateLock) {
        _uiState.value.routeSnapshot.takeIf { current ->
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
            _uiState.value.phase == StudySessionPhase.COMPLETE ||
            !matchesRouteLocked(expectedGeneration, expectedVersion, sessionToken)
        ) {
            return@synchronized null
        }
        dispatchLocked(StudySessionEvent.RouteActionClaimed)
        val claimed = _uiState.value
        StudyRouteActionClaim(sessionToken, claimed.sessionGeneration, claimed.routeVersion)
    }

    fun consumeRouteAction(claim: StudyRouteActionClaim): Boolean = synchronized(routeStateLock) {
        if (
            _uiState.value.phase == StudySessionPhase.COMPLETE ||
            !matchesRouteLocked(claim.sessionGeneration, claim.routeVersion, claim.sessionToken)
        ) {
            return@synchronized false
        }
        dispatchLocked(StudySessionEvent.RouteActionClaimed)
    }

    private fun publishFeedback(observed: StudyAnswerFeedbackState) {
        synchronized(routeStateLock) {
            publishFeedbackLocked(observed)
        }
    }

    private fun publishFeedbackLocked(observed: StudyAnswerFeedbackState?) {
        if (observed == null || feedbackState !== observed) return
        if (observed.sessionToken != _uiState.value.currentSession?.token) return
        dispatchLocked(StudySessionEvent.FeedbackChanged(observed.snapshot()))
    }

    private fun publishProgress() {
        synchronized(routeStateLock) {
            val progress = tracker.snapshot().toUiState()
            val current = _uiState.value.progress
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
        val state = _uiState.value
        return expectedGeneration == state.sessionGeneration &&
            expectedVersion == state.routeVersion &&
            (expectedSessionToken == null || expectedSessionToken == state.currentSession?.token)
    }

    private fun dispatchLocked(event: StudySessionEvent): Boolean {
        val current = _uiState.value
        val next = StudySessionReducer.reduce(current, event)
        if (next === current) return false
        _uiState.value = next
        return true
    }

    private fun clearFeedbackLocked() {
        feedbackState?.observeChanges(null)
        feedbackState = null
    }

    override fun onCleared() {
        synchronized(routeStateLock) {
            clearFeedbackLocked()
        }
        effectChannel.close()
        super.onCleared()
    }
}
