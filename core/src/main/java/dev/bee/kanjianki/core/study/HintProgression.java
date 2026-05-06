package dev.bee.kanjianki.core.study;

public final class HintProgression {
    public HintVisibility visibility(HintState state, StrokeGuide guide) {
        HintState safeState = state == null ? HintState.initial() : state;
        int strokeCount = guide == null ? 0 : guide.strokeCount();
        int revealed = Math.min(safeState.revealedStrokeCount(), strokeCount);
        switch (safeState.level()) {
            case BLIND:
                return new HintVisibility(HintLevel.BLIND, false, false, false, false, false, revealed);
            case MINIMAL:
                return new HintVisibility(HintLevel.MINIMAL, false, false, false, true, true, Math.min(strokeCount, 1 + revealed));
            case OUTLINE:
                return new HintVisibility(HintLevel.OUTLINE, false, true, false, true, true, strokeCount);
            case TRACE:
            default:
                return new HintVisibility(HintLevel.TRACE, true, true, true, true, true, strokeCount);
        }
    }

    public HintState revealNext(HintState state, StrokeGuide guide) {
        HintState safeState = state == null ? HintState.initial() : state;
        int strokeCount = guide == null ? 0 : guide.strokeCount();
        if (safeState.level() == HintLevel.TRACE) {
            return new HintState(safeState.level(), strokeCount, 0);
        }
        if (safeState.level() == HintLevel.OUTLINE || strokeCount <= 0) {
            return new HintState(safeState.level().previous(), 0, 0);
        }
        if (safeState.revealedStrokeCount() >= strokeCount) {
            return new HintState(safeState.level().previous(), 0, 0);
        }
        return new HintState(
                safeState.level(),
                Math.min(strokeCount, safeState.revealedStrokeCount() + 1),
                0
        );
    }

    public HintState afterReview(HintState state, boolean writingPassed, int hintsUsed) {
        HintState safeState = state == null ? HintState.initial() : state;
        if (!writingPassed) {
            return new HintState(safeState.level().previous(), 0, 0);
        }
        if (hintsUsed > 0 || safeState.revealedStrokeCount() > 0) {
            return new HintState(safeState.level(), 0, 0);
        }
        return new HintState(safeState.level().next(), 0, safeState.consecutivePasses() + 1);
    }

    public HintState afterWriting(HintState state, WritingAnalysis analysis) {
        if (analysis == null) {
            return afterReview(state, false, 0);
        }
        if (analysis.status == WritingAnalysis.Status.CLOSE) {
            HintState safeState = state == null ? HintState.initial() : state;
            return new HintState(safeState.level(), 0, 0);
        }
        if (analysis.status != WritingAnalysis.Status.PASS) {
            return afterReview(state, false, analysis.hintsUsed());
        }
        return afterReview(state, true, analysis.hintsUsed());
    }
}
