package dev.bee.kanjianki.core.study;

import java.util.Collections;
import java.util.List;

public final class WritingAnalysisEngine {
    private WritingAnalysisEngine() {
    }

    public static WritingAnalysis noInk() {
        return noInk(HintLevel.BLIND, 0);
    }

    public static WritingAnalysis noInk(HintLevel hintLevel, int hintsUsed) {
        return new WritingAnalysis(WritingAnalysis.Status.NO_INK, "again", false, "Write in the square before checking.", Collections.emptyList(), null, hintLevel, hintsUsed);
    }

    public static WritingAnalysis modelUnavailable(String message) {
        return modelUnavailable(message, HintLevel.BLIND, 0);
    }

    public static WritingAnalysis modelUnavailable(String message, HintLevel hintLevel, int hintsUsed) {
        return new WritingAnalysis(WritingAnalysis.Status.MODEL_UNAVAILABLE, "again", false, message, Collections.emptyList(), null, hintLevel, hintsUsed);
    }

    public static WritingAnalysis recognitionError(String message) {
        return recognitionError(message, HintLevel.BLIND, 0);
    }

    public static WritingAnalysis recognitionError(String message, HintLevel hintLevel, int hintsUsed) {
        return new WritingAnalysis(WritingAnalysis.Status.RECOGNITION_ERROR, "again", false, "The handwriting checker could not read this attempt. Try once more.", Collections.emptyList(), null, hintLevel, hintsUsed);
    }

    public static WritingAnalysis analyze(String target, WritingSample sample, StrokeGuide guide, List<RecognitionCandidate> candidates) {
        return analyze(target, sample, guide, candidates, HintLevel.BLIND, 0);
    }

    public static WritingAnalysis analyze(
            String target,
            WritingSample sample,
            StrokeGuide guide,
            List<RecognitionCandidate> candidates,
            HintLevel hintLevel,
            int hintsUsed
    ) {
        if (sample == null || !sample.hasInk()) {
            return noInk(hintLevel, hintsUsed);
        }
        StrokeOrderEvaluator.StrokeOrderResult order = StrokeOrderEvaluator.evaluate(guide, sample);
        if (order.missingGuide) {
            RecognitionMatch match = match(target, candidates);
            if (match.recognized) {
                String message = match.topCandidate
                        ? "Recognized as the target kanji. Stroke order could not be checked because no guide is bundled yet."
                        : "Recognized as the target kanji, but stroke order could not be checked because no guide is bundled yet.";
                return new WritingAnalysis(WritingAnalysis.Status.CLOSE, match.topCandidate ? "good" : "hard", true, message, candidates, order, hintLevel, hintsUsed);
            }
            return new WritingAnalysis(WritingAnalysis.Status.NO_STROKE_DATA, "again", false, order.message + " I could not read that as the target kanji yet.", candidates, order, hintLevel, hintsUsed);
        }
        RecognitionMatch match = match(target, candidates);
        if (!match.recognized) {
            return new WritingAnalysis(WritingAnalysis.Status.WRONG, "again", false, "I could not read that as the target kanji yet.", candidates, order, hintLevel, hintsUsed);
        }
        if (!order.acceptable) {
            return new WritingAnalysis(WritingAnalysis.Status.WRONG, "again", false, order.message, candidates, order, hintLevel, hintsUsed);
        }
        if (!order.clean) {
            order = order.withDiagnosis(order.diagnosis.plus(StrokeDiagnosis.Label.RECOGNIZED_BUT_MESSY, 0));
            return new WritingAnalysis(WritingAnalysis.Status.CLOSE, "hard", true, "Readable, but the stroke path needs one more careful pass.", candidates, order, hintLevel, hintsUsed);
        }
        if (match.topCandidate) {
            return new WritingAnalysis(WritingAnalysis.Status.PASS, "easy", true, "Clean match.", candidates, order, hintLevel, hintsUsed);
        }
        return new WritingAnalysis(WritingAnalysis.Status.PASS, "good", true, "Matched the kanji. Keep tightening the stroke path.", candidates, order, hintLevel, hintsUsed);
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
