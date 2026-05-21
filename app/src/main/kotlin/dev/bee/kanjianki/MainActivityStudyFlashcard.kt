package dev.bee.kanjianki

import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.StudyTaskCopy
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.core.study.HintState

internal class MainActivityStudyFlashcard(private val activity: MainActivityStudy) {
    private val interaction = MainActivityStudyFlashcardInteraction(activity)

    fun renderFlashcardSession(session: RecordsSchedulerModels.StudySession) {
        resetFlashcardSession()

        val card = recognitionHeroCard(session)
        activity.flashcardCard = card
        activity.flashcardGestureArea = card

        val cardLp = LinearLayout.LayoutParams(-1, 0, 1f)
        cardLp.setMargins(0, 0, 0, activity.dp(14))
        activity.content.addView(card, cardLp)
        interaction.buildFlashcardActionBar(false)
    }

    fun resetFlashcardSession() {
        activity.prepareStudyContent(activity.activeStudyPlan, true)
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

    fun recognitionHeroCard(session: RecordsSchedulerModels.StudySession): View {
        val revealState = FlashcardRevealState(false)
        activity.flashcardRevealState = revealState
        activity.flashcardHeroPanel = null
        activity.studyAnswerPanel = null
        val heroPanel = heroKanjiPanelModel(session)
        val typingAnswer = if (StudyTaskCopy.isTypingMeaningTask(session)) {
            typingAnswerField()
        } else {
            null
        }

        val answerPanel = flashcardAnswerPanelModel(session)

        return flashcardCardView(
            activity,
            FlashcardCardModel(
                flashcardPromptHeaderModel(session),
                heroPanel,
                typingAnswer,
                answerPanel,
                revealState
            )
        )
    }

    fun flashcardPromptHeaderModel(session: RecordsSchedulerModels.StudySession): FlashcardPromptHeaderModel {
        return FlashcardPromptHeaderModel(
            StudyTaskCopy.studyModeLabel(session),
            StudyTaskCopy.flashcardTitle(session),
            StudyTextCopy.heroQuestion(session),
            "Answer hidden until reveal",
            activity.studyReasonLine(session)
        )
    }

    fun heroKanjiPanel(session: RecordsSchedulerModels.StudySession): View {
        return heroKanjiPanelView(
            activity,
            heroKanjiPanelModel(session)
        )
    }

    fun heroKanjiPanelModel(session: RecordsSchedulerModels.StudySession): FlashcardHeroPanelModel {
        return FlashcardHeroPanelModel(
            if (StudyTaskCopy.isWordReadingTask(session)) StudyTextCopy.wordPrompt(session) else session.item.kanji,
            if (StudyTaskCopy.isWordReadingTask(session)) 44 else 116,
            if (StudyTaskCopy.isFontRecognitionTask(session)) randomFontVariantTypeface() else Typeface.DEFAULT
        )
    }

    fun randomFontVariantTypeface(): Typeface {
        return StudyFontVariants.random(activity)
    }

    fun flashcardAnswerPanel(session: RecordsSchedulerModels.StudySession): View {
        return flashcardAnswerPanelView(activity, session)
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

    fun isTouchInsideView(view: View, event: MotionEvent): Boolean {
        return interaction.isTouchInsideView(view, event)
    }
}
