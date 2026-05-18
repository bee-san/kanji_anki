package dev.bee.kanjianki.core.study;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class StrokeGuideParser {
    private static final Pattern TAB_SEPARATOR = Pattern.compile("\\t");
    private static final Pattern PIPE_SEPARATOR = Pattern.compile("\\|");
    private static final Pattern SEMICOLON_SEPARATOR = Pattern.compile(";");
    private static final Pattern COMMA_SEPARATOR = Pattern.compile(",");

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
            String[] columns = TAB_SEPARATOR.split(trimmed, 2);
            if (columns.length != 2) {
                throw new IOException("Invalid stroke guide line " + lineNumber + ": expected kanji<TAB>stroke data.");
            }
            guides.put(columns[0], new StrokeGuide(columns[0], parseStrokes(columns[1], lineNumber)));
        }
        return guides;
    }

    private static List<InkStroke> parseStrokes(String value, int lineNumber) throws IOException {
        List<InkStroke> strokes = new ArrayList<>();
        for (String strokeValue : PIPE_SEPARATOR.split(value)) {
            String trimmed = strokeValue.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            List<InkPoint> points = new ArrayList<>();
            for (String pointValue : SEMICOLON_SEPARATOR.split(trimmed)) {
                String[] xy = COMMA_SEPARATOR.split(pointValue.trim());
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
