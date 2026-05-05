package dev.bee.kanjianki.core.study;

import java.util.Objects;

public final class InkPoint {
    public final float x;
    public final float y;
    public final long timestampMillis;

    public InkPoint(float x, float y, long timestampMillis) {
        this.x = x;
        this.y = y;
        this.timestampMillis = timestampMillis;
    }

    public InkPoint scaled(float width, float height) {
        return new InkPoint(x * width, y * height, timestampMillis);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof InkPoint)) {
            return false;
        }
        InkPoint that = (InkPoint) other;
        return Float.compare(x, that.x) == 0
                && Float.compare(y, that.y) == 0
                && timestampMillis == that.timestampMillis;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, timestampMillis);
    }
}
