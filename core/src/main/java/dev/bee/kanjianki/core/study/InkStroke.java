package dev.bee.kanjianki.core.study;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class InkStroke {
    public final List<InkPoint> points;

    public InkStroke(List<InkPoint> points) {
        this.points = Collections.unmodifiableList(new ArrayList<>(points));
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }

    public InkPoint start() {
        return points.isEmpty() ? null : points.get(0);
    }

    public InkPoint end() {
        return points.isEmpty() ? null : points.get(points.size() - 1);
    }
}
