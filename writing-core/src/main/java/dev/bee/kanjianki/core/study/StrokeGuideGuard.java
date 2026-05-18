package dev.bee.kanjianki.core.study;

public final class StrokeGuideGuard {
    public static final float DEFAULT_CORRIDOR_FRACTION = 0.18f;

    private StrokeGuideGuard() {
    }

    public static Decision evaluatePoint(
            StrokeGuide guide,
            int committedStrokeCount,
            float width,
            float height,
            float x,
            float y
    ) {
        return evaluatePoint(guide, committedStrokeCount, width, height, x, y, DEFAULT_CORRIDOR_FRACTION);
    }

    public static Decision evaluatePoint(
            StrokeGuide guide,
            int committedStrokeCount,
            float width,
            float height,
            float x,
            float y,
            float corridorFraction
    ) {
        if (guide == null || guide.isEmpty() || width <= 0f || height <= 0f) {
            return Decision.allow();
        }
        if (!finite(x) || !finite(y)) {
            return Decision.rejected(nextStrokeNumber(guide, committedStrokeCount), "Stay close to the guide.");
        }
        int strokeIndex = Math.max(0, committedStrokeCount);
        if (strokeIndex >= guide.strokeCount()) {
            return Decision.rejected(guide.strokeCount(), "All guided strokes are already drawn.");
        }
        InkStroke expected = guide.strokes.get(strokeIndex);
        if (expected == null || expected.points.isEmpty()) {
            return Decision.allow();
        }
        float corridor = Math.max(1f, Math.min(width, height) * Math.max(0f, corridorFraction));
        float distance = distanceToStroke(expected, width, height, x, y);
        if (distance <= corridor) {
            return Decision.allow();
        }
        return Decision.rejected(strokeIndex + 1, "Stay close to stroke " + (strokeIndex + 1) + ".");
    }

    private static int nextStrokeNumber(StrokeGuide guide, int committedStrokeCount) {
        if (guide == null || guide.isEmpty()) {
            return 0;
        }
        return Math.min(guide.strokeCount(), Math.max(0, committedStrokeCount) + 1);
    }

    private static float distanceToStroke(InkStroke stroke, float width, float height, float x, float y) {
        InkPoint previous = null;
        float best = Float.MAX_VALUE;
        for (InkPoint point : stroke.points) {
            if (point == null || !finite(point.x) || !finite(point.y)) {
                continue;
            }
            InkPoint scaled = new InkPoint(point.x * width, point.y * height, point.timestampMillis);
            best = Math.min(best, distance(x, y, scaled.x, scaled.y));
            if (previous != null) {
                best = Math.min(best, distanceToSegment(x, y, previous.x, previous.y, scaled.x, scaled.y));
            }
            previous = scaled;
        }
        return best == Float.MAX_VALUE ? 0f : best;
    }

    private static float distanceToSegment(float px, float py, float ax, float ay, float bx, float by) {
        float dx = bx - ax;
        float dy = by - ay;
        float lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= 0.0001f) {
            return distance(px, py, ax, ay);
        }
        float t = ((px - ax) * dx + (py - ay) * dy) / lengthSquared;
        t = Math.max(0f, Math.min(1f, t));
        return distance(px, py, ax + t * dx, ay + t * dy);
    }

    private static float distance(float ax, float ay, float bx, float by) {
        float dx = ax - bx;
        float dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    public static final class Decision {
        public final boolean allowed;
        public final int strokeNumber;
        public final String message;

        private Decision(boolean allowed, int strokeNumber, String message) {
            this.allowed = allowed;
            this.strokeNumber = strokeNumber;
            this.message = message == null ? "" : message;
        }

        public static Decision allow() {
            return new Decision(true, 0, "");
        }

        public static Decision rejected(int strokeNumber, String message) {
            return new Decision(false, Math.max(0, strokeNumber), message);
        }
    }
}
