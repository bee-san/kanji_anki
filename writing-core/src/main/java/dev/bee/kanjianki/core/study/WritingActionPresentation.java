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

    private WritingActionPresentation(
            boolean hasResult,
            boolean passed,
            boolean messyPass,
            boolean checkVisible,
            boolean checkEnabled,
            String checkText,
            boolean undoEnabled,
            boolean downloadVisible,
            boolean nextVisible,
            String nextLabel,
            String nextRating,
            boolean manualOverrideVisible,
            boolean practiceWithGuideVisible,
            boolean replayVisible,
            boolean hintVisible,
            String hintText,
            boolean answerPanelVisible,
            boolean resultStatusVisible
    ) {
        this.hasResult = hasResult;
        this.passed = passed;
        this.messyPass = messyPass;
        this.checkVisible = checkVisible;
        this.checkEnabled = checkEnabled;
        this.checkText = checkText;
        this.undoEnabled = undoEnabled;
        this.downloadVisible = downloadVisible;
        this.nextVisible = nextVisible;
        this.nextLabel = nextLabel;
        this.nextRating = nextRating;
        this.manualOverrideVisible = manualOverrideVisible;
        this.practiceWithGuideVisible = practiceWithGuideVisible;
        this.replayVisible = replayVisible;
        this.hintVisible = hintVisible;
        this.hintText = hintText;
        this.answerPanelVisible = answerPanelVisible;
        this.resultStatusVisible = resultStatusVisible;
    }

    public static WritingActionPresentation from(Input input) {
        Input safeInput = input == null ? new Input(null) : input;
        WritingAnalysis analysis = safeInput.analysis;
        boolean hasResult = analysis != null;
        boolean passed = hasResult && analysis.writingPassed;
        boolean messyPass = hasResult && analysis.status == WritingAnalysis.Status.CLOSE;
        boolean submittable = WritingFeedbackCopy.canSubmitAnalysis(analysis);
        boolean replayVisible = hasResult
                && safeInput.hasReplaySnapshot
                && WritingFeedbackCopy.canReplayAnalysis(analysis, safeInput.hasInk, safeInput.guide);

        return new WritingActionPresentation(
                hasResult,
                passed,
                messyPass,
                !passed || messyPass,
                !safeInput.checkingWriting,
                WritingFeedbackCopy.checkWritingButtonText(safeInput.checkingWriting, messyPass),
                !safeInput.checkingWriting && safeInput.canUndoStroke,
                !(safeInput.writingModelStatusKnown && safeInput.writingModelDownloaded),
                submittable,
                WritingFeedbackCopy.submitLabel(analysis),
                WritingFeedbackCopy.submitRating(analysis),
                hasResult && WritingFeedbackCopy.canManualOverride(analysis),
                hasResult && !passed && WritingFeedbackCopy.canPracticeAfterAnalysis(analysis),
                replayVisible,
                !passed && safeInput.canRevealMoreHelp,
                safeInput.currentPracticeLevel == 3 ? "Hint" : "More help",
                WritingFeedbackCopy.shouldShowLearningPanel(
                        analysis,
                        safeInput.recallTask,
                        safeInput.teachingTask,
                        safeInput.currentPracticeLevel
                ),
                hasResult
        );
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
