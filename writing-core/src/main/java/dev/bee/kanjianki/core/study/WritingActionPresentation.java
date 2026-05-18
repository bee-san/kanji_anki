package dev.bee.kanjianki.core.study;

public final class WritingActionPresentation {
    public final boolean hasResult;
    public final boolean passed;
    public final boolean messyPass;
    public final boolean checkVisible;
    public final boolean checkEnabled;
    public final String checkText;
    public final boolean undoEnabled;
    public final boolean downloadVisible;
    public final boolean nextVisible;
    public final String nextLabel;
    public final String nextRating;
    public final boolean manualOverrideVisible;
    public final boolean practiceWithGuideVisible;
    public final boolean replayVisible;
    public final boolean hintVisible;
    public final String hintText;
    public final boolean answerPanelVisible;
    public final boolean resultStatusVisible;

    private WritingActionPresentation(Input input) {
        Input safeInput = input == null ? new Input(null) : input;
        WritingAnalysis analysis = safeInput.analysis;
        hasResult = analysis != null;
        passed = hasResult && analysis.writingPassed;
        messyPass = hasResult && analysis.status == WritingAnalysis.Status.CLOSE;
        checkVisible = !passed || messyPass;
        checkEnabled = !safeInput.checkingWriting;
        checkText = WritingFeedbackCopy.checkWritingButtonText(safeInput.checkingWriting, messyPass);
        undoEnabled = !safeInput.checkingWriting && safeInput.canUndoStroke;
        downloadVisible = !(safeInput.writingModelStatusKnown && safeInput.writingModelDownloaded);
        nextVisible = WritingFeedbackCopy.canSubmitAnalysis(analysis);
        nextLabel = WritingFeedbackCopy.submitLabel(analysis);
        nextRating = WritingFeedbackCopy.submitRating(analysis);
        manualOverrideVisible = hasResult && WritingFeedbackCopy.canManualOverride(analysis);
        practiceWithGuideVisible = hasResult && !passed && WritingFeedbackCopy.canPracticeAfterAnalysis(analysis);
        replayVisible = hasResult
                && safeInput.hasReplaySnapshot
                && WritingFeedbackCopy.canReplayAnalysis(analysis, safeInput.hasInk, safeInput.guide);
        hintVisible = !passed && safeInput.canRevealMoreHelp;
        hintText = safeInput.currentPracticeLevel == 3 ? "Hint" : "More help";
        answerPanelVisible = WritingFeedbackCopy.shouldShowLearningPanel(
                analysis,
                safeInput.recallTask,
                safeInput.teachingTask,
                safeInput.currentPracticeLevel
        );
        resultStatusVisible = hasResult;
    }

    public static WritingActionPresentation from(Input input) {
        return new WritingActionPresentation(input);
    }

    public static final class Input {
        public final WritingAnalysis analysis;
        public boolean checkingWriting;
        public boolean canUndoStroke;
        public boolean writingModelStatusKnown;
        public boolean writingModelDownloaded;
        public boolean hasReplaySnapshot;
        public boolean hasInk;
        public StrokeGuide guide;
        public boolean canRevealMoreHelp;
        public boolean recallTask;
        public boolean teachingTask;
        public int currentPracticeLevel;

        public Input(WritingAnalysis analysis) {
            this.analysis = analysis;
        }
    }
}
