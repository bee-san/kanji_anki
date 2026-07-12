package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.study.HintState

internal object MainActivityStudyInteractionReset {
    fun resetRoute(activity: MainActivityBase) {
        activity.flashcardGestureBounds = null
        activity.writingAnswerPanelState = null
        activity.flashcardRevealState = null
        activity.flashcardActionBarState = null
        activity.flashcardSwipeFeedback = null
        activity.flashcardAnswerRevealed = false
        activity.flashcardTouchTracking = false
    }

    fun resetFlashcard(activity: MainActivityStudy) {
        resetStudySurface(activity, HintState.initial(), true)
        activity.activeSimilarWritingRepair = null
        activity.drawingPad = null
        activity.flashcardHeroPanel = null
        activity.hideStudyActionBar()
    }

    fun resetChoice(activity: MainActivityStudy, resetTouchTracking: Boolean) {
        resetStudySurface(activity, HintState.initial(), resetTouchTracking)
        activity.activeSimilarWritingRepair = null
        activity.drawingPad = null
        activity.flashcardGestureBounds = null
        activity.hideStudyActionBar()
    }

    fun resetWriting(activity: MainActivityStudy, session: RecordsSchedulerModels.StudySession) {
        resetStudySurface(activity, activity.initialHintState(session), true)
        activity.flashcardGestureBounds = null
        activity.hideStudyActionBar()
    }

    private fun resetStudySurface(
        activity: MainActivityStudy,
        hintState: HintState,
        resetTouchTracking: Boolean,
    ) {
        activity.activeAnalysis = null
        activity.checkingWriting = false
        activity.flashcardAnswerRevealed = false
        if (resetTouchTracking) {
            activity.flashcardTouchTracking = false
        }
        activity.typingAnswerState = null
        activity.recognitionFailureCauseState = null
        activity.hintsUsed = 0
        activity.setHintState(hintState)
    }
}
