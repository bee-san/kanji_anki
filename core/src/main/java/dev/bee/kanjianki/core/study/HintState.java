package dev.bee.kanjianki.core.study;

public final class HintState {
    private final HintLevel level;
    private final int revealedStrokeCount;
    private final int consecutivePasses;

    public HintState(HintLevel level, int revealedStrokeCount, int consecutivePasses) {
        this.level = level == null ? HintLevel.TRACE : level;
        this.revealedStrokeCount = Math.max(0, revealedStrokeCount);
        this.consecutivePasses = Math.max(0, consecutivePasses);
    }

    public static HintState initial() {
        return new HintState(HintLevel.TRACE, 0, 0);
    }

    public static HintState fromWritingLevel(int writingLevel) {
        return new HintState(HintLevel.fromWritingLevel(writingLevel), 0, 0);
    }

    public HintLevel level() {
        return level;
    }

    public int revealedStrokeCount() {
        return revealedStrokeCount;
    }

    public int consecutivePasses() {
        return consecutivePasses;
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
