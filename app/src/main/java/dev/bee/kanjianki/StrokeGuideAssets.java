package dev.bee.kanjianki;

import android.content.Context;

import dev.bee.kanjianki.core.study.StrokeGuide;
import dev.bee.kanjianki.core.study.StrokeGuideParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

final class StrokeGuideAssets {
    private StrokeGuideAssets() {
    }

    static Map<String, StrokeGuide> load(Context context) {
        try (InputStream in = context.getResources().openRawResource(R.raw.kanji_strokes);
             InputStreamReader reader = new InputStreamReader(in)) {
            return StrokeGuideParser.parse(reader);
        } catch (Exception error) {
            return new HashMap<>();
        }
    }
}
