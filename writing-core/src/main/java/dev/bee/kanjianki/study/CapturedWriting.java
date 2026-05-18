package dev.bee.kanjianki.study;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class CapturedWriting {
    public final List<CapturedStroke> strokes;
    public final Float writingAreaWidth;
    public final Float writingAreaHeight;
    public final String preContext;

    public CapturedWriting(List<CapturedStroke> strokes) {
        this(strokes, null, null, "");
    }

    public CapturedWriting(List<CapturedStroke> strokes, Float writingAreaWidth, Float writingAreaHeight, String preContext) {
        Objects.requireNonNull(strokes, "strokes");
        if (strokes.isEmpty()) {
            throw new IllegalArgumentException("Captured writing must contain at least one stroke.");
        }
        List<CapturedStroke> copy = new ArrayList<>();
        for (CapturedStroke stroke : strokes) {
            copy.add(Objects.requireNonNull(stroke, "stroke"));
        }
        if ((writingAreaWidth == null) != (writingAreaHeight == null)) {
            throw new IllegalArgumentException("writingAreaWidth and writingAreaHeight must both be set or both be null.");
        }
        if (writingAreaWidth != null) {
            requirePositiveFinite(writingAreaWidth, "writingAreaWidth");
            requirePositiveFinite(writingAreaHeight, "writingAreaHeight");
        }
        this.strokes = Collections.unmodifiableList(copy);
        this.writingAreaWidth = writingAreaWidth;
        this.writingAreaHeight = writingAreaHeight;
        this.preContext = preContext == null ? "" : preContext;
    }

    public static CapturedWriting of(List<CapturedStroke> strokes) {
        return new CapturedWriting(strokes);
    }

    public static CapturedWriting prepareForRecognition(List<CapturedStroke> strokes, float width, float height) {
        Objects.requireNonNull(strokes, "strokes");
        if (strokes.isEmpty()) {
            throw new IllegalArgumentException("Captured writing must contain at least one stroke.");
        }
        List<CapturedStroke> simplified = new ArrayList<>();
        for (CapturedStroke stroke : strokes) {
            simplified.add(simplify(stroke));
        }
        requirePositiveFinite(width, "width");
        requirePositiveFinite(height, "height");

        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (CapturedStroke stroke : simplified) {
            for (CapturedStroke.Point point : stroke.points) {
                minX = Math.min(minX, point.x);
                maxX = Math.max(maxX, point.x);
                minY = Math.min(minY, point.y);
                maxY = Math.max(maxY, point.y);
            }
        }
        float sourceWidth = Math.max(maxX - minX, 1f);
        float sourceHeight = Math.max(maxY - minY, 1f);
        float targetWidth = 1000f;
        float targetHeight = 1000f;
        float margin = 140f;
        float scale = Math.min((targetWidth - margin * 2f) / sourceWidth, (targetHeight - margin * 2f) / sourceHeight);
        float scaledWidth = sourceWidth * scale;
        float scaledHeight = sourceHeight * scale;
        float offsetX = (targetWidth - scaledWidth) / 2f;
        float offsetY = (targetHeight - scaledHeight) / 2f;

        List<CapturedStroke> normalized = new ArrayList<>();
        for (CapturedStroke stroke : simplified) {
            List<CapturedStroke.Point> points = new ArrayList<>();
            for (CapturedStroke.Point point : stroke.points) {
                points.add(new CapturedStroke.Point(
                        ((point.x - minX) * scale) + offsetX,
                        ((point.y - minY) * scale) + offsetY,
                        point.timestampMillis
                ));
            }
            normalized.add(new CapturedStroke(points));
        }
        return new CapturedWriting(normalized, targetWidth, targetHeight, "");
    }

    public boolean hasWritingArea() {
        return writingAreaWidth != null;
    }

    public boolean hasRecognitionContext() {
        return hasWritingArea() || !preContext.isEmpty();
    }

    private static void requirePositiveFinite(float value, String name) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value <= 0.0f) {
            throw new IllegalArgumentException(name + " must be positive and finite.");
        }
    }

    private static CapturedStroke simplify(CapturedStroke stroke) {
        if (stroke.points.size() <= 2) {
            return stroke;
        }
        List<CapturedStroke.Point> simplified = new ArrayList<>();
        simplified.add(stroke.points.get(0));
        for (int i = 1; i < stroke.points.size() - 1; i++) {
            CapturedStroke.Point point = stroke.points.get(i);
            CapturedStroke.Point last = simplified.get(simplified.size() - 1);
            if (distance(last, point) >= 2.5f) {
                simplified.add(point);
            }
        }
        CapturedStroke.Point lastPoint = stroke.points.get(stroke.points.size() - 1);
        CapturedStroke.Point currentLast = simplified.get(simplified.size() - 1);
        if (currentLast.x != lastPoint.x || currentLast.y != lastPoint.y) {
            simplified.add(lastPoint);
        }
        return new CapturedStroke(simplified);
    }

    private static float distance(CapturedStroke.Point a, CapturedStroke.Point b) {
        float dx = a.x - b.x;
        float dy = a.y - b.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
