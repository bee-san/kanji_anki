package dev.bee.kanjianki.study;

import dev.bee.kanjianki.core.study.WritingSample;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public final class CapturedWritingTest {
    @Test
    public void capturedStrokeRejectsInvalidInputs() {
        List<CapturedStroke.Point> emptyPoints = Collections.emptyList();
        List<CapturedStroke.Point> pointsWithNull = Arrays.asList(point(1f, 2f), null);

        assertThrows(NullPointerException.class, () -> new CapturedStroke(null));
        assertThrows(IllegalArgumentException.class, () -> new CapturedStroke(emptyPoints));
        assertThrows(NullPointerException.class, () -> new CapturedStroke(pointsWithNull));
        assertThrows(IllegalArgumentException.class, () -> new CapturedStroke.Point(Float.NaN, 0f));
        assertThrows(IllegalArgumentException.class, () -> new CapturedStroke.Point(Float.POSITIVE_INFINITY, 0f));
        assertThrows(IllegalArgumentException.class, () -> new CapturedStroke.Point(0f, Float.NEGATIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new CapturedStroke.Point(0f, 0f, -1L));
    }

    @Test
    public void capturedStrokeCopiesAndFreezesPoints() {
        List<CapturedStroke.Point> source = new ArrayList<>();
        source.add(new CapturedStroke.Point(1f, 2f, 30L));
        source.add(point(3f, 4f));

        CapturedStroke stroke = CapturedStroke.of(source);
        source.add(point(9f, 9f));
        CapturedStroke.Point appendedPoint = point(5f, 6f);

        assertEquals(2, stroke.points.size());
        assertEquals(1f, stroke.points.get(0).x, 0f);
        assertEquals(2f, stroke.points.get(0).y, 0f);
        assertEquals(Long.valueOf(30L), stroke.points.get(0).timestampMillis);
        assertEquals(3f, stroke.points.get(1).x, 0f);
        assertEquals(4f, stroke.points.get(1).y, 0f);
        assertNull(stroke.points.get(1).timestampMillis);
        assertThrows(UnsupportedOperationException.class, () -> stroke.points.add(appendedPoint));
    }

    @Test
    public void capturedWritingRejectsInvalidInputs() {
        CapturedStroke stroke = stroke(point(1f, 1f));
        List<CapturedStroke> emptyStrokes = Collections.emptyList();
        List<CapturedStroke> nullStroke = Collections.singletonList(null);
        List<CapturedStroke> oneStroke = Collections.singletonList(stroke);

        assertThrows(NullPointerException.class, () -> new CapturedWriting(null));
        assertThrows(IllegalArgumentException.class, () -> new CapturedWriting(emptyStrokes));
        assertThrows(NullPointerException.class, () -> new CapturedWriting(nullStroke));
        assertThrows(IllegalArgumentException.class, () -> new CapturedWriting(oneStroke, 100f, null, ""));
        assertThrows(IllegalArgumentException.class, () -> new CapturedWriting(oneStroke, null, 100f, ""));
        assertThrows(IllegalArgumentException.class, () -> new CapturedWriting(oneStroke, 0f, 100f, ""));
        assertThrows(IllegalArgumentException.class, () -> new CapturedWriting(oneStroke, 100f, Float.NaN, ""));
        assertThrows(IllegalArgumentException.class, () -> new CapturedWriting(oneStroke, Float.POSITIVE_INFINITY, 100f, ""));
    }

    @Test
    public void capturedWritingCopiesStrokesAndReportsContextFlags() {
        CapturedStroke stroke = stroke(point(1f, 1f));
        List<CapturedStroke> strokes = new ArrayList<>();
        strokes.add(stroke);

        CapturedWriting withAreaAndNullContext = new CapturedWriting(strokes, 200f, 300f, null);
        strokes.add(stroke(point(2f, 2f)));
        CapturedWriting withTextContext = new CapturedWriting(Collections.singletonList(stroke), null, null, "previous");
        CapturedWriting basic = CapturedWriting.of(Collections.singletonList(stroke));

        assertEquals(1, withAreaAndNullContext.strokes.size());
        assertEquals(200f, withAreaAndNullContext.writingAreaWidth, 0f);
        assertEquals(300f, withAreaAndNullContext.writingAreaHeight, 0f);
        assertEquals("", withAreaAndNullContext.preContext);
        assertTrue(withAreaAndNullContext.hasWritingArea());
        assertTrue(withAreaAndNullContext.hasRecognitionContext());
        assertFalse(withTextContext.hasWritingArea());
        assertTrue(withTextContext.hasRecognitionContext());
        assertFalse(basic.hasWritingArea());
        assertFalse(basic.hasRecognitionContext());
        assertThrows(UnsupportedOperationException.class, () -> withAreaAndNullContext.strokes.add(stroke));
    }

    @Test
    public void capturedWritingConvertsToWritingSample() {
        CapturedWriting writing = new CapturedWriting(
                Arrays.asList(
                        stroke(new CapturedStroke.Point(1f, 2f, 30L)),
                        stroke(new CapturedStroke.Point(3f, 4f))
                ),
                200f,
                300f,
                ""
        );

        WritingSample sample = writing.toWritingSample();

        assertEquals(2, sample.strokes.size());
        assertEquals(200f, sample.width, 0f);
        assertEquals(300f, sample.height, 0f);
        assertEquals(1f, sample.strokes.get(0).points.get(0).x, 0f);
        assertEquals(2f, sample.strokes.get(0).points.get(0).y, 0f);
        assertEquals(30L, sample.strokes.get(0).points.get(0).timestampMillis);
        assertEquals(3f, sample.strokes.get(1).points.get(0).x, 0f);
        assertEquals(4f, sample.strokes.get(1).points.get(0).y, 0f);
        assertEquals(0L, sample.strokes.get(1).points.get(0).timestampMillis);
    }

    @Test
    public void capturedWritingWithoutAreaConvertsToZeroSizedSample() {
        CapturedWriting writing = CapturedWriting.of(Collections.singletonList(stroke(point(1f, 2f))));

        WritingSample sample = writing.toWritingSample();

        assertEquals(1, sample.strokes.size());
        assertEquals(0f, sample.width, 0f);
        assertEquals(0f, sample.height, 0f);
    }

    @Test
    public void prepareForRecognitionRejectsInvalidInputs() {
        List<CapturedStroke> strokes = Collections.singletonList(stroke(point(1f, 1f), point(2f, 2f)));
        List<CapturedStroke> emptyStrokes = Collections.emptyList();

        assertThrows(NullPointerException.class, () -> CapturedWriting.prepareForRecognition(null, 100f, 100f));
        assertThrows(IllegalArgumentException.class, () -> CapturedWriting.prepareForRecognition(emptyStrokes, 100f, 100f));
        assertThrows(IllegalArgumentException.class, () -> CapturedWriting.prepareForRecognition(strokes, 0f, 100f));
        assertThrows(IllegalArgumentException.class, () -> CapturedWriting.prepareForRecognition(strokes, 100f, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> CapturedWriting.prepareForRecognition(strokes, Float.NEGATIVE_INFINITY, 100f));
    }

    @Test
    public void prepareForRecognitionSimplifiesDenseStrokesAndNormalizesIntoSquare() {
        CapturedStroke duplicateFinal = stroke(
                new CapturedStroke.Point(10f, 10f, 0L),
                new CapturedStroke.Point(11f, 11f, 1L),
                new CapturedStroke.Point(13f, 10f, 2L),
                new CapturedStroke.Point(13f, 10f, 3L)
        );
        CapturedStroke addedFinal = stroke(
                new CapturedStroke.Point(20f, 20f, 4L),
                new CapturedStroke.Point(21f, 20f, 5L),
                new CapturedStroke.Point(24f, 20f, 6L)
        );

        CapturedWriting prepared = CapturedWriting.prepareForRecognition(
                Arrays.asList(duplicateFinal, addedFinal),
                320f,
                240f
        );

        assertEquals(2, prepared.strokes.size());
        assertEquals(2, prepared.strokes.get(0).points.size());
        assertEquals(2, prepared.strokes.get(1).points.size());
        assertEquals(1000f, prepared.writingAreaWidth, 0f);
        assertEquals(1000f, prepared.writingAreaHeight, 0f);
        assertEquals("", prepared.preContext);
        assertTrue(prepared.hasWritingArea());
        assertTrue(prepared.hasRecognitionContext());
        assertPoint(prepared.strokes.get(0).points.get(0), 140f, 242.85715f, 0L);
        assertPoint(prepared.strokes.get(0).points.get(1), 294.2857f, 242.85715f, 2L);
        assertPoint(prepared.strokes.get(1).points.get(0), 654.2857f, 757.1429f, 4L);
        assertPoint(prepared.strokes.get(1).points.get(1), 860f, 757.1429f, 6L);
    }

    @Test
    public void prepareForRecognitionKeepsShortStrokesAndCentersMinimumSizeBounds() {
        CapturedStroke shortStroke = stroke(
                new CapturedStroke.Point(5f, 7f, 10L),
                new CapturedStroke.Point(5f, 7f, 11L)
        );

        CapturedWriting prepared = CapturedWriting.prepareForRecognition(
                Collections.singletonList(shortStroke),
                80f,
                60f
        );

        assertEquals(1, prepared.strokes.size());
        assertEquals(2, prepared.strokes.get(0).points.size());
        assertPoint(prepared.strokes.get(0).points.get(0), 140f, 140f, 10L);
        assertPoint(prepared.strokes.get(0).points.get(1), 140f, 140f, 11L);
    }

    @Test
    public void prepareForRecognitionKeepsFinalPointWhenOnlyYChanges() {
        CapturedStroke verticalFinal = stroke(
                new CapturedStroke.Point(10f, 10f, 0L),
                new CapturedStroke.Point(10f, 11f, 1L),
                new CapturedStroke.Point(10f, 12f, 2L)
        );

        CapturedWriting prepared = CapturedWriting.prepareForRecognition(
                Collections.singletonList(verticalFinal),
                100f,
                100f
        );

        assertEquals(1, prepared.strokes.size());
        assertEquals(2, prepared.strokes.get(0).points.size());
        assertEquals(Long.valueOf(0L), prepared.strokes.get(0).points.get(0).timestampMillis);
        assertEquals(Long.valueOf(2L), prepared.strokes.get(0).points.get(1).timestampMillis);
    }

    private static CapturedStroke.Point point(float x, float y) {
        return new CapturedStroke.Point(x, y);
    }

    private static CapturedStroke stroke(CapturedStroke.Point... points) {
        return new CapturedStroke(Arrays.asList(points));
    }

    private static void assertPoint(CapturedStroke.Point point, float x, float y, long timestamp) {
        assertNotNull(point);
        assertEquals(x, point.x, 0.0001f);
        assertEquals(y, point.y, 0.0001f);
        assertEquals(Long.valueOf(timestamp), point.timestampMillis);
    }
}
