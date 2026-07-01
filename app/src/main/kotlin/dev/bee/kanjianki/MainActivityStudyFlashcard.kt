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
        val revealState = FlashcardRevealState(false)
        activity.flashcardRevealState = revealState
        activity.flashcardHeroPanel = null
        activity.studyAnswerPanel = null
        val heroPanel = FlashcardHeroPanelModel(
            if (StudyTaskCopy.isWordReadingTask(session)) StudyTextCopy.wordPrompt(session) else session.item?.kanji ?: "",
            if (StudyTaskCopy.isWordReadingTask(session)) 44 else 116,
            if (StudyTaskCopy.isFontRecognitionTask(session)) StudyFontVariants.random(activity) else Typeface.DEFAULT
        )
        val typingAnswer = if (StudyTaskCopy.isTypingMeaningTask(session)) {
            typingAnswerField()
        } else {
            null
        }
        val answerPanel = flashcardAnswerPanelModel(session)
        val cardModel = FlashcardCardModel(
            FlashcardPromptHeaderModel(
                StudyTaskCopy.studyModeLabel(session),
                StudyTaskCopy.flashcardTitle(session),
                StudyTextCopy.heroQuestion(session),
                "Answer hidden until reveal",
                activity.studyReasonLine(session)
            ),
            heroPanel,
            typingAnswer,
            answerPanel,
            revealState
        )
        val actionBarState = FlashcardActionBarState(
            false,
            Runnable { revealFlashcardAnswer() },
            Runnable { activity.submitReview(MainActivityBase.RATING_AGAIN, false) },
            Runnable { activity.submitReview(MainActivityBase.RATING_GOOD, false) },
        )
        activity.flashcardActionBarState = actionBarState
        return ComposeFlashcardRouteModel(cardModel, actionBarState)
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
                    AppTimingDiagnostics.markStudyCardUsable()
                },
            onTypingDone = Runnable { revealFlashcardAnswer() },
            onBrowseAction = browseAction,
        )
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
        )
    }

    private data class ComposeFlashcardRouteModel(
        val cardModel: FlashcardCardModel,
        val actionBarState: FlashcardActionBarState,
    )
}
