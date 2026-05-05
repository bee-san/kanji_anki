package dev.bee.kanjianki.core.study;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StrokeGuideParser {
    private StrokeGuideParser() {
    }

    public static Map<String, StrokeGuide> parse(Reader reader) throws IOException {
        Map<String, StrokeGuide> guides = new LinkedHashMap<>();
        BufferedReader buffered = new BufferedReader(reader);
        String line;
        int lineNumber = 0;
        while ((line = buffered.readLine()) != null) {
            lineNumber++;
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] columns = trimmed.split("\\t", 2);
            if (columns.length != 2 || columns[0].isEmpty()) {
                throw new IOException("Invalid stroke guide line " + lineNumber + ": expected kanji<TAB>stroke data.");
            }
            guides.put(columns[0], new StrokeGuide(columns[0], parseStrokes(columns[1], lineNumber)));
        }
        return guides;
    }

    private static List<InkStroke> parseStrokes(String value, int lineNumber) throws IOException {
        List<InkStroke> strokes = new ArrayList<>();
        for (String strokeValue : value.split("\\|")) {
            String trimmed = strokeValue.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            List<InkPoint> points = new ArrayList<>();
            for (String pointValue : trimmed.split(";")) {
                String[] xy = pointValue.trim().split(",");
                if (xy.length != 2) {
                    throw new IOException("Invalid point on stroke guide line " + lineNumber + ": " + pointValue);
                }
                try {
                    points.add(new InkPoint(Float.parseFloat(xy[0]), Float.parseFloat(xy[1]), points.size()));
                } catch (NumberFormatException error) {
                    throw new IOException("Invalid coordinate on stroke guide line " + lineNumber + ": " + pointValue, error);
                }
            }
            if (points.size() >= 2) {
                strokes.add(new InkStroke(points));
            }
        }
        if (strokes.isEmpty()) {
            throw new IOException("Stroke guide line " + lineNumber + " has no usable strokes.");
        }
        return strokes;
    }
}
