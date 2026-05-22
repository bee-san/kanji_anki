package dev.bee.kanjianki

import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.StudyTaskCopy
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.core.study.HintState

internal class MainActivityStudyFlashcard(private val activity: MainActivityStudy) {
    private val interaction = MainActivityStudyFlashcardInteraction(activity)

    fun renderComposeFlashcardSession(session: RecordsSchedulerModels.StudySession) {
        lateinit var route: ComposeFlashcardRouteModel
        activity.initializeSessionProgressTarget(activity.activeStudyPlan)
        val progress = activity.studySessionTracker.topBarProgress(activity.activeSession != null, activity.continueAllKanjiSession)
        activity.composeRouteWithActionBar(
            selected = MainActivityBase.NAV_STUDY,
            beforeContent = { route = composeFlashcardRouteModel(session) },
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
        activity.prepareStudyContent(activity.activeStudyPlan, true)
        resetFlashcardInteractionState()
    }

    private fun resetFlashcardInteractionState() {
        activity.activeSimilarWritingRepair = null
        activity.activeAnalysis = null
        activity.checkingWriting = false
        activity.flashcardAnswerRevealed = false
        activity.flashcardTouchTracking = false
        activity.typingAnswerState = null
        activity.hintsUsed = 0
        activity.setHintState(HintState.initial())
        activity.drawingPad = null
        activity.flashcardHeroPanel = null
        activity.hideStudyActionBar()
    }

    private fun composeFlashcardRouteModel(session: RecordsSchedulerModels.StudySession): ComposeFlashcardRouteModel {
        resetFlashcardInteractionState()
        val card = recognitionHeroCard(session)
        activity.flashcardCard = card
        activity.flashcardGestureArea = card
        val actionBarState = FlashcardActionBarState(
            false,
            Runnable { revealFlashcardAnswer() },
            Runnable { activity.submitReview(MainActivityBase.RATING_AGAIN, false) },
            Runnable { activity.submitReview(MainActivityBase.RATING_GOOD, false) },
        )
        activity.flashcardActionBarState = actionBarState
        return ComposeFlashcardRouteModel(card, actionBarState)
    }

    fun recognitionHeroCard(session: RecordsSchedulerModels.StudySession): View {
        val revealState = FlashcardRevealState(false)
        activity.flashcardRevealState = revealState
        activity.flashcardHeroPanel = null
        activity.studyAnswerPanel = null
        val heroPanel = FlashcardHeroPanelModel(
            if (StudyTaskCopy.isWordReadingTask(session)) StudyTextCopy.wordPrompt(session) else session.item.kanji,
            if (StudyTaskCopy.isWordReadingTask(session)) 44 else 116,
            if (StudyTaskCopy.isFontRecognitionTask(session)) StudyFontVariants.random(activity) else Typeface.DEFAULT
        )
        val typingAnswer = if (StudyTaskCopy.isTypingMeaningTask(session)) {
            typingAnswerField()
        } else {
            null
        }

        val answerPanel = flashcardAnswerPanelModel(session)

        return ComposeView(activity).apply {
            isClickable = true
            isFocusable = true
            setContent {
                MaterialTheme {
                    FlashcardCard(
                        FlashcardCardModel(
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
                    )
                }
            }
        }
    }

    fun flashcardAnswerPanelModel(session: RecordsSchedulerModels.StudySession): StudyAnswerPanelModel {
        return flashcardAnswerPanelModel(activity, session)
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
        val card = remember(route) { route.card }
        AndroidView(
            factory = { card },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp)
                .padding(top = 10.dp, bottom = 14.dp),
        )
    }

    @Composable
    private fun ComposeFlashcardActionBar(route: ComposeFlashcardRouteModel) {
        StudyFlashcardActionBar(
            revealed = route.actionBarState.revealed,
            onReveal = { route.actionBarState.onReveal.run() },
            onFail = { route.actionBarState.onFail.run() },
            onPass = { route.actionBarState.onPass.run() },
        )
    }

    private data class ComposeFlashcardRouteModel(
        val card: View,
        val actionBarState: FlashcardActionBarState,
    )
}
