package dev.bee.kanjianki.core.study;

public final class WritingHintPolicy {
    private WritingHintPolicy() {
    }

    public static HintState initialHintState(
            int writingLevel,
            int totalReviews,
            int learningStep,
            boolean targetedWriting
    ) {
        int stored = Math.max(HintLevel.TRACE.writingLevel(), Math.min(HintLevel.BLIND.writingLevel(), writingLevel));
        if (targetedWriting || totalReviews == 0 || learningStep == 0) {
            return HintState.fromWritingLevel(Math.min(stored, HintLevel.OUTLINE.writingLevel()));
        }
        return HintState.fromWritingLevel(stored);
    }
}
