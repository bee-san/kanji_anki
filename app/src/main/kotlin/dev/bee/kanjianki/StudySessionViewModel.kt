package dev.bee.kanjianki

import androidx.lifecycle.ViewModel
import dev.bee.kanjianki.core.AppliedReviewSnapshot
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Android lifecycle retention for the portable authoritative Study state machine. */
internal class StudySessionViewModel : ViewModel(), StudyAnswerStateStore {
    private val _uiState = MutableStateFlow(StudySessionUiState())
    private val stateMachine = StudySessionStateMachine { _uiState.value = it }

    val uiState: StateFlow<StudySessionUiState> = _uiState.asStateFlow()

    val tracker: StudySessionTracker
        get() = stateMachine.tracker

    val undoState: StudyUndoState
        get() = stateMachine.undoState

    override fun activeSessionToken(): String? = stateMachine.activeSessionToken()

    fun activeSession(): RecordsSchedulerModels.StudySession? = stateMachine.activeSession()

    fun mountSession(session: RecordsSchedulerModels.StudySession?) {
        stateMachine.mountSession(session)
    }

    override fun feedbackFor(sessionToken: String): StudyAnswerFeedbackState =
        stateMachine.feedbackFor(sessionToken)

    fun feedbackFor(
        sessionToken: String,
        restored: StudyAnswerFeedbackState?,
    ): StudyAnswerFeedbackState = stateMachine.feedbackFor(sessionToken, restored)

    fun feedbackState(): StudyAnswerFeedbackState? = stateMachine.feedbackState()

    fun installFeedback(state: StudyAnswerFeedbackState?): StudyAnswerFeedbackState? =
        stateMachine.installFeedback(state)

    override fun feedbackChanged() {
        stateMachine.feedbackChanged()
    }

    fun acceptFeedback(
        feedback: StudyAnswerFeedbackSnapshot,
        expectedGeneration: StudySessionGeneration,
        expectedVersion: StudyRouteVersion,
    ): Boolean = stateMachine.acceptFeedback(feedback, expectedGeneration, expectedVersion)

    fun showLoading() {
        stateMachine.showLoading()
    }

    fun acceptedRouteSnapshot(): StudyRouteSnapshot = stateMachine.acceptedRouteSnapshot()

    fun acceptTerminalSessionAbsence(expectedRoute: StudyRouteSnapshot): StudyRouteSnapshot? =
        stateMachine.acceptTerminalSessionAbsence(expectedRoute)

    fun reconcileRouteTarget(
        reconciledTarget: Int,
        expectedGeneration: StudySessionGeneration,
        expectedVersion: StudyRouteVersion,
    ): Boolean = stateMachine.reconcileRouteTarget(
        reconciledTarget,
        expectedGeneration,
        expectedVersion,
    )

    fun acceptCompletionEvidence(
        reason: StudyRouteCompletionReason,
        expectedGeneration: StudySessionGeneration,
        expectedVersion: StudyRouteVersion,
        expectedSessionToken: String?,
    ): StudyRouteSnapshot? = stateMachine.acceptCompletionEvidence(
        reason,
        expectedGeneration,
        expectedVersion,
        expectedSessionToken,
    )

    fun completeRoute(
        reason: StudyRouteCompletionReason,
        expectedGeneration: StudySessionGeneration,
        expectedVersion: StudyRouteVersion,
        expectedSessionToken: String?,
    ): Boolean = stateMachine.completeRoute(
        reason,
        expectedGeneration,
        expectedVersion,
        expectedSessionToken,
    )

    fun restoreTerminalPresentation(reason: StudyRouteCompletionReason): Boolean =
        stateMachine.restoreTerminalPresentation(reason)

    fun reset() {
        stateMachine.reset()
    }

    fun isCurrentRoute(
        sessionToken: String,
        expectedGeneration: StudySessionGeneration,
        expectedVersion: StudyRouteVersion,
    ): Boolean = stateMachine.isCurrentRoute(sessionToken, expectedGeneration, expectedVersion)

    fun isCurrentRoute(expected: StudyRouteSnapshot): Boolean =
        stateMachine.isCurrentRoute(expected)

    fun acceptStudyLoadTracker(
        expected: StudyRouteSnapshot,
        staged: StudySessionTracker,
    ): StudyRouteSnapshot? = stateMachine.acceptStudyLoadTracker(expected, staged)

    fun acceptStudyLoadRoute(expected: StudyRouteSnapshot): StudyRouteSnapshot? =
        stateMachine.acceptStudyLoadRoute(expected)

    fun claimCurrentRouteAction(
        sessionToken: String,
        expectedGeneration: StudySessionGeneration,
        expectedVersion: StudyRouteVersion,
    ): StudyRouteActionClaim? = stateMachine.claimCurrentRouteAction(
        sessionToken,
        expectedGeneration,
        expectedVersion,
    )

    fun consumeRouteAction(claim: StudyRouteActionClaim): Boolean =
        stateMachine.consumeRouteAction(claim)

    fun tryClaimReviewToken(token: String): Boolean = stateMachine.tryClaimReviewToken(token)

    fun releaseReviewToken(token: String) {
        stateMachine.releaseReviewToken(token)
    }

    fun acceptAppliedReview(
        snapshot: AppliedReviewSnapshot,
        label: String,
        createdAtMillis: Long,
    ): Boolean = stateMachine.acceptAppliedReview(snapshot, label, createdAtMillis)

    fun activeRepair(): RecordsImportModels.SimilarKanjiWritingRepair? = stateMachine.activeRepair()

    fun setActiveRepair(value: RecordsImportModels.SimilarKanjiWritingRepair?) {
        stateMachine.setActiveRepair(value)
    }

    fun activePlan(): RecordsSchedulerModels.AdaptiveLoadPlan? = stateMachine.activePlan()

    fun setActivePlan(value: RecordsSchedulerModels.AdaptiveLoadPlan?) {
        stateMachine.setActivePlan(value)
    }

    fun answerRevealed(): Boolean = stateMachine.answerRevealed()

    fun setAnswerRevealed(value: Boolean) {
        stateMachine.setAnswerRevealed(value)
    }

    fun hintsUsed(): Int = stateMachine.hintsUsed()

    fun setHintsUsed(value: Int) {
        stateMachine.setHintsUsed(value)
    }

    fun incrementHintsUsed() {
        stateMachine.incrementHintsUsed()
    }

    fun currentPracticeLevel(): Int = stateMachine.currentPracticeLevel()

    fun setCurrentPracticeLevel(value: Int) {
        stateMachine.setCurrentPracticeLevel(value)
    }

    fun checkingWriting(): Boolean = stateMachine.checkingWriting()

    fun setCheckingWriting(value: Boolean) {
        stateMachine.setCheckingWriting(value)
    }

    fun continueAllKanji(): Boolean = stateMachine.continueAllKanji()

    fun setContinueAllKanji(value: Boolean) {
        stateMachine.setContinueAllKanji(value)
    }

    fun moreNewCardKanji(): MutableList<String> = stateMachine.moreNewCardKanji()

    fun activeRecovery(): StoredActiveStudyRecovery? = stateMachine.activeRecovery()

    fun setActiveRecovery(value: StoredActiveStudyRecovery?) {
        stateMachine.setActiveRecovery(value)
    }

    fun recoveryRouteActive(): Boolean = stateMachine.recoveryRouteActive()

    fun setRecoveryRouteActive(value: Boolean) {
        stateMachine.setRecoveryRouteActive(value)
    }

    fun targetReconciliationPending(): Boolean = stateMachine.targetReconciliationPending()

    fun setTargetReconciliationPending(value: Boolean) {
        stateMachine.setTargetReconciliationPending(value)
    }

    override fun onCleared() {
        stateMachine.close()
        super.onCleared()
    }
}
