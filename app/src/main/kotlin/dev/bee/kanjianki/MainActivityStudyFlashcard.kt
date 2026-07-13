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

    fun renderComposeFlashcardSession(session: RecordsSchedulerModels.StudySession) {
        renderComposeFlashcardRoute { composeFlashcardRouteModel(session) }
    }

    private fun renderComposeFlashcardRoute(routeProvider: () -> ComposeFlashcardRouteModel) {
        activity.initializeSessionProgressTarget(activity.activeStudyPlan)
        val progress = activity.studySessionTracker.topBarProgress(activity.activeSession != null, activity.continueAllKanjiSession)
        lateinit var route: ComposeFlashcardRouteModel
        activity.composeRouteWithActionBar(
            selected = MainActivityBase.NAV_STUDY,
            studySessionActive = true,
            beforeContent = { route = routeProvider() },
            content = {
                Column {
                    StudyTopBar(
                        completed = progress.completed,
                        target = progress.target,
                        fraction = progress.fraction,
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

    private fun composeFlashcardRouteModel(session: RecordsSchedulerModels.StudySession): ComposeFlashcardRouteModel {
        resetFlashcardInteractionState()
        val feedback = activity.prepareStudyAnswerFeedback(session.token)
        val answered = feedback.snapshot().phase == StudyAnswerFeedbackPhase.SUBMITTING ||
            feedback.snapshot().phase == StudyAnswerFeedbackPhase.APPLIED
        activity.flashcardAnswerRevealed = answered
        val revealState = FlashcardRevealState(answered)
        activity.flashcardRevealState = revealState
        activity.flashcardHeroPanel = null
        activity.studyAnswerPanel = null
        val swipeFeedback = StudySwipeFeedbackState()
        activity.flashcardSwipeFeedback = swipeFeedback
        val failureCauseState = RecognitionFailureCauseState()
        activity.recognitionFailureCauseState = failureCauseState
        val heroPanel = when {
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
        val typingAnswer = if (
            StudyTaskCopy.isTypingMeaningTask(session) || StudyTaskCopy.isTypingReadingTask(session)
        ) {
            TypingAnswerState(if (answered) feedback.selectedAnswer else "").also {
                activity.typingAnswerState = it
            }
        } else {
            null
        }
        val answerPanel = flashcardAnswerPanelModel(session)
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
        val actionBarState = FlashcardActionBarState(
            answered,
            Runnable { revealFlashcardAnswer() },
            Runnable {
                if (requiresRecognitionFailureCause(session)) {
                    failureCauseState.show("button")
                } else {
                    activity.submitReview(MainActivityBase.RATING_AGAIN, false)
                }
            },
            Runnable { activity.submitReview(MainActivityBase.RATING_GOOD, false) },
        )
        activity.flashcardActionBarState = actionBarState
        return ComposeFlashcardRouteModel(
            cardModel = cardModel,
            actionBarState = actionBarState,
            swipeFeedback = swipeFeedback,
            sessionToken = session.token,
            failureCauseState = failureCauseState,
            askRecognitionFailureCause = requiresRecognitionFailureCause(session),
            onFailureCause = { cause, source ->
                activity.submitReview(
                    rating = MainActivityBase.RATING_AGAIN,
                    override = false,
                    interactionSource = source,
                    answerEvidence = recognitionFailureEvidence(session, cause),
                )
            },
        )
    }

    fun flashcardAnswerPanelModel(session: RecordsSchedulerModels.StudySession): StudyAnswerPanelModel {
        return flashcardAnswerPanelModel(activity, session)
    }

    fun meaningChoiceAnswerPanelModel(session: RecordsSchedulerModels.StudySession): StudyAnswerPanelModel {
        return meaningChoiceAnswerPanelModel(activity, session)
    }

    fun typingAnswerField(): TypingAnswerState {
        val state = TypingAnswerState()
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
        interaction.revealFlashcardAnswer()
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
            Runnable { activity.renderDetail(glyph, false, null, Runnable { renderComposeFlashcardRoute { route } }) }
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
                onTypingDone = Runnable { revealFlashcardAnswer() },
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
            onUndo = { activity.undoLastRating() },
            swipeFeedback = route.swipeFeedback,
            onReview = { source, rating ->
                if (rating == MainActivityBase.RATING_AGAIN && route.askRecognitionFailureCause) {
                    route.failureCauseState.show(source)
                    false
                } else {
                    activity.submitReview(
                        rating = rating,
                        override = false,
                        interactionSource = source,
                    )
                }
            },
            feedbackState = activity.studyAnswerFeedbackState,
            onContinue = { activity.continueAfterStudyAnswer() },
        )
    }

    private fun requiresRecognitionFailureCause(session: RecordsSchedulerModels.StudySession): Boolean {
        return session.item?.phase == RecordsBase.SchedulerPhase.REVIEW &&
            (session.taskType == dev.bee.kanjianki.core.StudyTaskTypes.KANJI_MEANING ||
                session.taskType == dev.bee.kanjianki.core.StudyTaskTypes.FONT_MEANING)
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
        val failureCauseState: RecognitionFailureCauseState,
        val askRecognitionFailureCause: Boolean,
        val onFailureCause: (FailureKind, String) -> Unit,
    )
}
