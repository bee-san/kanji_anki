package dev.bee.kanjianki.core.study

class WritingActionPresentation private constructor(input: Input?) {
    @JvmField val hasResult: Boolean
    @JvmField val passed: Boolean
    @JvmField val messyPass: Boolean
    @JvmField val checkVisible: Boolean
    @JvmField val checkEnabled: Boolean
    @JvmField val checkText: String
    @JvmField val undoEnabled: Boolean
    @JvmField val downloadVisible: Boolean
    @JvmField val nextVisible: Boolean
    @JvmField val nextLabel: String
    @JvmField val nextRating: String
    @JvmField val manualOverrideVisible: Boolean
    @JvmField val practiceWithGuideVisible: Boolean
    @JvmField val replayVisible: Boolean
    @JvmField val hintVisible: Boolean
    @JvmField val hintText: String
    @JvmField val answerPanelVisible: Boolean
    @JvmField val resultStatusVisible: Boolean

    init {
        val safeInput = input ?: Input(null)
        val analysis = safeInput.analysis
        hasResult = analysis != null
        passed = analysis?.writingPassed == true
        messyPass = analysis?.status == WritingAnalysis.Status.CLOSE
        checkVisible = !passed || messyPass
        checkEnabled = !safeInput.checkingWriting
        checkText = WritingFeedbackCopy.checkWritingButtonText(safeInput.checkingWriting, messyPass)
        undoEnabled = !safeInput.checkingWriting && safeInput.canUndoStroke
        downloadVisible = !(safeInput.writingModelStatusKnown && safeInput.writingModelDownloaded)
        nextVisible = WritingFeedbackCopy.canSubmitAnalysis(analysis)
        nextLabel = WritingFeedbackCopy.submitLabel(analysis)
        nextRating = WritingFeedbackCopy.submitRating(analysis)
        manualOverrideVisible = hasResult && WritingFeedbackCopy.canManualOverride(analysis)
        practiceWithGuideVisible = hasResult && !passed && WritingFeedbackCopy.canPracticeAfterAnalysis(analysis)
        replayVisible = hasResult &&
            safeInput.hasReplaySnapshot &&
            WritingFeedbackCopy.canReplayAnalysis(analysis, safeInput.hasInk, safeInput.guide)
        hintVisible = !passed && safeInput.canRevealMoreHelp
        hintText = if (safeInput.currentPracticeLevel == 3) "Hint" else "More help"
        answerPanelVisible = WritingFeedbackCopy.shouldShowLearningPanel(
            analysis,
            safeInput.recallTask,
            safeInput.teachingTask,
            safeInput.currentPracticeLevel
        )
        resultStatusVisible = hasResult
    }

    class Input(@JvmField val analysis: WritingAnalysis?) {
        @JvmField var checkingWriting: Boolean = false
        @JvmField var canUndoStroke: Boolean = false
        @JvmField var writingModelStatusKnown: Boolean = false
        @JvmField var writingModelDownloaded: Boolean = false
        @JvmField var hasReplaySnapshot: Boolean = false
        @JvmField var hasInk: Boolean = false
        @JvmField var guide: StrokeGuide? = null
        @JvmField var canRevealMoreHelp: Boolean = false
        @JvmField var recallTask: Boolean = false
        @JvmField var teachingTask: Boolean = false
        @JvmField var currentPracticeLevel: Int = 0
    }

    companion object {
        @JvmStatic
        fun from(input: Input?): WritingActionPresentation = WritingActionPresentation(input)
    }
}
