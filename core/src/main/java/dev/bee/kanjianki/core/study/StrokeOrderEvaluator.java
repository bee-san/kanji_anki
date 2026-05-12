package dev.bee.kanjianki.core.study;

public final class StrokeOrderEvaluator {
    private StrokeOrderEvaluator() {
    }

    public static StrokeOrderResult evaluate(StrokeGuide guide, WritingSample sample) {
        if (guide == null || guide.isEmpty()) {
            return StrokeOrderResult.missing();
        }
        if (sample == null || !sample.hasInk()) {
            return new StrokeOrderResult(false, false, 0f, "No ink was drawn.");
        }
        int expected = guide.strokeCount();
        int actual = sample.strokeCount();
        int countDelta = Math.abs(expected - actual);
        float countScore = Math.max(0f, 1f - (countDelta / (float) Math.max(1, expected)));
        int compared = Math.min(expected, actual);
        Bounds guideBounds = Bounds.forGuide(guide);
        Bounds sampleBounds = Bounds.forSample(sample);
        StrokeComparisonSummary summary = compareStrokes(guide, sample, sampleBounds, guideBounds, compared);
        float shapeScore = summary.shapeScore / compared;
        float weakestStrokeScore = summary.weakestStrokeScore;
        if (actual < expected) {
            addMissingStrokes(summary.matchedGuideStrokes, summary.diagnosis);
        }
        float score = clamp((countScore * 0.45f) + (shapeScore * 0.55f));
        boolean acceptable = countDelta <= Math.max(1, expected / 4) && score >= 0.45f;
        boolean clean = countDelta == 0 && score >= 0.68f && weakestStrokeScore >= 0.55f;
        String message = resultMessage(acceptable, clean);
        return new StrokeOrderResult(acceptable, clean, score, message, false, summary.diagnosis.build());
    }

    private static StrokeComparisonSummary compareStrokes(
            StrokeGuide guide,
            WritingSample sample,
            Bounds sampleBounds,
            Bounds guideBounds,
            int compared
    ) {
        float shapeScore = 0f;
        float weakestStrokeScore = 1f;
        StrokeDiagnosis.Builder diagnosis = StrokeDiagnosis.builder();
        boolean[] matchedGuideStrokes = new boolean[guide.strokeCount()];
        for (int i = 0; i < compared; i++) {
            StrokeComparison expectedComparison = compare(guide.strokes.get(i), sample.strokes.get(i), sampleBounds, guideBounds);
            shapeScore += expectedComparison.score;
            weakestStrokeScore = Math.min(weakestStrokeScore, expectedComparison.score);
            BestStrokeMatch best = bestGuideMatch(guide, sample.strokes.get(i), sampleBounds, guideBounds);
            if (best.index >= 0 && best.directionlessScore >= 0.65f) {
                matchedGuideStrokes[best.index] = true;
            }
            diagnoseComparedStroke(i, expectedComparison, best, diagnosis);
        }
        return new StrokeComparisonSummary(shapeScore, weakestStrokeScore, matchedGuideStrokes, diagnosis);
    }

    private static void addMissingStrokes(boolean[] matchedGuideStrokes, StrokeDiagnosis.Builder diagnosis) {
        for (int i = 0; i < matchedGuideStrokes.length; i++) {
            if (!matchedGuideStrokes[i]) {
                diagnosis.add(StrokeDiagnosis.Label.MISSING_STROKE, i + 1);
            }
        }
    }

    private static String resultMessage(boolean acceptable, boolean clean) {
        if (clean) {
            return "Stroke path looks clean.";
        }
        if (acceptable) {
            return "Readable path, but some strokes look shaky.";
        }
        return "The stroke count or order does not match the guide yet.";
    }

    private static void diagnoseComparedStroke(
            int expectedIndex,
            StrokeComparison expectedComparison,
            BestStrokeMatch best,
            StrokeDiagnosis.Builder diagnosis
    ) {
        boolean wrongOrder = best.index >= 0
                && best.index != expectedIndex
                && best.directionlessScore >= 0.72f
                && best.directionlessScore - expectedComparison.directionlessScore() >= 0.18f;
        if (wrongOrder) {
            diagnosis.add(StrokeDiagnosis.Label.WRONG_ORDER, expectedIndex + 1);
            return;
        }
        boolean wrongDirection = expectedComparison.reversedScore >= 0.70f
                && expectedComparison.reversedScore - expectedComparison.directScore >= 0.25f;
        if (wrongDirection) {
            diagnosis.add(StrokeDiagnosis.Label.WRONG_DIRECTION, expectedIndex + 1);
            return;
        }
        if (expectedComparison.score < 0.50f && expectedComparison.directionlessScore() < 0.65f) {
            diagnosis.add(StrokeDiagnosis.Label.ROUGH_SHAPE, expectedIndex + 1);
        }
    }

    private static BestStrokeMatch bestGuideMatch(StrokeGuide guide, InkStroke sampleStroke, Bounds sampleBounds, Bounds guideBounds) {
        int bestIndex = -1;
        float bestScore = 0f;
        for (int i = 0; i < guide.strokes.size(); i++) {
            StrokeComparison comparison = compare(guide.strokes.get(i), sampleStroke, sampleBounds, guideBounds);
            float score = comparison.directionlessScore();
            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }
        return new BestStrokeMatch(bestIndex, bestScore);
    }

    private static StrokeComparison compare(InkStroke guideStroke, InkStroke sampleStroke, Bounds sampleBounds, Bounds guideBounds) {
        InkPoint guideStart = guideStroke.start();
        InkPoint guideEnd = guideStroke.end();
        InkPoint sampleStart = normalized(sampleStroke.start(), sampleBounds, guideBounds);
        InkPoint sampleEnd = normalized(sampleStroke.end(), sampleBounds, guideBounds);
        if (guideStart == null || guideEnd == null || sampleStart == null || sampleEnd == null) {
            return new StrokeComparison(0f, 0f, 0f);
        }
        float startDistance = distance(guideStart, sampleStart);
        float endDistance = distance(guideEnd, sampleEnd);
        float direct = Math.max(0f, 1f - ((startDistance + endDistance) / 1.2f));
        float reverseStartDistance = distance(guideStart, sampleEnd);
        float reverseEndDistance = distance(guideEnd, sampleStart);
        float reversed = Math.max(0f, 1f - ((reverseStartDistance + reverseEndDistance) / 1.2f));
        float score = reversed > direct ? direct * 0.55f : direct;
        return new StrokeComparison(direct, reversed, score);
    }

    private static InkPoint normalized(InkPoint point, Bounds source, Bounds target) {
        if (point == null) {
            return null;
        }
        float x = target.minX + ((point.x - source.minX) / source.width()) * target.width();
        float y = target.minY + ((point.y - source.minY) / source.height()) * target.height();
        return new InkPoint(clamp(x), clamp(y), point.timestampMillis);
    }

    private static float distance(InkPoint a, InkPoint b) {
        float dx = a.x - b.x;
        float dy = a.y - b.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    public static final class StrokeOrderResult {
        public final boolean acceptable;
        public final boolean clean;
        public final float score;
        public final String message;
        public final boolean missingGuide;
        public final StrokeDiagnosis diagnosis;

        private StrokeOrderResult(boolean acceptable, boolean clean, float score, String message) {
            this(acceptable, clean, score, message, false, StrokeDiagnosis.empty());
        }

        private StrokeOrderResult(boolean acceptable, boolean clean, float score, String message, boolean missingGuide, StrokeDiagnosis diagnosis) {
            this.acceptable = acceptable;
            this.clean = clean;
            this.score = score;
            this.message = message;
            this.missingGuide = missingGuide;
            this.diagnosis = diagnosis == null ? StrokeDiagnosis.empty() : diagnosis;
        }

        public StrokeOrderResult withDiagnosis(StrokeDiagnosis diagnosis) {
            return new StrokeOrderResult(acceptable, clean, score, message, missingGuide, diagnosis);
        }

        private static StrokeOrderResult missing() {
            return new StrokeOrderResult(false, false, 0f, "No stroke-order guide is available for this kanji.", true, StrokeDiagnosis.empty());
        }
    }

    private static final class StrokeComparison {
        final float directScore;
        final float reversedScore;
        final float score;

        private StrokeComparison(float directScore, float reversedScore, float score) {
            this.directScore = directScore;
            this.reversedScore = reversedScore;
            this.score = score;
        }

        float directionlessScore() {
            return Math.max(directScore, reversedScore);
        }
    }

    private static final class StrokeComparisonSummary {
        final float shapeScore;
        final float weakestStrokeScore;
        final boolean[] matchedGuideStrokes;
        final StrokeDiagnosis.Builder diagnosis;

        private StrokeComparisonSummary(
                float shapeScore,
                float weakestStrokeScore,
                boolean[] matchedGuideStrokes,
                StrokeDiagnosis.Builder diagnosis
        ) {
            this.shapeScore = shapeScore;
            this.weakestStrokeScore = weakestStrokeScore;
            this.matchedGuideStrokes = matchedGuideStrokes;
            this.diagnosis = diagnosis;
        }
    }

    private static final class BestStrokeMatch {
        final int index;
        final float directionlessScore;

        private BestStrokeMatch(int index, float directionlessScore) {
            this.index = index;
            this.directionlessScore = directionlessScore;
        }
    }

    private static final class Bounds {
        final float minX;
        final float minY;
        final float maxX;
        final float maxY;

        private Bounds(float minX, float minY, float maxX, float maxY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }

        float width() {
            return Math.max(0.001f, maxX - minX);
        }

        float height() {
            return Math.max(0.001f, maxY - minY);
        }

        static Bounds forGuide(StrokeGuide guide) {
            MutableBounds bounds = new MutableBounds();
            for (InkStroke stroke : guide.strokes) {
                for (InkPoint point : stroke.points) {
                    bounds.include(point);
                }
            }
            return bounds.toBounds();
        }

        static Bounds forSample(WritingSample sample) {
            MutableBounds bounds = new MutableBounds();
            for (InkStroke stroke : sample.strokes) {
                for (InkPoint point : stroke.points) {
                    bounds.include(point);
                }
            }
            return bounds.toBounds();
        }
    }

    private static final class MutableBounds {
        private float minX = Float.MAX_VALUE;
        private float minY = Float.MAX_VALUE;
        private float maxX = -Float.MAX_VALUE;
        private float maxY = -Float.MAX_VALUE;

        void include(InkPoint point) {
            if (point == null) {
                return;
            }
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }

        Bounds toBounds() {
            if (minX == Float.MAX_VALUE) {
                return new Bounds(0f, 0f, 1f, 1f);
            }
            return new Bounds(minX, minY, maxX, maxY);
        }
    }
}
