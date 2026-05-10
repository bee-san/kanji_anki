package dev.bee.kanjianki.core.study;

public record HintVisibility(
        HintLevel level,
        boolean tracePathsVisible,
        boolean outlineVisible,
        boolean strokeNumbersVisible,
        boolean startDotsVisible,
        boolean strokeCountVisible,
        int visibleStrokeCount
) {
    public HintVisibility {
        level = level == null ? HintLevel.TRACE : level;
        visibleStrokeCount = Math.max(0, visibleStrokeCount);
    }
}
