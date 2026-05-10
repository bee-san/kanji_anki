package dev.bee.kanjianki.core.study;

import org.junit.Test;

import java.io.StringReader;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class StrokeGuideTest {
    @Test
    public void parsesCompactNormalizedStrokeData() throws Exception {
        Map<String, StrokeGuide> guides = StrokeGuideParser.parse(new StringReader(
                """
                # generated from KanjiVG
                拉\t0.1,0.2;0.3,0.4|0.5,0.6;0.7,0.8
                """
        ));

        StrokeGuide guide = guides.get("拉");

        assertEquals("拉", guide.kanji);
        assertEquals(2, guide.strokeCount());
        assertEquals(0.1f, guide.strokes.get(0).points.get(0).x, 0.001f);
        InkStroke emptyStroke = new InkStroke(Collections.emptyList());
        assertThrows(UnsupportedOperationException.class, () -> guide.strokes.add(emptyStroke));
    }

    @Test
    public void rejectsMalformedStrokeData() {
        Exception error = assertThrows(Exception.class, () -> StrokeGuideParser.parse(new StringReader("拉\t0.1,0.2;bad\n")));
        assertTrue(error.getMessage().contains("Invalid point") || error.getMessage().contains("Invalid coordinate"));
    }

    @Test
    public void skipsBlankAndCommentLines() throws Exception {
        Map<String, StrokeGuide> guides = StrokeGuideParser.parse(new StringReader(
                """

                   # generated data
                \t
                """
        ));

        assertTrue(guides.isEmpty());
    }

    @Test
    public void rejectsMissingColumnsInvalidCoordinatesAndEmptyUsableStrokes() {
        Exception missingColumn = assertThrows(Exception.class, () -> StrokeGuideParser.parse(new StringReader("拉\n")));
        Exception invalidCoordinate = assertThrows(Exception.class, () -> StrokeGuideParser.parse(new StringReader("拉\t0.1,nope;0.2,0.3\n")));
        Exception noUsableStroke = assertThrows(Exception.class, () -> StrokeGuideParser.parse(new StringReader("拉\t0.1,0.2\n")));

        assertTrue(missingColumn.getMessage().contains("expected kanji<TAB>stroke data"));
        assertTrue(invalidCoordinate.getMessage().contains("Invalid coordinate"));
        assertTrue(noUsableStroke.getMessage().contains("no usable strokes"));
    }
}
