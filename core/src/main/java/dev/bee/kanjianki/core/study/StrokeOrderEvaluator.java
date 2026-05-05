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
        if (expected == 0) {
            return StrokeOrderResult.missing();
        }
        int countDelta = Math.abs(expected - actual);
        float countScore = Math.max(0f, 1f - (countDelta / (float) Math.max(1, expected)));
        int compared = Math.min(expected, actual);
        Bounds guideBounds = Bounds.forGuide(guide);
        Bounds sampleBounds = Bounds.forSample(sample);
        float shapeScore = 0f;
        float weakestStrokeScore = compared == 0 ? 0f : 1f;
        for (int i = 0; i < compared; i++) {
            float score = strokeScore(guide.strokes.get(i), sample.strokes.get(i), sampleBounds, guideBounds);
            shapeScore += score;
            weakestStrokeScore = Math.min(weakestStrokeScore, score);
        }
        shapeScore = compared == 0 ? 0f : shapeScore / compared;
        float score = clamp((countScore * 0.45f) + (shapeScore * 0.55f));
        boolean acceptable = countDelta <= Math.max(1, expected / 4) && score >= 0.45f;
        boolean clean = countDelta == 0 && score >= 0.68f && weakestStrokeScore >= 0.55f;
        String message = clean ? "Stroke order looks clean."
                : acceptable ? "Stroke order is close enough, but some strokes look shaky."
                : "Stroke count or order does not match the guide.";
        return new StrokeOrderResult(acceptable, clean, score, message);
    }

    private static float strokeScore(InkStroke guideStroke, InkStroke sampleStroke, Bounds sampleBounds, Bounds guideBounds) {
        InkPoint guideStart = guideStroke.start();
        InkPoint guideEnd = guideStroke.end();
        InkPoint sampleStart = normalized(sampleStroke.start(), sampleBounds, guideBounds);
        InkPoint sampleEnd = normalized(sampleStroke.end(), sampleBounds, guideBounds);
        if (guideStart == null || guideEnd == null || sampleStart == null || sampleEnd == null) {
            return 0f;
        }
        float startDistance = distance(guideStart, sampleStart);
        float endDistance = distance(guideEnd, sampleEnd);
        float direct = Math.max(0f, 1f - ((startDistance + endDistance) / 1.2f));
        float reverseStartDistance = distance(guideStart, sampleEnd);
        float reverseEndDistance = distance(guideEnd, sampleStart);
        float reversed = Math.max(0f, 1f - ((reverseStartDistance + reverseEndDistance) / 1.2f));
        return reversed > direct ? direct * 0.55f : direct;
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

        private StrokeOrderResult(boolean acceptable, boolean clean, float score, String message) {
            this(acceptable, clean, score, message, false);
        }

        private StrokeOrderResult(boolean acceptable, boolean clean, float score, String message, boolean missingGuide) {
            this.acceptable = acceptable;
            this.clean = clean;
            this.score = score;
            this.message = message;
            this.missingGuide = missingGuide;
        }

        private static StrokeOrderResult missing() {
            return new StrokeOrderResult(false, false, 0f, "No stroke-order guide is available for this kanji.", true);
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
