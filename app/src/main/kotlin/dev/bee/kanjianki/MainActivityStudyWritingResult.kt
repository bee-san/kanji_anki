package dev.bee.kanjianki

import dev.bee.kanjianki.core.study.StrokeDiagnosisFormatter
import dev.bee.kanjianki.core.study.WritingAnalysis
import dev.bee.kanjianki.core.study.WritingAnalysisEngine
import dev.bee.kanjianki.core.study.WritingFeedbackCopy

internal class MainActivityStudyWritingResult(private val activity: MainActivityStudy) {
    fun showModelUnavailable(message: String) {
        val analysis = WritingAnalysisEngine.modelUnavailable(
            message,
            activity.currentHintState.level(),
            activity.hintsUsed
        )
        activity.activeAnalysis = analysis
        activity.checkingWriting = false
        showAnalysis(analysis)
    }

    fun showAnalysis(analysis: WritingAnalysis) {
        val session = activity.activeSession
        val guide = session?.item?.let { activity.strokeGuide(it.kanji) }
        val shouldIncreaseSupport = WritingFeedbackCopy.shouldIncreaseSupportAfterAnalysis(analysis)
        if (shouldIncreaseSupport) {
            activity.setHintState(activity.hintProgression.afterWriting(activity.currentHintState, analysis))
        }
        val drawingPad = activity.drawingPad
        if (drawingPad != null && session != null) {
            drawingPad.setGuide(guide, activity.currentHintState, true)
            if (WritingFeedbackCopy.canReplayAnalysis(analysis, drawingPad.hasInk(), guide)) {
                drawingPad.captureReplaySnapshot()
                drawingPad.startReplay()
            } else {
                drawingPad.clearReplaySnapshot()
            }
        }
        val color = if (analysis.writingPassed) MainActivityBase.TEAL else MainActivityBase.CORAL
        val targetKanji = session?.item?.kanji
        val activeWritingLevel = session?.item?.writingLevel
        val message = WritingFeedbackCopy.resultMessage(
            analysis,
            targetKanji,
            activeWritingLevel,
            shouldIncreaseSupport,
            StrokeDiagnosisFormatter.text(analysis)
        )
        activity.setStudyStatus(WritingFeedbackCopy.guideLabel(activity.currentHintState, guide), MainActivityBase.MUTED)
        activity.setResultStatus(message, color)
        AppTimingDiagnostics.markStudyFeedbackShown()
        activity.updateResultActions()
    }

    fun clearWritingResult() {
        activity.activeAnalysis = null
        activity.writingResultStatus?.hide()
    }
}
