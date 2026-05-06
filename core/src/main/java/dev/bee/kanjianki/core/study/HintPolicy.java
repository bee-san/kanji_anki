package dev.bee.kanjianki.core.study;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HintPolicy {
    private HintPolicy() {
    }

    public static List<StrokeHint> hintsFor(StrokeGuide guide, int writingLevel, int completedStrokes, boolean reveal) {
        return hintsFor(guide, HintState.fromWritingLevel(writingLevel), completedStrokes, reveal);
    }

    public static List<StrokeHint> hintsFor(StrokeGuide guide, HintState state, int completedStrokes, boolean reveal) {
        if (guide == null || guide.strokes.isEmpty()) {
            return Collections.emptyList();
        }
        List<StrokeHint> hints = new ArrayList<>();
        HintState safeState = state == null ? HintState.initial() : state;
        int current = Math.max(0, Math.min(completedStrokes, guide.strokeCount() - 1));
        for (int i = 0; i < guide.strokeCount(); i++) {
            HintVisibility visibility = visibilityFor(safeState, i, current, guide.strokeCount(), reveal);
            hints.add(new StrokeHint(i, visibility.visible, visibility.alpha, visibility.numberVisible, i == current, guide.strokes.get(i)));
        }
        return hints;
    }

    private static HintVisibility visibilityFor(HintState state, int index, int current, int strokeCount, boolean reveal) {
        if (reveal) {
            return new HintVisibility(true, index == current ? 0.95f : 0.42f, state.level() == HintLevel.TRACE);
        }
        switch (state.level()) {
            case TRACE:
                return new HintVisibility(true, index == current ? 0.95f : 0.62f, true);
            case OUTLINE:
                return new HintVisibility(true, index == current ? 0.9f : 0.20f, false);
            case MINIMAL:
                if (index == current) {
                    return new HintVisibility(true, 0.86f, false);
                }
                if (index > current && index <= Math.min(strokeCount - 1, current + state.revealedStrokeCount())) {
                    return new HintVisibility(true, 0.58f, false);
                }
                return new HintVisibility(false, 0f, false);
            case BLIND:
            default:
                if (index >= current && index < Math.min(strokeCount, current + state.revealedStrokeCount())) {
                    return new HintVisibility(true, index == current ? 0.86f : 0.58f, false);
                }
                return new HintVisibility(false, 0f, false);
        }
    }

    private static final class HintVisibility {
        final boolean visible;
        final float alpha;
        final boolean numberVisible;

        HintVisibility(boolean visible, float alpha, boolean numberVisible) {
            this.visible = visible;
            this.alpha = alpha;
            this.numberVisible = numberVisible;
        }
    }

    public static final class StrokeHint {
        public final int strokeIndex;
        public final boolean visible;
        public final float alpha;
        public final boolean numberVisible;
        public final boolean current;
        public final InkStroke stroke;

        public StrokeHint(int strokeIndex, boolean visible, float alpha, boolean current, InkStroke stroke) {
            this(strokeIndex, visible, alpha, true, current, stroke);
        }

        public StrokeHint(int strokeIndex, boolean visible, float alpha, boolean numberVisible, boolean current, InkStroke stroke) {
            this.strokeIndex = strokeIndex;
            this.visible = visible;
            this.alpha = alpha;
            this.numberVisible = numberVisible;
            this.current = current;
            this.stroke = stroke;
        }
    }
}
