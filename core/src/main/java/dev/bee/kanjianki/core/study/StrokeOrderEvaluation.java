package dev.bee.kanjianki.core.study;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StrokeOrderEvaluation {
    private final int expectedCount;
    private final int attemptedCount;
    private final int orderedMatchCount;
    private final List<String> missingStrokeIds;
    private final List<String> extraStrokeIds;
    private final List<String> duplicateStrokeIds;
    private final List<String> outOfPositionStrokeIds;
    private final double score;

    public StrokeOrderEvaluation(
            int expectedCount,
            int attemptedCount,
            int orderedMatchCount,
            List<String> missingStrokeIds,
            List<String> extraStrokeIds,
            List<String> duplicateStrokeIds,
            List<String> outOfPositionStrokeIds,
            double score
    ) {
        this.expectedCount = Math.max(0, expectedCount);
        this.attemptedCount = Math.max(0, attemptedCount);
        this.orderedMatchCount = Math.max(0, orderedMatchCount);
        this.missingStrokeIds = copy(missingStrokeIds);
        this.extraStrokeIds = copy(extraStrokeIds);
        this.duplicateStrokeIds = copy(duplicateStrokeIds);
        this.outOfPositionStrokeIds = copy(outOfPositionStrokeIds);
        this.score = clamp(score);
    }

    public int expectedCount() {
        return expectedCount;
    }

    public int attemptedCount() {
        return attemptedCount;
    }

    public int orderedMatchCount() {
        return orderedMatchCount;
    }

    public List<String> missingStrokeIds() {
        return missingStrokeIds;
    }

    public List<String> extraStrokeIds() {
        return extraStrokeIds;
    }

    public List<String> duplicateStrokeIds() {
        return duplicateStrokeIds;
    }

    public List<String> outOfPositionStrokeIds() {
        return outOfPositionStrokeIds;
    }

    public double score() {
        return score;
    }

    public boolean complete() {
        return expectedCount > 0
                && attemptedCount == expectedCount
                && missingStrokeIds.isEmpty()
                && extraStrokeIds.isEmpty()
                && duplicateStrokeIds.isEmpty();
    }

    public boolean exactOrder() {
        return complete() && orderedMatchCount == expectedCount && outOfPositionStrokeIds.isEmpty();
    }

    public boolean passed() {
        return exactOrder();
    }

    private static List<String> copy(List<String> values) {
        return Collections.unmodifiableList(values == null ? new ArrayList<>() : new ArrayList<>(values));
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
