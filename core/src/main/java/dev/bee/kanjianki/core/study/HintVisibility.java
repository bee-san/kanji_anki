package dev.bee.kanjianki.core.study;

public final class HintVisibility {
    private final HintLevel level;
    private final boolean tracePathsVisible;
    private final boolean outlineVisible;
    private final boolean strokeNumbersVisible;
    private final boolean startDotsVisible;
    private final boolean strokeCountVisible;
    private final int visibleStrokeCount;

    public HintVisibility(
            HintLevel level,
            boolean tracePathsVisible,
            boolean outlineVisible,
            boolean strokeNumbersVisible,
            boolean startDotsVisible,
            boolean strokeCountVisible,
            int visibleStrokeCount
    ) {
        this.level = level == null ? HintLevel.TRACE : level;
        this.tracePathsVisible = tracePathsVisible;
        this.outlineVisible = outlineVisible;
        this.strokeNumbersVisible = strokeNumbersVisible;
        this.startDotsVisible = startDotsVisible;
        this.strokeCountVisible = strokeCountVisible;
        this.visibleStrokeCount = Math.max(0, visibleStrokeCount);
    }

    public HintLevel level() {
        return level;
    }

    public boolean tracePathsVisible() {
        return tracePathsVisible;
    }

    public boolean outlineVisible() {
        return outlineVisible;
    }

    public boolean strokeNumbersVisible() {
        return strokeNumbersVisible;
    }

    public boolean startDotsVisible() {
        return startDotsVisible;
    }

    public boolean strokeCountVisible() {
        return strokeCountVisible;
    }

    public int visibleStrokeCount() {
        return visibleStrokeCount;
    }
}
