package dev.bee.kanjianki.core.study;

public record HintState(HintLevel level, int revealedStrokeCount, int consecutivePasses) {
    public HintState {
        level = level == null ? HintLevel.TRACE : level;
        revealedStrokeCount = Math.max(0, revealedStrokeCount);
        consecutivePasses = Math.max(0, consecutivePasses);
    }

    public static HintState initial() {
        return new HintState(HintLevel.TRACE, 0, 0);
    }

    public static HintState fromWritingLevel(int writingLevel) {
        return new HintState(HintLevel.fromWritingLevel(writingLevel), 0, 0);
    }

    public HintState withLevel(HintLevel nextLevel) {
        return new HintState(nextLevel, 0, 0);
    }

    public HintState withRevealCount(int nextRevealCount) {
        return new HintState(level, nextRevealCount, consecutivePasses);
    }

    public HintState withConsecutivePasses(int nextConsecutivePasses) {
        return new HintState(level, revealedStrokeCount, nextConsecutivePasses);
    }
}
