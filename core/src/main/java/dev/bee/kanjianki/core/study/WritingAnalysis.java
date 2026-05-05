package dev.bee.kanjianki.core.study;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WritingAnalysis {
    public enum Status {
        PASS,
        CLOSE,
        WRONG,
        NO_INK,
        MODEL_UNAVAILABLE,
        NO_STROKE_DATA,
        RECOGNITION_ERROR
    }

    public final Status status;
    public final String rating;
    public final boolean writingPassed;
    public final String message;
    public final List<RecognitionCandidate> candidates;
    public final StrokeOrderEvaluator.StrokeOrderResult strokeOrder;

    public WritingAnalysis(
            Status status,
            String rating,
            boolean writingPassed,
            String message,
            List<RecognitionCandidate> candidates,
            StrokeOrderEvaluator.StrokeOrderResult strokeOrder
    ) {
        this.status = status;
        this.rating = rating;
        this.writingPassed = writingPassed;
        this.message = message == null ? "" : message;
        this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates == null ? Collections.emptyList() : candidates));
        this.strokeOrder = strokeOrder;
    }

    public boolean failed() {
        return status != Status.PASS && status != Status.CLOSE;
    }

    public boolean passed() {
        return writingPassed;
    }

    public double confidenceScore() {
        double recognitionScore = 0.0;
        if (!candidates.isEmpty() && candidates.get(0).score != null) {
            recognitionScore = candidates.get(0).score;
        } else if (!candidates.isEmpty()) {
            recognitionScore = writingPassed ? 0.78 : 0.0;
        }
        double orderScore = strokeOrder == null ? (writingPassed ? 0.7 : 0.0) : strokeOrder.score;
        return Math.max(0.0, Math.min(1.0, (recognitionScore * 0.55) + (orderScore * 0.45)));
    }

    public HintLevel hintLevel() {
        return HintLevel.BLIND;
    }

    public int hintsUsed() {
        return 0;
    }
}
