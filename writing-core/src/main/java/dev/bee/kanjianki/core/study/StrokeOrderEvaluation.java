package dev.bee.kanjianki.core.study;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record StrokeOrderEvaluation(
        int expectedCount,
        int attemptedCount,
        int orderedMatchCount,
        List<String> missingStrokeIds,
        List<String> extraStrokeIds,
        List<String> duplicateStrokeIds,
        List<String> outOfPositionStrokeIds,
        double score
) {
    public StrokeOrderEvaluation {
        expectedCount = Math.max(0, expectedCount);
        attemptedCount = Math.max(0, attemptedCount);
        orderedMatchCount = Math.max(0, orderedMatchCount);
        missingStrokeIds = copy(missingStrokeIds);
        extraStrokeIds = copy(extraStrokeIds);
        duplicateStrokeIds = copy(duplicateStrokeIds);
        outOfPositionStrokeIds = copy(outOfPositionStrokeIds);
        score = clamp(score);
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
