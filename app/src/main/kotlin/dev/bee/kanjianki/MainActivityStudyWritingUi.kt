package dev.bee.kanjianki

import android.view.View
import dev.bee.kanjianki.core.StudyTaskCopy
import dev.bee.kanjianki.core.study.WritingActionPresentation

internal class MainActivityStudyWritingUi(private val activity: MainActivityStudy) {
    private val writingStatus = MainActivityStudyWritingStatus(activity)
    private val writingToolbar = MainActivityStudyWritingToolbar(activity)

    fun buildStudyActionBar() {
        writingToolbar.buildStudyActionBar()
    }

    fun buildComposeActionBarState(): WritingActionsBarState {
        return writingToolbar.buildComposeActionBarState()
    }

    fun updateResultActions() {
        val presentation = writingActionPresentation()
        updateToolActionRow(presentation)
        updatePrimaryActionRow(presentation)
        updateFallbackActionButtons(presentation)
        updateHintAndAnswerVisibility(presentation)
        if (!presentation.resultStatusVisible) {
            hideResultStatus()
        }
    }

    fun refreshWritingModelStatus() {
        writingStatus.refreshWritingModelStatus()
    }

    fun writingActionPresentation(): WritingActionPresentation {
        val input = WritingActionPresentation.Input(activity.activeAnalysis)
        val drawingPad = activity.drawingPad
        val session = activity.activeSession
        input.checkingWriting = activity.checkingWriting
        input.canUndoStroke = drawingPad != null && drawingPad.canUndoStroke()
        input.writingModelStatusKnown = activity.writingModelStatusKnown
        input.writingModelDownloaded = activity.writingModelDownloaded
        input.hasReplaySnapshot = drawingPad != null && drawingPad.hasReplaySnapshot()
        input.hasInk = drawingPad != null && drawingPad.hasInk()
        input.guide = session?.item?.let { activity.strokeGuide(it.kanji) }
        input.canRevealMoreHelp = canRevealMoreHelp()
        input.recallTask = session != null && StudyTaskCopy.isRecallTask(session)
        input.teachingTask = session != null && StudyTaskCopy.isTeachingTask(session)
        input.currentPracticeLevel = activity.currentPracticeLevel
        return WritingActionPresentation.from(input)
    }

    fun updateUndoStrokeButton() {
        updateToolActionRow(writingActionPresentation())
    }

    fun updateToolActionRow(presentation: WritingActionPresentation) {
        activity.writingToolActionsView?.render(
            WritingToolActionsModel(
                presentation.undoEnabled,
                presentation.hintText,
                presentation.hintVisible,
                Runnable { activity.eraseWritingPad() },
                Runnable { activity.undoWritingStroke() },
                Runnable { activity.showWritingHint() }
            )
        )
    }

    fun updatePrimaryActionRow(presentation: WritingActionPresentation) {
        activity.writingPrimaryActionsView?.render(
            WritingPrimaryActionsModel(
                presentation.checkText,
                presentation.checkVisible,
                presentation.checkEnabled,
                "Download checker",
                presentation.downloadVisible,
                presentation.nextLabel,
                presentation.nextVisible,
                if (presentation.messyPass) {
                    Runnable { activity.startCleanerRetry() }
                } else {
                    Runnable { activity.checkWriting() }
                },
                Runnable { writingStatus.downloadWritingModel() },
                Runnable { activity.submitReview(presentation.nextRating, false) }
            )
        )
    }

    fun updateFallbackActionButtons(presentation: WritingActionPresentation) {
        activity.writingFallbackActionsView?.render(
            WritingFallbackActionsModel(
                presentation.replayVisible,
                presentation.manualOverrideVisible,
                presentation.practiceWithGuideVisible,
                Runnable { activity.replayWritingAnalysis() },
                Runnable { activity.submitReview(MainActivityBase.RATING_GOOD, true) },
                Runnable { activity.startGuidedWritingRetry() }
            )
        )
    }

    fun updateHintAndAnswerVisibility(presentation: WritingActionPresentation) {
        val answerPanelState = activity.writingAnswerPanelState
        if (answerPanelState != null) {
            answerPanelState.updateVisible(presentation.answerPanelVisible)
        } else {
            val answerPanel = activity.studyAnswerPanel
            if (answerPanel != null) {
                answerPanel.visibility = if (presentation.answerPanelVisible) View.VISIBLE else View.GONE
            }
        }
    }

    fun canRevealMoreHelp(): Boolean {
        val session = activity.activeSession ?: return false
        val guide = activity.strokeGuide(session.item?.kanji ?: return false)
        return activity.hintProgression.canRevealMoreHelp(activity.currentHintState, guide)
    }

    fun setStudyStatus(value: String, color: Int) {
        activity.studyStatus?.setStatus(value, color)
        if (activity.activeAnalysis == null) {
            hideResultStatus()
        }
    }

    fun setResultStatus(value: String, color: Int) {
        activity.writingResultStatus?.show(value, color)
    }

    fun hideResultStatus() {
        activity.writingResultStatus?.hide()
    }
}
