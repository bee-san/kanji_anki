package dev.bee.kanjianki.core.study;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HintPolicy {
    private HintPolicy() {
    }

    public static List<StrokeHint> hintsFor(StrokeGuide guide, int writingLevel, int completedStrokes, boolean reveal) {
        if (guide == null || guide.strokes.isEmpty()) {
            return Collections.emptyList();
        }
        List<StrokeHint> hints = new ArrayList<>();
        int level = Math.max(0, Math.min(3, writingLevel));
        int current = Math.max(0, Math.min(completedStrokes, guide.strokeCount() - 1));
        for (int i = 0; i < guide.strokeCount(); i++) {
            HintVisibility visibility = visibilityFor(level, i, current, reveal);
            hints.add(new StrokeHint(i, visibility.visible, visibility.alpha, i == current, guide.strokes.get(i)));
        }
        return hints;
    }

    private static HintVisibility visibilityFor(int level, int index, int current, boolean reveal) {
        if (reveal) {
            return new HintVisibility(true, index == current ? 0.95f : 0.42f);
        }
        if (level == 0) {
            return new HintVisibility(true, index == current ? 0.95f : 0.62f);
        }
        if (level == 1) {
            return new HintVisibility(true, index == current ? 0.9f : 0.24f);
        }
        if (level == 2) {
            int distance = index - current;
            if (distance < 0 || distance > 1) {
                return new HintVisibility(false, 0f);
            }
            return new HintVisibility(true, distance == 0 ? 0.86f : 0.34f);
        }
        return new HintVisibility(false, 0f);
    }

    private static final class HintVisibility {
        final boolean visible;
        final float alpha;

        HintVisibility(boolean visible, float alpha) {
            this.visible = visible;
            this.alpha = alpha;
        }
    }

    public static final class StrokeHint {
        public final int strokeIndex;
        public final boolean visible;
        public final float alpha;
        public final boolean current;
        public final InkStroke stroke;

        public StrokeHint(int strokeIndex, boolean visible, float alpha, boolean current, InkStroke stroke) {
            this.strokeIndex = strokeIndex;
            this.visible = visible;
            this.alpha = alpha;
            this.current = current;
            this.stroke = stroke;
        }
    }
}
