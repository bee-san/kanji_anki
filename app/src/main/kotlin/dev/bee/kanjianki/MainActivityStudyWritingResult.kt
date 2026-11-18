package dev.bee.kanjianki

import dev.bee.kanjianki.core.study.StrokeDiagnosisFormatter
import dev.bee.kanjianki.core.study.StrokeGuide
import dev.bee.kanjianki.core.study.StrokeOrderEvaluator
import dev.bee.kanjianki.core.study.WritingAnalysis
import dev.bee.kanjianki.core.study.WritingAnalysisEngine
import dev.bee.kanjianki.core.study.WritingCompareModel
import dev.bee.kanjianki.core.study.WritingFeedbackCopy
import dev.bee.kanjianki.core.study.WritingSample

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
        activity.writingCompareState?.show(compareModel(analysis, guide, drawingPad?.writingSample()))
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
        activity.updateResultActions()
    }

    /**
     * The comparison is pure geometry, so it is still available when the
     * recognizer is not (no model, recognition error): the learner can judge
     * their own strokes against the model.
     */
    private fun compareModel(
        analysis: WritingAnalysis,
        guide: StrokeGuide?,
        sample: WritingSample?,
    ): WritingCompareModel? {
        if (analysis.status == WritingAnalysis.Status.NO_INK) {
            return null
        }
        val diagnosis = (analysis.strokeOrder ?: StrokeOrderEvaluator.evaluate(guide, sample)).diagnosis
        return WritingCompareModel.from(guide, sample, diagnosis)
    }

    fun clearWritingResult() {
        activity.activeAnalysis = null
        activity.writingResultStatus?.hide()
        activity.writingCompareState?.hide()
    }
}
