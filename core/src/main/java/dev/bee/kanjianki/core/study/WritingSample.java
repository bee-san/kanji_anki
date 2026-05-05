package dev.bee.kanjianki.core.study;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WritingSample {
    public final List<InkStroke> strokes;
    public final float width;
    public final float height;

    public WritingSample(List<InkStroke> strokes, float width, float height) {
        this.strokes = Collections.unmodifiableList(new ArrayList<>(strokes));
        this.width = width;
        this.height = height;
    }

    public static WritingSample empty() {
        return new WritingSample(Collections.emptyList(), 0f, 0f);
    }

    public boolean hasInk() {
        for (InkStroke stroke : strokes) {
            if (!stroke.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public int strokeCount() {
        int count = 0;
        for (InkStroke stroke : strokes) {
            if (!stroke.isEmpty()) {
                count++;
            }
        }
        return count;
    }
}
