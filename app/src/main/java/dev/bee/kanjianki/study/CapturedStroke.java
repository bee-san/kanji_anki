package dev.bee.kanjianki.study;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class CapturedStroke {
    public final List<Point> points;

    public CapturedStroke(List<Point> points) {
        Objects.requireNonNull(points, "points");
        if (points.isEmpty()) {
            throw new IllegalArgumentException("A captured stroke must contain at least one point.");
        }
        List<Point> copy = new ArrayList<>();
        for (Point point : points) {
            copy.add(Objects.requireNonNull(point, "point"));
        }
        this.points = Collections.unmodifiableList(copy);
    }

    public static CapturedStroke of(List<Point> points) {
        return new CapturedStroke(points);
    }

    public static final class Point {
        public final float x;
        public final float y;
        public final Long timestampMillis;

        public Point(float x, float y) {
            this(x, y, null);
        }

        public Point(float x, float y, Long timestampMillis) {
            requireFinite(x, "x");
            requireFinite(y, "y");
            if (timestampMillis != null && timestampMillis < 0) {
                throw new IllegalArgumentException("timestampMillis must be non-negative.");
            }
            this.x = x;
            this.y = y;
            this.timestampMillis = timestampMillis;
        }

        private static void requireFinite(float value, String name) {
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                throw new IllegalArgumentException(name + " must be finite.");
            }
        }
    }
}
