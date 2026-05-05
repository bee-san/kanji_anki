package dev.bee.kanjianki.core.study;

import java.util.Collections;
import java.util.List;

public final class WritingAnalysisEngine {
    private WritingAnalysisEngine() {
    }

    public static WritingAnalysis noInk() {
        return new WritingAnalysis(WritingAnalysis.Status.NO_INK, "again", false, "Write the kanji before checking.", Collections.emptyList(), null);
    }

    public static WritingAnalysis modelUnavailable(String message) {
        return new WritingAnalysis(WritingAnalysis.Status.MODEL_UNAVAILABLE, "again", false, message, Collections.emptyList(), null);
    }

    public static WritingAnalysis recognitionError(String message) {
        return new WritingAnalysis(WritingAnalysis.Status.RECOGNITION_ERROR, "again", false, message, Collections.emptyList(), null);
    }

    public static WritingAnalysis analyze(String target, WritingSample sample, StrokeGuide guide, List<RecognitionCandidate> candidates) {
        if (sample == null || !sample.hasInk()) {
            return noInk();
        }
        StrokeOrderEvaluator.StrokeOrderResult order = StrokeOrderEvaluator.evaluate(guide, sample);
        if (order.missingGuide) {
            return new WritingAnalysis(WritingAnalysis.Status.NO_STROKE_DATA, "again", false, order.message, candidates, order);
        }
        RecognitionMatch match = match(target, candidates);
        if (!match.recognized) {
            return new WritingAnalysis(WritingAnalysis.Status.WRONG, "again", false, "That did not look like the target kanji yet.", candidates, order);
        }
        if (!order.acceptable) {
            return new WritingAnalysis(WritingAnalysis.Status.WRONG, "again", false, order.message, candidates, order);
        }
        if (!order.clean) {
            return new WritingAnalysis(WritingAnalysis.Status.CLOSE, "hard", true, "Recognized the kanji. Stroke order is close, so give it one more guided pass.", candidates, order);
        }
        if (match.topCandidate) {
            return new WritingAnalysis(WritingAnalysis.Status.PASS, "easy", true, "Recognized cleanly with matching stroke order.", candidates, order);
        }
        return new WritingAnalysis(WritingAnalysis.Status.PASS, "good", true, "Recognized the kanji. Keep tightening stroke order.", candidates, order);
    }

    private static RecognitionMatch match(String target, List<RecognitionCandidate> candidates) {
        if (target == null || candidates == null || candidates.isEmpty()) {
            return new RecognitionMatch(false, false);
        }
        for (int i = 0; i < candidates.size(); i++) {
            String text = normalizedCandidate(candidates.get(i).text);
            if (target.equals(text)) {
                return new RecognitionMatch(true, i == 0);
            }
        }
        return new RecognitionMatch(false, false);
    }

    private static String normalizedCandidate(String text) {
        if (text == null) {
            return "";
        }
        return text.trim()
                .replace("\uFE0E", "")
                .replace("\uFE0F", "");
    }

    private static final class RecognitionMatch {
        final boolean recognized;
        final boolean topCandidate;

        RecognitionMatch(boolean recognized, boolean topCandidate) {
            this.recognized = recognized;
            this.topCandidate = topCandidate;
        }
    }
}
