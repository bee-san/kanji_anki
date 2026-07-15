package dev.bee.kanjianki

import androidx.lifecycle.ViewModel
import dev.bee.kanjianki.core.RecordsSchedulerModels
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/** The route-level state of the current Study run. */
internal enum class StudySessionPhase {
    IDLE,
    LOADING,
    ACTIVE,
    SUBMITTING,
    FEEDBACK,
    ADVANCING,
    COMPLETE,
}

/** Immutable progress consumed by Compose and retained across configuration changes. */
internal data class StudySessionProgressUiState(
    val targetCount: Int = 0,
    val completedCount: Int = 0,
    val movedForwardCount: Int = 0,
    val missedCount: Int = 0,
    val activeTask: Boolean = false,
)

/**
 * The serializable portion of a mounted Study route. Android-owned objects such as
 * drawing views, gesture bounds and typefaces intentionally remain in the composition.
 */
internal data class StudySessionUiState(
    val phase: StudySessionPhase = StudySessionPhase.IDLE,
    val currentSession: RecordsSchedulerModels.StudySession? = null,
    val feedback: StudyAnswerFeedbackSnapshot? = null,
    val progress: StudySessionProgressUiState = StudySessionProgressUiState(),
) {
    val sessionActive: Boolean
        get() = currentSession != null
}

/** One-shot work that must be handled by the currently started Activity instance. */
internal sealed interface StudySessionEffect {
    data class AutoContinue(val sessionToken: String) : StudySessionEffect
}

/** Events are reduced separately so transition behavior is deterministic and unit-testable. */
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

    data object Reset : StudySessionEvent
}

internal object StudySessionReducer {
    fun reduce(state: StudySessionUiState, event: StudySessionEvent): StudySessionUiState = when (event) {
        is StudySessionEvent.SessionMounted -> state.copy(
            phase = phaseFor(event.session, state.feedback),
            currentSession = event.session,
        )
        is StudySessionEvent.FeedbackChanged -> state.copy(
            phase = phaseFor(state.currentSession, event.feedback),
            feedback = event.feedback,
        )
        is StudySessionEvent.ProgressChanged -> state.copy(progress = event.progress)
        is StudySessionEvent.PresentationChanged -> state.copy(phase = event.phase)
        StudySessionEvent.Reset -> StudySessionUiState()
    }

    private fun phaseFor(
        session: RecordsSchedulerModels.StudySession?,
        feedback: StudyAnswerFeedbackSnapshot?,
    ): StudySessionPhase {
        if (session == null) return StudySessionPhase.IDLE
        if (feedback?.sessionToken != session.token) return StudySessionPhase.ACTIVE
        return when (feedback.phase) {
            StudyAnswerFeedbackPhase.UNANSWERED -> StudySessionPhase.ACTIVE
            StudyAnswerFeedbackPhase.SUBMITTING -> StudySessionPhase.SUBMITTING
            StudyAnswerFeedbackPhase.APPLIED -> StudySessionPhase.FEEDBACK
            StudyAnswerFeedbackPhase.CONTINUED -> StudySessionPhase.ADVANCING
        }
    }
}

/**
 * Retained owner for the real Study session, answer gate and progress tracker.
 *
 * Persistence remains token-first and revision-CAS in the existing Study review pipeline. This
 * holder only publishes UI snapshots; it cannot advance the queue or mutate scheduler state.
 */
internal class StudySessionViewModel : ViewModel(), StudyAnswerStateStore {
    private val _uiState = MutableStateFlow(StudySessionUiState())
    val uiState: StateFlow<StudySessionUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<StudySessionEffect>(Channel.BUFFERED)
    val effects: Flow<StudySessionEffect> = effectChannel.receiveAsFlow()

    @Volatile
    private var feedbackState: StudyAnswerFeedbackState? = null

    private val progressPublicationLock = Any()

    val tracker = StudySessionTracker(onChanged = ::publishProgress)

    override fun activeSessionToken(): String? = uiState.value.currentSession?.token

    fun activeSession(): RecordsSchedulerModels.StudySession? = uiState.value.currentSession

    fun mountSession(session: RecordsSchedulerModels.StudySession?) {
        dispatch(StudySessionEvent.SessionMounted(session))
    }

    override fun feedbackFor(sessionToken: String): StudyAnswerFeedbackState {
        feedbackState?.takeIf { it.sessionToken == sessionToken }?.let { return it }
        return requireNotNull(installFeedback(StudyAnswerFeedbackState(sessionToken)))
    }

    fun feedbackFor(
        sessionToken: String,
        restored: StudyAnswerFeedbackState?,
    ): StudyAnswerFeedbackState {
        feedbackState?.takeIf { it.sessionToken == sessionToken }?.let { return it }
        return requireNotNull(
            installFeedback(
                restored?.takeIf { it.sessionToken == sessionToken }
                    ?: StudyAnswerFeedbackState(sessionToken),
            ),
        )
    }

    fun feedbackState(): StudyAnswerFeedbackState? = feedbackState

    fun installFeedback(state: StudyAnswerFeedbackState?): StudyAnswerFeedbackState? {
        if (feedbackState === state) {
            publishFeedback()
            return state
        }
        feedbackState?.observeChanges(null)
        feedbackState = state
        state?.observeChanges(::publishFeedback)
        publishFeedback()
        return state
    }

    override fun feedbackChanged() {
        publishFeedback()
    }

    fun showLoading() {
        dispatch(StudySessionEvent.PresentationChanged(StudySessionPhase.LOADING))
    }

    fun showComplete() {
        dispatch(StudySessionEvent.PresentationChanged(StudySessionPhase.COMPLETE))
    }

    fun reset() {
        feedbackState?.observeChanges(null)
        feedbackState = null
        dispatch(StudySessionEvent.Reset)
    }

    fun requestAutoContinue(sessionToken: String) {
        effectChannel.trySend(StudySessionEffect.AutoContinue(sessionToken))
    }

    private fun publishFeedback() {
        dispatch(StudySessionEvent.FeedbackChanged(feedbackState?.snapshot()))
    }

    private fun publishProgress() {
        synchronized(progressPublicationLock) {
            val snapshot = tracker.snapshot()
            dispatch(
                StudySessionEvent.ProgressChanged(
                    StudySessionProgressUiState(
                        targetCount = snapshot.targetCount,
                        completedCount = snapshot.completedCount,
                        movedForwardCount = snapshot.movedForwardCount,
                        missedCount = snapshot.missedCount,
                        activeTask = snapshot.activeTask,
                    ),
                ),
            )
        }
    }

    private fun dispatch(event: StudySessionEvent) {
        _uiState.update { state -> StudySessionReducer.reduce(state, event) }
    }

    override fun onCleared() {
        feedbackState?.observeChanges(null)
        effectChannel.close()
        super.onCleared()
    }
}
