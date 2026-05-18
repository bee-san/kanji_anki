package dev.bee.kanjianki.core.study;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StrokeGuide {
    public final String kanji;
    public final List<InkStroke> strokes;

    public StrokeGuide(String kanji, List<InkStroke> strokes) {
        this.kanji = kanji;
        this.strokes = Collections.unmodifiableList(new ArrayList<>(strokes));
    }

    public boolean isEmpty() {
        return strokes.isEmpty();
    }

    public int strokeCount() {
        return strokes.size();
    }
}
