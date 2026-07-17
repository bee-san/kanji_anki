package dev.bee.kanjianki

import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.AnswerEvidence
import dev.bee.kanjianki.core.CoreSkill
import dev.bee.kanjianki.core.EvidenceSource
import dev.bee.kanjianki.core.FailureKind
import dev.bee.kanjianki.core.PresentationVariant
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.StudyTaskCopy
import dev.bee.kanjianki.core.StudyTextCopy

internal class MainActivityStudyFlashcard(private val activity: MainActivityStudy) {
    private val interaction = MainActivityStudyFlashcardInteraction(activity)

    fun renderComposeFlashcardSession(
        session: RecordsSchedulerModels.StudySession,
        mnemonic: StudyAnswerMnemonicModel? = null,
    ) {
        renderComposeFlashcardRoute { composeFlashcardRouteModel(session, mnemonic) }
    }

    private fun renderComposeFlashcardRoute(routeProvider: () -> ComposeFlashcardRouteModel) {
        val routeSnapshot = activity.studySessionViewModel.acceptedRouteSnapshot()
        lateinit var route: ComposeFlashcardRouteModel
        activity.composeRouteWithActionBar(
            selected = MainActivityBase.NAV_STUDY,
            studySessionActive = true,
            beforeContent = { route = routeProvider() },
            content = {
                Column {
                    StudyTopBar(
                        routeSnapshot = routeSnapshot,
                        onClose = activity::renderHome,
                        onSettings = activity::renderSettings,
                    )
                    ComposeFlashcardCard(route)
                }
                RecognitionFailureCauseDialog(route.failureCauseState, route.onFailureCause)
            },
            actionBar = { ComposeFlashcardActionBar(route) },
        )
    }

    fun resetFlashcardSession() {
        resetFlashcardInteractionState()
    }

    private fun resetFlashcardInteractionState() {
        MainActivityStudyInteractionReset.resetFlashcard(activity)
    }

    private fun composeFlashcardRouteModel(
        session: RecordsSchedulerModels.StudySession,
        mnemonic: StudyAnswerMnemonicModel?,
    ): ComposeFlashcardRouteModel {
        resetFlashcardInteractionState()
        val feedback = activity.prepareStudyAnswerFeedback(session.token)
        val answered = isAnswered(feedback.snapshot().phase)
        val typingTask = isTypingTask(session)
        val activeUiRecovery = activity.activeStudyUiRecovery(session.token)
        val restoredUi = activeUiRecovery?.snapshot
        val revealed = answered || (!typingTask && restoredUi?.revealed == true)
        activity.flashcardAnswerRevealed = revealed
        val revealState = FlashcardRevealState(revealed)
        activity.flashcardRevealState = revealState
        activity.flashcardHeroPanel = null
        activity.studyAnswerPanel = null
        val swipeFeedback = StudySwipeFeedbackState()
        activity.flashcardSwipeFeedback = swipeFeedback
        val failureCauseState = RecognitionFailureCauseState()
        activity.recognitionFailureCauseState = failureCauseState
        val heroPanel = flashcardHeroPanelModel(session)
        val typingAnswer = buildTypingAnswerState(typingTask, answered, feedback, activeUiRecovery)
        val answerPanel = flashcardAnswerPanelModel(session, mnemonic)
        val cardModel = FlashcardCardModel(
            FlashcardPromptHeaderModel(
                StudyTaskCopy.studyModeLabel(session),
                StudyTextCopy.heroQuestion(session),
            ),
            heroPanel,
            typingAnswer,
            answerPanel,
            revealState,
            typingReading = StudyTaskCopy.isTypingReadingTask(session),
        )
        val actionBarState = buildFlashcardActionBarState(
            session,
            activeUiRecovery,
            failureCauseState,
            revealed,
        )
        activity.flashcardActionBarState = actionBarState
        return ComposeFlashcardRouteModel(
            cardModel = cardModel,
            actionBarState = actionBarState,
            swipeFeedback = swipeFeedback,
            sessionToken = session.token,
            activeRecovery = activeUiRecovery,
            failureCauseState = failureCauseState,
            onReview = { source, rating ->
                handleReviewAction(session, activeUiRecovery, failureCauseState, source, rating)
            },
            onFailureCause = { cause, source ->
                submitReviewForRoute(
                    session = session,
                    expectedRecovery = activeUiRecovery,
                    rating = MainActivityBase.RATING_AGAIN,
                    interactionSource = source,
                    answerEvidence = recognitionFailureEvidence(session, cause),
                )
            },
        )
    }

    private fun isAnswered(phase: StudyAnswerFeedbackPhase): Boolean =
        phase == StudyAnswerFeedbackPhase.SUBMITTING || phase == StudyAnswerFeedbackPhase.APPLIED

    private fun isTypingTask(session: RecordsSchedulerModels.StudySession): Boolean =
        StudyTaskCopy.isTypingMeaningTask(session) || StudyTaskCopy.isTypingReadingTask(session)

    private fun flashcardHeroPanelModel(
        session: RecordsSchedulerModels.StudySession,
    ): FlashcardHeroPanelModel = when {
        // sentence_reading (Goal 80): the front is the mined sentence. It is
        // longer than a word, so it renders well below the 116sp kanji hero
        // and the 44sp word_reading hero — 28sp keeps a typical sentence on
        // screen. When no sentence example exists sentencePrompt falls back
        // to the plain word.
        StudyTaskCopy.isSentenceReadingTask(session) -> FlashcardHeroPanelModel(
            StudyTextCopy.sentencePrompt(session),
            KaniUiTokens.StudyQuestionTextSizeSp,
            Typeface.DEFAULT,
        )
        StudyTaskCopy.isWordReadingTask(session) -> FlashcardHeroPanelModel(
            StudyTextCopy.wordPrompt(session),
            KaniUiTokens.StudyWordHeroTextSizeSp,
            Typeface.DEFAULT,
        )
        else -> FlashcardHeroPanelModel(
            session.item?.kanji ?: "",
            KaniUiTokens.StudyFrontHeroTextSizeSp,
            if (StudyTaskCopy.isFontRecognitionTask(session)) {
                StudyFontVariants.deterministic(
                    activity,
                    session.item?.kanji,
                    session.item?.kanjiMeaningMemory?.totalReviews ?: 0,
                )
            } else {
                Typeface.DEFAULT
            },
        )
    }

    private fun buildTypingAnswerState(
        typingTask: Boolean,
        answered: Boolean,
        feedback: StudyAnswerFeedbackState,
        activeRecovery: StoredActiveStudyRecovery?,
    ): TypingAnswerState? {
        if (!typingTask) return null
        val initialText = if (answered) feedback.selectedAnswer else activeRecovery?.snapshot?.typedDraft.orEmpty()
        return TypingAnswerState(initialText).also { state ->
            activeRecovery?.let { expected ->
                state.onTextChanged = { value -> activity.persistActiveStudyTypedDraft(expected, value) }
            }
            activity.typingAnswerState = state
        }
    }

    private fun buildFlashcardActionBarState(
        session: RecordsSchedulerModels.StudySession,
        activeRecovery: StoredActiveStudyRecovery?,
        failureCauseState: RecognitionFailureCauseState,
        revealed: Boolean,
    ): FlashcardActionBarState = FlashcardActionBarState(
        revealed,
        Runnable { interaction.revealFlashcardAnswer(session.token, activeRecovery) },
        Runnable { handleFailAction(session, activeRecovery, failureCauseState) },
        Runnable { submitReviewForRoute(session, activeRecovery, MainActivityBase.RATING_GOOD) },
    )

    private fun handleFailAction(
        session: RecordsSchedulerModels.StudySession,
        activeRecovery: StoredActiveStudyRecovery?,
        failureCauseState: RecognitionFailureCauseState,
    ) {
        if (!activity.matchesUngradedStudyRoute(session.token, activeRecovery)) return
        if (requiresRecognitionFailureCause(session)) {
            failureCauseState.show("button")
        } else {
            submitReviewForRoute(session, activeRecovery, MainActivityBase.RATING_AGAIN)
        }
    }

    private fun handleReviewAction(
        session: RecordsSchedulerModels.StudySession,
        activeRecovery: StoredActiveStudyRecovery?,
        failureCauseState: RecognitionFailureCauseState,
        source: String,
        rating: String,
    ): Boolean {
        if (rating != MainActivityBase.RATING_AGAIN || !requiresRecognitionFailureCause(session)) {
            return submitReviewForRoute(session, activeRecovery, rating, interactionSource = source)
        }
        if (!activity.matchesUngradedStudyRoute(session.token, activeRecovery)) return false
        failureCauseState.show(source)
        return false
    }

    fun flashcardAnswerPanelModel(
        session: RecordsSchedulerModels.StudySession,
        mnemonic: StudyAnswerMnemonicModel? = null,
    ): StudyAnswerPanelModel {
        return flashcardAnswerPanelModel(activity, session, mnemonic)
    }

    fun meaningChoiceAnswerPanelModel(
        session: RecordsSchedulerModels.StudySession,
        mnemonic: StudyAnswerMnemonicModel? = null,
    ): StudyAnswerPanelModel {
        return meaningChoiceAnswerPanelModel(activity, session, mnemonic)
    }

    fun typingAnswerField(): TypingAnswerState {
        val activeUiRecovery = activity.activeSession?.token?.let(activity::activeStudyUiRecovery)
        val state = TypingAnswerState().also {
            activeUiRecovery?.let { expected ->
                it.onTextChanged = { value -> activity.persistActiveStudyTypedDraft(expected, value) }
            }
        }
        activity.typingAnswerState = state
        return state
    }

    fun fontResource(fontRes: Int, fallback: Typeface): Typeface {
        return try {
            activity.resources.getFont(fontRes)
        } catch (error: RuntimeException) {
            fallback
        }
    }

    fun buildFlashcardActionBar(revealed: Boolean) {
        interaction.buildFlashcardActionBar(revealed)
    }

    fun revealFlashcardAnswer() {
        val sessionToken = activity.activeSession?.token
        val activeUiRecovery = sessionToken?.let(activity::activeStudyUiRecovery)
        interaction.revealFlashcardAnswer(sessionToken, activeUiRecovery)
    }

    fun expandFlashcardForAnswer() {
        interaction.expandFlashcardForAnswer()
    }

    fun handleFlashcardGesture(event: MotionEvent): Boolean {
        return interaction.handleFlashcardGesture(event)
    }

    fun handleFlashcardRelease(event: MotionEvent): Boolean {
        return interaction.handleFlashcardRelease(event)
    }

    fun isTouchInsideView(view: View?, event: MotionEvent): Boolean {
        return interaction.isTouchInsideView(view, event)
    }

    @Composable
    private fun ComposeFlashcardCard(route: ComposeFlashcardRouteModel) {
        val browseAction = route.cardModel.answerPanel.glyph.takeIf { it.isNotBlank() }?.let { glyph ->
            Runnable {
                if (activity.matchesMountedStudyRoute(route.sessionToken, route.activeRecovery)) {
                    activity.renderDetail(
                        glyph,
                        false,
                        null,
                        Runnable {
                            if (activity.matchesMountedStudyRoute(route.sessionToken, route.activeRecovery)) {
                                renderComposeFlashcardRoute { route }
                            }
                        },
                    )
                }
            }
        }
        StudyCardEnterTransition(cardToken = route.sessionToken) {
            FlashcardCard(
                model = route.cardModel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp)
                    .onGloballyPositioned { coordinates ->
                        val position = coordinates.positionInWindow()
                        val size = coordinates.size
                        activity.setFlashcardGestureBounds(
                            position.x,
                            position.y,
                            position.x + size.width,
                            position.y + size.height,
                        )
                    },
                onTypingDone = route.actionBarState.onReveal,
                onBrowseAction = browseAction,
                swipeFeedback = route.swipeFeedback,
            )
        }
    }

    @Composable
    private fun ComposeFlashcardActionBar(route: ComposeFlashcardRouteModel) {
        val undoMessage = activity.studyUndoState.undoMessageOrNull()
        StudyFlashcardActionBar(
            revealed = route.actionBarState.revealed,
            onReveal = { route.actionBarState.onReveal.run() },
            onFail = { route.actionBarState.onFail.run() },
            onPass = { route.actionBarState.onPass.run() },
            undoMessage = undoMessage,
            onUndo = {
                if (activity.matchesMountedStudyRoute(route.sessionToken, route.activeRecovery)) {
                    activity.undoLastRating()
                }
            },
            swipeFeedback = route.swipeFeedback,
            onReview = route.onReview,
            feedbackState = activity.studyAnswerFeedbackState,
            onContinue = {
                activity.matchesMountedStudyRoute(route.sessionToken, route.activeRecovery) &&
                    activity.continueAfterStudyAnswer()
            },
        )
    }

    private fun requiresRecognitionFailureCause(session: RecordsSchedulerModels.StudySession): Boolean {
        return session.item?.phase == RecordsBase.SchedulerPhase.REVIEW &&
            (session.taskType == dev.bee.kanjianki.core.StudyTaskTypes.KANJI_MEANING ||
                session.taskType == dev.bee.kanjianki.core.StudyTaskTypes.FONT_MEANING)
    }

    private fun submitReviewForRoute(
        session: RecordsSchedulerModels.StudySession,
        expectedRecovery: StoredActiveStudyRecovery?,
        rating: String,
        interactionSource: String = "review-action",
        answerEvidence: AnswerEvidence? = null,
    ): Boolean {
        if (!activity.matchesUngradedStudyRoute(session.token, expectedRecovery)) return false
        // Self-graded: the user rated after seeing the flipside, so the
        // applied write auto-advances instead of waiting for a Continue tap.
        return activity.submitReview(
            rating = rating,
            override = false,
            interactionSource = interactionSource,
            answerEvidence = answerEvidence,
            autoContinue = true,
        )
    }

    private fun recognitionFailureEvidence(
        session: RecordsSchedulerModels.StudySession,
        cause: FailureKind,
    ): AnswerEvidence = AnswerEvidence(
        coreSkill = CoreSkill.RECOGNITION,
        failureKind = cause,
        evidenceSource = EvidenceSource.SELF_REPORT,
        presentationVariant = if (StudyTaskCopy.isFontRecognitionTask(session)) {
            PresentationVariant.FONT_GLYPH
        } else {
            PresentationVariant.STANDARD_GLYPH
        },
        renderedExpression = session.item?.kanji.orEmpty(),
    )

    private data class ComposeFlashcardRouteModel(
        val cardModel: FlashcardCardModel,
        val actionBarState: FlashcardActionBarState,
        val swipeFeedback: StudySwipeFeedbackState,
        val sessionToken: String,
        val activeRecovery: StoredActiveStudyRecovery?,
        val failureCauseState: RecognitionFailureCauseState,
        val onReview: (String, String) -> Boolean,
        val onFailureCause: (FailureKind, String) -> Unit,
    )
}
