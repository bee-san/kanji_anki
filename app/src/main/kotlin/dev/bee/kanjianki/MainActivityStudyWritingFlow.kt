package dev.bee.kanjianki

import dev.bee.kanjianki.core.study.HintState
import dev.bee.kanjianki.core.study.StrokeGuideGuard
import dev.bee.kanjianki.core.study.WritingAnalysis
import dev.bee.kanjianki.core.study.WritingAnalysisEngine
import dev.bee.kanjianki.core.study.WritingFeedbackCopy

internal class MainActivityStudyWritingFlow(private val activity: MainActivityStudy) {
    private val writingResult = MainActivityStudyWritingResult(activity)

    fun eraseWritingPad() {
        val session = activity.activeSession!!
        activity.drawingPad!!.clear()
        activity.activeAnalysis = null
        activity.setStudyStatus(
            WritingFeedbackCopy.guideLabel(activity.currentHintState, activity.strokeGuide(session.item.kanji)),
            MainActivityBase.MUTED
        )
        activity.updateResultActions()
    }

    fun startGuidedWritingRetry() {
        val session = activity.activeSession!!
        val drawingPad = activity.drawingPad!!
        activity.setHintState(HintState.initial())
        activity.hintsUsed++
        activity.activeAnalysis = null
        drawingPad.clear()
        val guide = activity.strokeGuide(session.item.kanji)
        drawingPad.setGuide(guide, activity.currentHintState, false)
        activity.setStudyStatus(
            WritingFeedbackCopy.freshGuidedTryStatus(WritingFeedbackCopy.guideLabel(activity.currentHintState, guide)),
            MainActivityBase.MUTED
        )
        activity.updateResultActions()
    }

    fun showNoInkWhenNeeded(): Boolean {
        if (activity.drawingPad != null && activity.drawingPad!!.hasInk()) {
            return false
        }
        val analysis = WritingAnalysisEngine.noInk(activity.currentHintState.level(), activity.hintsUsed)
        activity.activeAnalysis = analysis
        writingResult.showAnalysis(analysis)
        return true
    }

    fun showModelUnavailable(message: String) {
        writingResult.showModelUnavailable(message)
    }

    fun showAnalysis(analysis: WritingAnalysis) {
        writingResult.showAnalysis(analysis)
    }

    fun showWritingHint() {
        val drawingPad = activity.drawingPad ?: return
        val session = activity.activeSession ?: return
        val guide = activity.strokeGuide(session.item.kanji)
        activity.setHintState(activity.hintProgression.revealNext(activity.currentHintState, guide))
        activity.hintsUsed++
        activity.activeAnalysis = null
        drawingPad.setGuide(guide, activity.currentHintState, false)
        activity.setStudyStatus(
            WritingFeedbackCopy.hintUsedStatus(WritingFeedbackCopy.guideLabel(activity.currentHintState, guide)),
            MainActivityBase.MUTED
        )
        activity.updateResultActions()
    }

    fun startCleanerRetry() {
        val drawingPad = activity.drawingPad ?: return
        val session = activity.activeSession ?: return
        clearWritingResult()
        val guide = activity.strokeGuide(session.item.kanji)
        drawingPad.clear()
        drawingPad.setGuide(guide, activity.currentHintState, false)
        activity.setStudyStatus(
            WritingFeedbackCopy.cleanerRetryStatus(WritingFeedbackCopy.guideLabel(activity.currentHintState, guide)),
            MainActivityBase.MUTED
        )
        activity.updateResultActions()
    }

    fun undoWritingStroke() {
        val drawingPad = activity.drawingPad
        val session = activity.activeSession
        if (drawingPad == null || session == null || !drawingPad.undoLastStroke()) {
            activity.updateUndoStrokeButton()
            return
        }
        clearWritingResult()
        val guide = activity.strokeGuide(session.item.kanji)
        activity.setStudyStatus(
            WritingFeedbackCopy.undoStrokeStatus(WritingFeedbackCopy.guideLabel(activity.currentHintState, guide)),
            MainActivityBase.MUTED
        )
        activity.updateResultActions()
    }

    fun replayWritingAnalysis() {
        val drawingPad = activity.drawingPad ?: return
        val session = activity.activeSession ?: return
        val guide = activity.strokeGuide(session.item.kanji)
        if (WritingFeedbackCopy.canReplayAnalysis(activity.activeAnalysis, drawingPad.hasInk(), guide)) {
            drawingPad.setGuide(guide, activity.currentHintState, true)
            drawingPad.startReplay()
        }
    }

    fun handleDrawingEdited() {
        activity.updateUndoStrokeButton()
        val drawingPad = activity.drawingPad
        val session = activity.activeSession
        if (activity.checkingWriting || activity.activeAnalysis == null || session == null || drawingPad == null) {
            return
        }
        clearWritingResult()
        drawingPad.clearReplaySnapshot()
        val guide = activity.strokeGuide(session.item.kanji)
        activity.setStudyStatus(
            WritingFeedbackCopy.updatedInkStatus(WritingFeedbackCopy.guideLabel(activity.currentHintState, guide)),
            MainActivityBase.MUTED
        )
        activity.updateResultActions()
    }

    fun clearWritingResult() {
        writingResult.clearWritingResult()
    }

    fun handleDrawingBlocked(decision: StrokeGuideGuard.Decision) {
        val session = activity.activeSession ?: return
        if (activity.drawingPad == null) {
            return
        }
        val guide = activity.strokeGuide(session.item.kanji)
        activity.setStudyStatus(
            WritingFeedbackCopy.blockedStrokeStatus(WritingFeedbackCopy.guideLabel(activity.currentHintState, guide), decision),
            MainActivityBase.MUTED
        )
        activity.updateUndoStrokeButton()
    }
}
